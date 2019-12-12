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
import com.hankcs.hanlp.collection.trie.datrie.MutableDoubleArrayTrie;
import com.hankcs.hanlp.dictionary.other.CharTable;
import com.hankcs.hanlp.seg.common.Term;
import com.hankcs.hanlp.tokenizer.StandardTokenizer;
import junit.framework.TestCase;

import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Create by guanquan.wang at 2019-12-05 09:35
 */
public class RemotePerceptronLexicalAnalyzerTest extends TestCase {

    public void testConstructor() throws IOException
    {
        HanLP.Config.enableDebug();
        RemotePerceptronLexicalAnalyzer analyzer = new RemotePerceptronLexicalAnalyzer(
            "/hanlp/cws.bin.5", "/hanlp/pos.bin.5", "/hanlp/ner.bin.5");
        CharTable.CONVERT[' '] = '!';
        CharTable.CONVERT['\t'] = '!';
        CharTable.CONVERT['/'] = '!';
        System.out.println(analyzer.analyze("SAMSUNG 三星 RS542NCAEWW/SC 545L 风冷变频对开门冰箱，还行还行，可以看看;"));
    }

    public synchronized void testLazyLoad() throws IOException, InterruptedException {
        HanLP.Config.enableDebug();
        RemoteLexicalAnalyzer analyzer = new RemotePerceptronLexicalAnalyzer();
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
        String s = RemoteLexicalAnalyzer.getSupportScheme();
        System.out.println(s);
    }

    public void testStandard() {
        HanLP.Config.enableDebug();
//        List<Term> termList = StandardTokenizer.segment("交流漏电断路器 C65H-DC 2P C6A C65H-DC 2P C6A 施耐德");
        List<Term> termList = StandardTokenizer.segment("SAMSUNG 三星 RS542NCAEWW/SC 545L 风冷变频对开门冰箱，还行还行，可以看看;");
        System.out.println(termList);
    }

}
