/*
 * Copyright (c) 2019-2020, guanquan.wang@yandex.com All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hankcs.hanlp.model.perceptron;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.collection.trie.DoubleArrayTrie;
import com.hankcs.hanlp.collection.trie.datrie.MutableDoubleArrayTrieInteger;
import com.hankcs.hanlp.collection.trie.datrie.Utf8CharacterMapping;
import com.hankcs.hanlp.corpus.document.sentence.Sentence;
import com.hankcs.hanlp.corpus.document.sentence.word.IWord;
import com.hankcs.hanlp.dictionary.other.CharTable;
import com.hankcs.hanlp.seg.Remote;
import com.hankcs.hanlp.seg.Viterbi.RemoteViterbiSegment;
import com.hankcs.hanlp.seg.common.Term;
import com.hankcs.hanlp.tokenizer.StandardTokenizer;
import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Create by guanquan.wang at 2019-12-05 09:35
 */
public class RemotePerceptronLexicalAnalyzerTest extends TestCase {

    public void testConstructor() throws IOException
    {
        HanLP.Config.enableDebug();
        RemotePerceptronLexicalAnalyzer analyzer = new RemotePerceptronLexicalAnalyzer(
            "http://localhost/hanlp/cws.bin.5", "http://localhost/hanlp/pos.bin.5", "http://localhost/hanlp/ner.bin.5");
        CharTable.CONVERT[' '] = '!';
        CharTable.CONVERT['\t'] = '!';
        CharTable.CONVERT['/'] = '!';
        System.out.println(analyzer.analyze("德力西电气 得力 一字铬钒钢旋具 6寸6*150；DHCDL6161501"));
        System.out.println(analyzer.analyze("可穿戴的VR头盔已经正在影响我们的生活"));
    }

    public synchronized void testLazyLoad() throws IOException, InterruptedException {
        HanLP.Config.enableDebug();
        RemotePerceptronLexicalAnalyzer analyzer = new RemotePerceptronLexicalAnalyzer();
        analyzer.lazy("http://localhost/hanlp/cws.bin.3"
            , "http://localhost/hanlp/pos.bin.3"
            , "http://localhost/hanlp/ner.bin.3");
//        analyzer.refresh();
//        analyzer.refresh();

        if (analyzer.isReady()) {
            System.out.println(analyzer.analyze("无线对讲机附件"));
        }

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println(analyzer.getStatus());
                System.out.println(analyzer.analyze("无线对讲机附件"));
            }
        }, 5000L);

        wait(20_000);
    }

    public void testLearn() throws IOException {

        RemotePerceptronLexicalAnalyzer analyzer = new RemotePerceptronLexicalAnalyzer(
            "/Users/guanquan.wang/hanlp/cws.bin.6"
            , "/Users/guanquan.wang/hanlp/pos.bin.6"
            , "/Users/guanquan.wang/hanlp/ner.bin.6");

        HanLP.Config.enableDebug();
        CharTable.CONVERT[' '] = '!';
        CharTable.CONVERT['\t'] = '!';
        CharTable.CONVERT['/'] = '!';


        System.out.println(analyzer.analyze("光电传感器O300.GR-11135876(Baumer)"));
        System.out.println(analyzer.analyze("电磁阀4KA210-06-B/220V(CKD)可推荐"));
        System.out.println(analyzer.analyze("辅助触头ABB"));


//        analyzer.getPerceptronPOSTagger().getModel().tagSet().add("g");
//        analyzer.getPerceptronPOSTagger().getModel().tagSet().add("gc");
//        analyzer.getPerceptronPOSTagger().getModel().tagSet().add("gp");

        for (int i = 0; i < 1000; i++) {
            analyzer.learn("光电/b 传感器/n O300.GR-11135876/nx (/w Baumer/nr )/w 可/c 推荐/v");
            analyzer.learn("Baumer/nr 光电/b 传感器/n");
            analyzer.learn("辅助触头/n ABB/nr");
            analyzer.learn("AB光幕/n 连接/b 电缆/n ABB/nr");
            analyzer.learn("漏电/v 保护/v 空气/a 开关/n ABB/nr");
            analyzer.learn("电磁阀/n 4KA210-06-B/nx //w 220V/g (/w CKD/nr )/w 可/c 推荐/v");
        }

        System.out.println(analyzer.analyze("电磁阀4KA210-06-B/220V(CKD)可推荐"));
        System.out.println(analyzer.analyze("光电传感器O300.GR-11135876(Baumer)"));
        System.out.println(analyzer.analyze("辅助触头ABB"));



//        System.out.println(analyzer.analyze("明装式控制箱正泰"));
//        System.out.println(analyzer.analyze("断路器(两P)正泰"));
//        System.out.println(analyzer.analyze("断路器正泰"));
//        analyzer.getPerceptronPOSTagger().getModel().save("/Users/guanquan.wang/hanlp/pos.bin.6");
//        analyzer.getPerceptionNERecognizer().getModel().save("/Users/guanquan.wang/hanlp/ner.bin.6");
//        analyzer.getPerceptronSegmenter().getModel().save("/Users/guanquan.wang/hanlp/cws.bin.6");
    }

    public void testSupport() {
        String s = Remote.getSupportScheme();
        System.out.println(s);

        MutableDoubleArrayTrieInteger trie = new MutableDoubleArrayTrieInteger();
        trie.set("a", 1);
        trie.set("b", 1);
        trie.set("ab", 1);

        System.out.println(trie.get("a"));
    }

    public void testStandard() throws IOException {
        HanLP.Config.IOAdapter = null;
        RemotePerceptronLexicalAnalyzer analyzer = new RemotePerceptronLexicalAnalyzer(
            "/Users/guanquan.wang/hanlp/cws.bin.6"
            , "/Users/guanquan.wang/hanlp/pos.bin.6"
            , "/Users/guanquan.wang/hanlp/ner.bin.6");
        CharTable.CONVERT[' '] = '!';
        CharTable.CONVERT['\t'] = '!';
        CharTable.CONVERT['/'] = '!';

//        MutableDoubleArrayTrieInteger trie = new MutableDoubleArrayTrieInteger();
        Map<String, Integer> map = new HashMap<>(4096);
        try (Stream<String> stream = Files.lines(Paths.get("data/dictionary/mro/无.txt"));
             BufferedWriter writer = Files.newBufferedWriter(Paths.get("data/dictionary/mro/goods_name.txt"))) {
            char[] cache = new char[512];
            stream.map(RemotePerceptronLexicalAnalyzerTest::trimAll)
                .map(analyzer::analyze).forEach(s -> {
                for (IWord word : s.wordList) {
                    String v = word.getValue(), l = word.getLabel();
                    if (l.length() > 0 && (l.charAt(0) == 'n' || l.charAt(0) == 'j' || l.charAt(0) == 'g')) {
                        int nv = v.length(), nl = l.length();
                        v.getChars(0, nv, cache, 0);
                        cache[nv] = '_';
                        l.getChars(0, nl, cache, nv + 1);
                        int len = nv + nl + 1;
                        String key = new String(cache, 0, len);
                        Integer value = map.get(key);
                        map.put(key, value != null ? value + 1 : 1);

                        System.out.println(key);

//                        if ((n = trie.get(cache, 0, len)) != -1) {
//                            trie.set(cache, 0, len, n + 1);
//                        } else {
//                            trie.set(cache, 0, len, 1);
//                        }
                    }
                }
            });

//            MutableDoubleArrayTrieInteger.KeyValuePair pair = trie.iterator();
//            for (; pair.hasNext(); ) {
//                MutableDoubleArrayTrieInteger.KeyValuePair p = pair.next();
//                writer.write(p.key());
//                writer.write(' ');
//                writer.write(p.value());
//                writer.newLine();
//            }
            System.out.println(map.size());
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                int n = entry.getKey().lastIndexOf(('_'));
                writer.write(entry.getKey().substring(0, n));
                writer.write(' ');
                writer.write(entry.getKey().substring(n + 1));
                writer.write(' ');
                writer.write(entry.getValue().toString());
                writer.newLine();
            }
        }
    }

    public void testStandard1() throws IOException {
        HanLP.Config.IOAdapter = null;
        RemotePerceptronLexicalAnalyzer analyzer = new RemotePerceptronLexicalAnalyzer(
            "/Users/guanquan.wang/hanlp/cws.bin.6"
            , "/Users/guanquan.wang/hanlp/pos.bin.6"
            , "/Users/guanquan.wang/hanlp/ner.bin.6");
        CharTable.CONVERT[' '] = '!';
        CharTable.CONVERT['\t'] = '!';
        CharTable.CONVERT['/'] = '!';

        MutableDoubleArrayTrieInteger trie = new MutableDoubleArrayTrieInteger();
        try (Stream<String> stream = Files.lines(Paths.get("data/dictionary/mro/无.txt"));
             BufferedWriter writer = Files.newBufferedWriter(Paths.get("data/dictionary/mro/goods_name_3.txt"))) {
            char[] cache = new char[512];
            stream
//                .map(RemotePerceptronLexicalAnalyzerTest::trimAll)
                .map(analyzer::analyze).forEach(s -> {
                for (IWord word : s.wordList) {
                    String v = word.getValue(), l = word.getLabel();
                    if (l.length() == 1 && 'n' == l.charAt(0)) {
                        int nv = v.length();
                        v.getChars(0, nv, cache, 0);

                        int nature = isNoun(cache, 0, nv);
                        if (nature > 0) {
                            if (trie.get(cache, 0, nv) == -1) {
                                trie.set(cache, 0, nv, nature);
                            }
                        }
                    }
                }
            });

            MutableDoubleArrayTrieInteger.KeyValuePair pair = trie.iterator();
            for (; pair.hasNext(); ) {
                MutableDoubleArrayTrieInteger.KeyValuePair p = pair.next();
                String nn = natureName(p.value());
                if (nn == null) continue;
                writer.write(p.key());
                writer.write(' ');
                writer.write(nn);
                writer.write(" 1");
                writer.newLine();
            }
        }
    }

    public static int isNoun(char[] chars) {
        return isNoun(chars, 0, chars.length);
    }

    public static int isNoun(char[] chars, int from, int length) {
        if (length == 1) return 0x0;
        boolean c = isChinese(chars, from, length);
        if (!c) return 0x0;
        char nc = chars[length - 1];
        boolean b = nc == '型' || nc == '式' || nc == '形' || nc == '款'
            || nc == '件' || nc == '色';
        if (b) return 'b';
        boolean o = nc == '列' || nc == '长' || nc == '组' || nc == '方';
        if (o) return 0x0;
        String key = new String(chars, from, length);
        if (isBrand(key)) return 0x01;
        if (isCatgs(key)) return 0x02;
        return 'n';
    }

    public static boolean isChinese(char[] chars, int from, int length) {
        for (int i = 0; i < length; i++) {
            if (chars[from + i] < 0x4E00 || chars[from + i] > 0x9FA5)
                return false;
        }
        return true;
    }

    private static final Set<String> brands;
    private static final Set<String> catgs;
    private static String[] natureNames;
    static {
        brands = new HashSet<>(1 << 12);
        catgs = new HashSet<>(1 << 12);
        natureNames = new String[111];

        natureNames[0x1] = "br";
        natureNames[0x2] = "cg";
        natureNames['b'] = "b";
        natureNames['n'] = "n";

        try (Stream<String> b = Files.lines(Paths.get("data/dictionary/mro/brand.txt"))) {
            b.map(s -> s.substring(0, s.indexOf(' '))).forEach(brands::add);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (Stream<String> b = Files.lines(Paths.get("data/dictionary/mro/b2r_seller_goods-categoryNames.keyword.txt"))) {
            b.map(s -> s.substring(0, s.indexOf(' '))).forEach(catgs::add);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean isBrand(String key) {
        return brands.contains(key);
    }

    public static boolean isCatgs(String key) {
        return catgs.contains(key);
    }

    public static boolean isBrand(char[] chars, int from, int length) {
        return isBrand(new String(chars, from, length));
    }

    public static boolean isCatgs(char[] chars, int from, int length) {
        return isCatgs(new String(chars, from, length));
    }

    public static String trimAll(String s) {
        int len = Math.min(s.length(), 128);
        char[] chars = new char[len];
        s.getChars(0, len, chars, 0);
        int j = 0;
        for (int i = 0; i < chars.length; ) {
            if (i != j) {
                chars[j] = chars[i];
            }
            if (chars[i] > 32 && chars[i] != '　')
                j++;
            i++;
        }
        return j > 0 ? new String(chars, 0, j) : "";
    }

    public static String natureName(int nature) {
        return nature > 0 && nature < natureNames.length ? natureNames[nature] : null;
    }

    public void testS() throws IOException {
        RemotePerceptronLexicalAnalyzer analyzer = new RemotePerceptronLexicalAnalyzer(
            "/Users/guanquan.wang/hanlp/cws.bin.6"
            , "/Users/guanquan.wang/hanlp/pos.bin.6"
            , "/Users/guanquan.wang/hanlp/ner.bin.6");
        CharTable.CONVERT[' '] = '!';
        CharTable.CONVERT['\t'] = '!';
        CharTable.CONVERT['/'] = '!';
        System.out.println(analyzer.analyze("上海人民开关厂 塑壳漏电断路器；RKM1LE-100HZ/43002 II 10A 500mA 延时型 1.15s"));
    }


    public void testCharset() {
        Utf8CharacterMapping mapping = new Utf8CharacterMapping();
        String key = "中文ab123243测试";
        int[] ids = mapping.toIdList(key);
        System.out.println(Arrays.toString(ids));

        ids = mapping.toIdList(key.toCharArray(), 0, key.length());
        System.out.println(Arrays.toString(ids));

//        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(key.toCharArray(), 0, key.length()));
////        byteBuffer.flip();
//        System.out.println(byteBuffer);
//        for (int i = 0, len = byteBuffer.limit(); i < len; i++) {
//            System.out.print(byteBuffer.get());
//            System.out.print(' ');
//        }
    }

    public void testRead() throws IOException {
        RemotePerceptronLexicalAnalyzer analyzer = new RemotePerceptronLexicalAnalyzer(
            "/Users/guanquan.wang/hanlp/cws.bin.6"
            , "/Users/guanquan.wang/hanlp/pos.bin.6"
            , "/Users/guanquan.wang/hanlp/ner.bin.6");
        CharTable.CONVERT[' '] = '!';
        CharTable.CONVERT['\t'] = '!';
        CharTable.CONVERT['/'] = '!';

        try (Stream<String> stream = Files.lines(Paths.get("data/dictionary/mro/无.txt"))) {
            stream.map(analyzer::analyze).forEach(System.out::println);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void testChinese() {
        try (Stream<String> stream = Files.lines(Paths.get("data/dictionary/mro/goods_name_2.txt"));
             Stream<String> stream2 = Files.lines(Paths.get("data/dictionary/mro/搜索服务-词典.txt"));
             BufferedWriter writer = Files.newBufferedWriter(Paths.get("data/dictionary/mro/goods_name_chinese_2.txt"))) {
            stream
                .map(s -> {
                    int n = s.indexOf(' ');
                    return n > 0 ? s.substring(0, n) : s;
                })
                .filter(s -> s.length() > 1)
                .filter(s -> isNoun(s.toCharArray()) > 0x0)
                .forEach(s -> {
                    try {
                        writer.write(s);
                        writer.write(' ');
                        writer.write('n');
                        writer.write(' ');
                        writer.write(49);
                        writer.newLine();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            stream2
                .map(s -> {
                    int n = s.indexOf(' ');
                    return n > 0 ? s.substring(0, n) : s;
                })
                .filter(s -> s.length() > 1)
                .filter(s -> isNoun(s.toCharArray()) > 0x0)
                .forEach(s -> {
                    try {
                        writer.write(s);
                        writer.write(' ');
                        writer.write('n');
                        writer.write(' ');
                        writer.write(49);
                        writer.newLine();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void testFilter() {
        try (Stream<String> lines = Files.lines(Paths.get("data/dictionary/mro/bdm_goods-brandName.keyword.txt"))) {
            lines
                .map(s -> {
                    int n = s.indexOf(' ');
                    return n > 0 ? s.substring(0, n) : s;
                })
                .filter(s -> !isBrand(s))
                .forEach(s -> System.out.println(s + " br 1"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void testViterbi() throws IOException {
        HanLP.Config.enableDebug(false);
        HanLP.Config.IOAdapter = null;
        RemoteViterbiSegment segment = new RemoteViterbiSegment("http://localhost/hanlp/mro.bin");
        CharTable.CONVERT[' '] = '!';
        CharTable.CONVERT['\t'] = '!';
        CharTable.CONVERT['/'] = '!';

        MutableDoubleArrayTrieInteger trie = new MutableDoubleArrayTrieInteger();
        try (Stream<String> stream = Files.lines(Paths.get("data/dictionary/mro/无.txt"));
             BufferedWriter writer = Files.newBufferedWriter(Paths.get("data/dictionary/mro/goods_name_viterbi.txt"))) {
            char[] cache = new char[512];
            stream
//                .map(RemotePerceptronLexicalAnalyzerTest::trimAll)
                .map(segment::seg).forEach(terms -> {
                for (Term term : terms) {
                    String v = term.word, l = term.nature.getName();
                    if (l.length() == 1 && 'n' == l.charAt(0)) {
                        int nv = v.length();
                        v.getChars(0, nv, cache, 0);

                        int nature = isNoun(cache, 0, nv);
                        if (nature > 0) {
                            if (trie.get(cache, 0, nv) == -1) {
                                trie.set(cache, 0, nv, nature);
                            }
                        }
                    }
                }
            });

            MutableDoubleArrayTrieInteger.KeyValuePair pair = trie.iterator();
            for (; pair.hasNext(); ) {
                MutableDoubleArrayTrieInteger.KeyValuePair p = pair.next();
                String nn = natureName(p.value());
                if (nn == null) continue;
                writer.write(p.key());
                writer.write(' ');
                writer.write(nn);
                writer.write(" 1");
                writer.newLine();
            }
        }
    }

    public void testFilterOne() {
        try (Stream<String> stream = Files.lines(Paths.get("data/dictionary/mro/goods_name_3.txt"));
             BufferedWriter writer = Files.newBufferedWriter(Paths.get("data/dictionary/mro/goods_name_chinese_3.txt"))) {
            stream
                .filter(s -> s.indexOf(' ') > 1)
                .forEach(s -> {
                    try {
                        writer.write(s);
                        writer.newLine();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
