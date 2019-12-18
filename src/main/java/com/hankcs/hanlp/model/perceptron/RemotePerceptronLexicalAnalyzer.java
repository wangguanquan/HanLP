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
import com.hankcs.hanlp.corpus.io.ByteArrayOtherStream;
import com.hankcs.hanlp.model.perceptron.model.LinearModel;
import sun.misc.Contended;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import static com.hankcs.hanlp.utility.Predefine.logger;

/**
 * Create by guanquan.wang at 2019-12-05 09:15
 */
public class RemotePerceptronLexicalAnalyzer extends PerceptronLexicalAnalyzer implements RemoteLexicalAnalyzer {
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
    // Lazy model file on use
//    private boolean loadOnUse;

    // Cache some model base info
    private ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();

    public RemotePerceptronLexicalAnalyzer(PerceptronSegmenter segmenter) {
        super(segmenter);
        status = 3;
    }

    public RemotePerceptronLexicalAnalyzer(PerceptronSegmenter segmenter, PerceptronPOSTagger posTagger) {
        super(segmenter, posTagger);
        status = 3;
    }

    public RemotePerceptronLexicalAnalyzer(PerceptronSegmenter segmenter, PerceptronPOSTagger posTagger, PerceptronNERecognizer neRecognizer) {
        super(segmenter, posTagger, neRecognizer);
        status = 3;
    }

    public RemotePerceptronLexicalAnalyzer(LinearModel cwsModel, LinearModel posModel, LinearModel nerModel) {
        super(cwsModel, posModel, nerModel);
        status = 3;
    }

    public RemotePerceptronLexicalAnalyzer(String cwsModelFile, String posModelFile, String nerModelFile) throws IOException {
        this();
        reload(cwsModelFile, posModelFile, nerModelFile);
    }

    public RemotePerceptronLexicalAnalyzer(String cwsModelFile, String posModelFile) throws IOException {
        this(cwsModelFile, posModelFile, null);
    }

    public RemotePerceptronLexicalAnalyzer(String cwsModelFile) throws IOException {
        this(cwsModelFile, null, null);
    }

    public RemotePerceptronLexicalAnalyzer(LinearModel CWSModel) {
        super(CWSModel);
        status = 3;
    }

    public RemotePerceptronLexicalAnalyzer() {
        // Create a un-mutable feature map
        super(new LinearModel(RemoteLexicalAnalyzer.createEmptyFeatureMap()), null, null);
    }

//    /**
//     * Returns the `load-on-use` mark
//     *
//     * @return boolean value
//     */
//    @Override
//    public boolean getLoadOnUse() {
//        return loadOnUse;
//    }

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
     * @param cwsModelFile the cws model file
     * @param posModelFile the pos model file
     * @param nerModelFile the ner model file
     */
    @Override
    public void lazy(String cwsModelFile, String posModelFile, String nerModelFile) throws IOException {
        checkAndCacheParam(cwsModelFile, posModelFile, nerModelFile);
        new Thread(() -> {
            try {
                refresh();
            } catch (IOException e) {
                logger.warning("加载失败");
                e.printStackTrace();
            }
        }).start();
    }

//    /**
//     * Lazy load remote/local model file on use
//     */
//    @Override
//    public void loadOnUse() {
//        this.loadOnUse = true;
//    }

    /**
     * Load remote/local model file
     *
     * @param cwsModelFile the cws model file
     * @param posModelFile the pos model file
     * @param nerModelFile the ner model file
     * @return true if success
     * @throws IOException if I/O error occur
     */
    @Override
    public boolean reload(String cwsModelFile, String posModelFile, String nerModelFile) throws IOException {
        checkAndCacheParam(cwsModelFile, posModelFile, nerModelFile);
        return refresh(true);
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
            boolean r = loadCwsModelFile(mandatory) && loadPosModelFile(mandatory) && loadNerModelFile(mandatory);
            if (r) {
                Object fcws = cache.get("FCWS"), fpos = cache.get("FPOS"), fner = cache.get("FNER");
                if (fcws instanceof Exception) {
                    hasErr = true;
                    ((Exception) fcws).printStackTrace();
                }
                if (fpos instanceof Exception) {
                    hasErr = true;
                    ((Exception) fpos).printStackTrace();
                }
                if (fner instanceof Exception) {
                    hasErr = true;
                    ((Exception) fner).printStackTrace();
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

    @Override
    public boolean save() {
        return false;
    }

    // --------------OVERRIDE FUNCTIONS--------------
//
//    @Override
//    public List<String> segment(String text) {
//        checkStatusAndLoad();
//        return super.segment(text);
//    }
//
//    @Override
//    public void segment(String text, String normalized, List<String> output) {
//        checkStatusAndLoad();
//        super.segment(text, normalized, output);
//    }
//
//    @Override
//    public String[] tag(String... words) {
//        checkStatusAndLoad();
//        return super.tag(words);
//    }
//
//    @Override
//    public String[] tag(List<String> wordList) {
//        checkStatusAndLoad();
//        return super.tag(wordList);
//    }
//
//    @Override
//    public String[] recognize(String[] wordArray, String[] posArray) {
//        checkStatusAndLoad();
//        return super.recognize(wordArray, posArray);
//    }
//
//    @Override
//    public Sentence analyze(final String sentence) {
//        checkStatusAndLoad();
//        return super.analyze(sentence);
//    }

    // --------------PRIVATE FUNCTIONS--------------

    private void checkAndCacheParam(String cwsModelFile, String posModelFile, String nerModelFile) throws IOException {
        if (cwsModelFile == null || cwsModelFile.isEmpty()) {
            throw new IOException("未指定CWS文件路径.");
        }

        if (cwsModelFile.equals(cache.get("CP")) && (sync_status == 1 || sync_status == 2)) {
            logger.warning("已有相同任务正在执行。");
            throw new IOException("已有相同任务正在执行。");
        }

        cache.put("cws", cwsModelFile);
        // Check scheme
        String scheme = null;
        try {
            URI cwsPath = new URI(cwsModelFile);
            cache.put("SC" + cwsModelFile, (scheme = cwsPath.getScheme()) == null ? Scheme.local : Scheme.valueOf(scheme));
        } catch (URISyntaxException e) {
            throw new IOException("无法解析CWS文件路径", e);
        } catch (IllegalArgumentException e) {
            throw new IOException("不支持的远程协议: " + scheme);
        }

        if (posModelFile != null && !posModelFile.isEmpty()) {
            cache.put("pos", posModelFile);
            try {
                URI posPath = new URI(posModelFile);
                cache.put("SC" + posModelFile, (scheme = posPath.getScheme()) == null ? Scheme.local : Scheme.valueOf(scheme));
            } catch (URISyntaxException e) {
                throw new IOException("无法解析POS文件路径", e);
            } catch (IllegalArgumentException e) {
                throw new IOException("不支持的远程协议: " + scheme);
            }
        }

        if (nerModelFile != null && !nerModelFile.isEmpty()) {
            cache.put("ner", nerModelFile);
            try {
                URI nerPath = new URI(nerModelFile);
                cache.put("SC" + nerModelFile, (scheme = nerPath.getScheme()) == null ? Scheme.local : Scheme.valueOf(scheme));
            } catch (URISyntaxException e) {
                throw new IOException("无法解析NER文件路径", e);
            } catch (IllegalArgumentException e) {
                throw new IOException("不支持的远程协议: " + scheme);
            }
        }
    }

    // Load cws model file
    private boolean loadCwsModelFile(boolean mandatory) throws IOException {
        String path = (String) cache.get("cws");
        if (path == null || path.isEmpty()) {
            throw new IOException("未指定CWS文件路径.");
        }
        checkAndRequestOnModified(path, mandatory, new Callback() {
            @Override
            public void onFailure(IOException e) {
                cache.put("FCWS", e);
            }

            @Override
            public void onResponse(Response response) throws IOException {
                try {
                    if (response.code == 200) {
                        LinearModel model = new LinearModel(null, null);
                        model.load(ByteArrayOtherStream.createByteArrayOtherStream(response.stream));
                        cache.put("SCWS", new PerceptronSegmenter(model));
                    } else {
                        cache.put("FCWS", new IOException(response.message));
                    }
                } finally {
                    response.close();
                }
            }
        });
        return true;
    }

    // Load pos model file
    private boolean loadPosModelFile(boolean mandatory) throws IOException {
        String path = (String) cache.get("pos");
        checkAndRequestOnModified(path, mandatory, new Callback() {
            @Override
            public void onFailure(IOException e) {
                cache.put("FPOS", e);
            }

            @Override
            public void onResponse(Response response) throws IOException {
                try {
                    if (response.code == 200) {
                        LinearModel model = new LinearModel(null, null);
                        model.load(ByteArrayOtherStream.createByteArrayOtherStream(response.stream));
                        cache.put("SPOS", new PerceptronPOSTagger(model));
                    } else {
                        cache.put("FPOS", new IOException(response.message));
                    }
                } finally {
                    response.close();
                }
            }
        });
        return true;
    }

    // Load ner model file
    private boolean loadNerModelFile(boolean mandatory) throws IOException {
        String path = (String) cache.get("ner");
        checkAndRequestOnModified(path, mandatory, new Callback() {
            @Override
            public void onFailure(IOException e) {
                cache.put("FNER", e);
            }

            @Override
            public void onResponse(Response response) throws IOException {
                try {
                    if (response.code == 200) {
                        LinearModel model = new LinearModel(null, null);
                        model.load(ByteArrayOtherStream.createByteArrayOtherStream(response.stream));
                        cache.put("SNER", new PerceptronNERecognizer(model));
                    } else {
                        cache.put("FNER", new IOException(response.message));
                    }
                } finally {
                    response.close();
                }
            }
        });
        return true;
    }

    /**
     * Test local/remote file is modified
     *
     * @param path the file path
     * @param callback a request {@link Callback}
     * @throws IOException if file not exists or others I/O error occur
     */
    private void checkAndRequestOnModified(String path, boolean mandatory, Callback callback) throws IOException {
        if (path == null || path.isEmpty())
            return;
        boolean modified = mandatory;
        Scheme scheme = (Scheme) cache.get("SC" + path);
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
                        URL url = getClass().getClassLoader().getResource(path);
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
            logger.info("开始拉取:" + path);
            request(path, scheme, callback);
        } else {
            logger.info(path + "没有更新无需更新");
        }
    }

    private void commit() {
        sync_status = 2;
        cache.remove("FCWS");
        cache.remove("FPOS");
        cache.remove("FNER");
        Object scws = cache.remove("SCWS"), spos = cache.remove("SPOS"), sner = cache.remove("SNER");
        if (scws instanceof PerceptronSegmenter) {
            this.segmenter = (PerceptronSegmenter) scws;
            logger.info("刷新[%s]成功" + cache.get("cws"));
        }
        if (spos instanceof PerceptronPOSTagger) {
            this.posTagger = (PerceptronPOSTagger) spos;
            this.config.speechTagging = true;
            logger.info("刷新[%s]成功" + cache.get("pos"));
        }
        if (sner instanceof PerceptronNERecognizer) {
            this.neRecognizer = (PerceptronNERecognizer) sner;
            this.config.ner = true;
            logger.info("刷新[%s]成功" + cache.get("ner"));
        }
    }

    private void rollback() {
        sync_status = 2;
        cache.remove("FCWS");
        cache.remove("FPOS");
        cache.remove("FNER");
        cache.remove("SCWS");
        cache.remove("SPOS");
        cache.remove("SNER");
        logger.warning("刷新[%s]失败" + cache.get("cws"));
    }
}
