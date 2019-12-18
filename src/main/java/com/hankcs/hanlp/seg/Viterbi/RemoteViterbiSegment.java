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

package com.hankcs.hanlp.seg.Viterbi;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.collection.trie.DoubleArrayTrie;
import com.hankcs.hanlp.corpus.io.ByteArray;
import com.hankcs.hanlp.corpus.io.IOUtil;
import com.hankcs.hanlp.corpus.tag.Nature;
import com.hankcs.hanlp.dictionary.CoreDictionary;
import com.hankcs.hanlp.seg.Remote;
import com.hankcs.hanlp.utility.TextUtility;
import sun.misc.Contended;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import static com.hankcs.hanlp.utility.Predefine.logger;

/**
 * Create by guanquan.wang at 2019-12-12 09:55
 */
public class RemoteViterbiSegment extends ViterbiSegment implements Remote {
    /*
    0: unload model files
    1: getting remote files
    2: refresh new model files
    3: analyzer is ready
    */
    @Contended
    private volatile int status = 0;
    @Contended
    private volatile int sync_status = 0;

    // Cache some model base info
    private ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();

    public RemoteViterbiSegment() { }

    /**
     * @param customPath local/remote model path
     *                   Use `;` to connect multiple paths
     */
    public RemoteViterbiSegment(String customPath) throws IOException {
        checkAndCacheParam(customPath);
        refresh(true);
    }

    /**
     * Returns this analyzer is ready
     *
     * @return true if it is ready
     */
    @Override
    public boolean isReady() {
        return status == 3;
    }

    /**
     * Returns this analyzer status
     *
     * @return the status int value
     *     0: unload model files
     *     1: getting remote files
     *     2: flushing new model files
     *     3: analyzer is ready
     */
    @Override
    public int getStatus() {
        return this.status;
    }

    /**
     * Lazy load remote/local model file
     *
     * @param customPath the customize dictionary file
     */
    public void lazy(String customPath) throws IOException {
        checkAndCacheParam(customPath);
        new Thread(() -> {
            try {
                refresh();
            } catch (IOException e) {
                logger.warning("加载失败");
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Load remote/local model file
     *
     * @param customPath the customize dictionary file
     * @return true if success
     * @throws IOException if I/O error occur
     */
    public boolean reload(String customPath) throws IOException {
        checkAndCacheParam(customPath);
        return refresh();
    }

    /**
     * Refresh all model, Whether to force a refresh regardless of whether
     * the file has changed if `mandatory` is true
     *
     * @param mandatory a force refresh flag
     * @return true if success
     * @throws IOException if I/O error occur
     */
    @Override
    public synchronized boolean refresh(boolean mandatory) throws IOException {
        if (sync_status == 1 || sync_status == 2) {
            logger.warning("已有相同任务正在执行。");
            return false;
        }
        boolean hasErr = false;
        // Modify status
        sync_status = 1;
        try {
            boolean r = loadCustomDic(mandatory);
            if (r) {
                Object f = cache.get("F");
                if (f instanceof Exception) {
                    hasErr = true;
                    ((Exception) f).printStackTrace();
                }

                if (hasErr) {
                    rollback();
                } else {
                    commit();
                }
            }
        } finally {
            // Reset status when error occur
            if (sync_status == 2 && (status == 0 || status == 3)) {
                status = 3;
            }
            // Reset sync status
            sync_status = 0;
        }
        return !hasErr;
    }

    /**
     * Returns the customer dictionary paths
     *
     * @return paths join to String
     */
    public String getCustomPath() {
        Object o = cache.get("CP");
        if (o instanceof String[]) {
            return Arrays.toString((String[]) o);
        }
        return "";
    }

    /**
     * Returns the customer dictionary short name
     *
     * @return paths join to String
     */
    public String getShortCustomPath() {
        Object o = cache.get("CP");
        if (o instanceof String[]) {
            String[] paths = (String[]) o;
            String[] nPaths = new String[paths.length];
            for (int i = 0; i < paths.length; i++) {
                nPaths[i] = paths[i].substring(paths[i].lastIndexOf('/') + 1);
            }
            return Arrays.toString(nPaths);
        }
        return "";
    }

    protected boolean loadCustomDic(boolean mandatory) throws IOException {
        String[] paths = (String[]) cache.get("CP");
        if (paths == null || paths.length == 0) {
            throw new IOException("未指定自定义词典文件路径.");
        }
        Scheme[] schemes = (Scheme[]) cache.get("SC");
        DoubleArrayTrie<CoreDictionary.Attribute> dat = new DoubleArrayTrie<>();

        for (int i = 0; i < paths.length; i++) {
            checkAndRequestOnModified(paths[i], schemes[i], mandatory, new Callback() {
                @Override
                public void onFailure(IOException e) {
                    cache.put("F", e);
                }

                @Override
                public void onResponse(Response response) throws IOException {
                    try {
                        if (response.code == 200) {

                            if (loadFromInputStream(response.stream, dat)) {
                                cache.put("S", dat);
                            } else {
                                cache.put("F", new IOException("创建trie树失败"));
                            }
                            logger.info("加载[" + dat.size() + "]个词条");
                        } else {
                            cache.put("F", new IOException(response.message));
                        }
                    } finally {
                        response.close();
                    }
                }
            });
        }
        return true;
    }

    /**
     * Test local/remote file is modified
     *
     * @param path the file path
     * @param callback a request {@link Callback}
     * @throws IOException if file not exists or others I/O error occur
     */
    private void checkAndRequestOnModified(String path, Scheme scheme, boolean mandatory, Callback callback) throws IOException {
        if (path == null || path.isEmpty()) return;
        boolean modified = mandatory;
        if (!mandatory) {
            Object slm = cache.get("LM" + path);
            Instant lm = slm != null ? (Instant) slm : null;
            switch (scheme) {
                case http:
                case https:
                    String k_lm = "LM" + path, k_et = "ET" + path;
                    Object et = cache.get(k_et);
                    modified = remoteCheck(path, lm, (String) et, e -> {
                        cache.put(k_lm, e.lastModified);
                        cache.put(k_et, e.eTag);
                    });
                    break;
                case local:
                    Path localPath = Paths.get(path);
                    // File not exists
                    if (!Files.exists(localPath) && HanLP.Config.IOAdapter != null) {
                        // Check if in jar
                        URL  url = getClass().getClassLoader().getResource(path);
                        if (url == null)
                            throw new IOException("文件" + path + "不存在");
                        else
                            modified = false;
                    } else {
                        FileTime fileTime = Files.getLastModifiedTime(localPath);
                        modified = lm == null || fileTime.toInstant().isAfter(lm);
                        if (modified) {
                            cache.put("LM" + path, fileTime.toInstant());
                        }
                    }
                    break;
            }
        }
        if (modified) {
            logger.info("开始拉取:"+ path);
            request(path, scheme, callback);
        } else {
            logger.info(path + "没有更新无需更新");
        }
    }

    // --------------PRIVATE FUNCTIONS--------------

    private void checkAndCacheParam(String customPath) throws IOException {
        if (customPath == null || customPath.isEmpty()) {
            throw new IOException("未指定自定义词典文件路径.");
        }

        if (customPath.equals(cache.get("CP")) && (sync_status == 1 || sync_status == 2)) {
            logger.warning("已有相同任务正在执行。");
            throw new IOException("已有相同任务正在执行。");
        }

        String[] paths = customPath.split(";");
        Scheme[] schemes = new Scheme[paths.length];

        cache.put("CP", paths);
        // Check scheme
        String scheme = null;
        try {
            for (int i = 0; i < paths.length; i++) {
                URI remotePath = new URI(paths[i]);
                schemes[i] = (scheme = remotePath.getScheme()) == null ? Scheme.local : Scheme.valueOf(scheme);
            }
            cache.put("SC", schemes);
        } catch (URISyntaxException e) {
            throw new IOException("无法解析自定义词典文件路径", e);
        } catch (IllegalArgumentException e) {
            throw new IOException("不支持的远程协议: " + scheme);
        }
    }

    @SuppressWarnings({"unchecked"})
    private void commit() {
        sync_status = 2;
        cache.remove("F");
        Object trie = cache.remove("S");
        if (trie instanceof DoubleArrayTrie) {
            this.setDat((DoubleArrayTrie<CoreDictionary.Attribute>) trie);
            logger.info("刷新[%s]成功" + Arrays.toString((String[]) cache.get("CP")));
        }
    }

    private void rollback() {
        sync_status = 2;
        cache.remove("F");
        cache.remove("S");
        logger.warning("刷新[%s]失败" + Arrays.toString((String[]) cache.get("CP")));
    }


    /**
     * 加载词典
     * @param stream 缓存文件文件名
     * @param dat 自定义词典
     */
    public static boolean loadFromInputStream(InputStream stream, DoubleArrayTrie<CoreDictionary.Attribute> dat) {
        logger.info("开始创建trie树");

        try {
            ByteArray byteArray = new ByteArray(IOUtil.getBytes(stream));
            int size = byteArray.nextInt();
            // 一种兼容措施,当size小于零表示文件头部储存了-size个用户词性
            if (size < 0) {
                while (++size <= 0) {
                    Nature.create(byteArray.nextString());
                }
                size = byteArray.nextInt();
            }
            CoreDictionary.Attribute[] attributes = new CoreDictionary.Attribute[size];
            final Nature[] natureIndexArray = Nature.values();
            for (int i = 0; i < size; ++i) {
                // 第一个是全部频次，第二个是词性个数
                int currentTotalFrequency = byteArray.nextInt();
                int length = byteArray.nextInt();
                attributes[i] = new CoreDictionary.Attribute(length);
                attributes[i].totalFrequency = currentTotalFrequency;
                for (int j = 0; j < length; ++j) {
                    attributes[i].nature[j] = natureIndexArray[byteArray.nextInt()];
                    attributes[i].frequency[j] = byteArray.nextInt();
                }
            }
            // TODO 此方法无法追加值，后面的加载会覆盖前面的值
            if (!dat.load(byteArray, attributes)) return false;
        }
        catch (Exception e) {
            logger.warning("读取失败，问题发生在" + TextUtility.exceptionToString(e));
            return false;
        }
        return true;
    }
}
