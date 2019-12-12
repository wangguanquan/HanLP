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

import com.hankcs.hanlp.model.perceptron.feature.FeatureMap;
import com.hankcs.hanlp.model.perceptron.tagset.CWSTagSet;
import com.hankcs.hanlp.tokenizer.lexical.LexicalAnalyzer;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Consumer;

/**
 * Create by guanquan.wang at 2019-12-06 08:39
 */
public interface RemoteLexicalAnalyzer extends LexicalAnalyzer {

    /**
     * Returns this analyzer is ready
     *
     * @return true if it is ready
     */
    boolean isReady();

    /**
     * Returns this analyzer status
     *
     * @return the status int value
     *     0: unload model files
     *     1: getting remote files
     *     2: flushing new model files
     *     3: analyzer is ready
     */
    int getStatus();

//    /**
//     * Lazy load remote/local model file on use
//     */
//    void loadOnUse();

    /**
     * Lazy load remote/local model file
     *
     * @param cwsModelFile the cws model file
     * @param posModelFile the pos model file
     * @param nerModelFile the ner model file
     */
    void lazy(String cwsModelFile, String posModelFile, String nerModelFile) throws IOException;

    /**
     * Load remote/local model file
     *
     * @param cwsModelFile the cws model file
     * @param posModelFile the pos model file
     * @param nerModelFile the ner model file
     * @return true if success
     * @throws IOException if I/O error occur
     */
    boolean reload(String cwsModelFile, String posModelFile, String nerModelFile) throws IOException;

    /**
     * Refresh all model
     *
     * @return true if success
     */
    boolean refresh() throws IOException;

    /**
     * Refresh all model, Whether to force a refresh regardless of whether
     * the file has changed if `mandatory` is true
     *
     * @param mandatory a force refresh flag
     * @return true if success
     * @throws IOException if I/O error occur
     */
    boolean refresh(boolean mandatory) throws IOException;

//    /**
//     * Returns the `load-on-use` mark
//     *
//     * @return boolean value
//     */
//    boolean getLoadOnUse();

    // Sync request
    default void remote(String path, Callback callback) {
        HttpURLConnection con = null;
        try {
            con = ((HttpURLConnection) new URL(path).openConnection());
            con.setRequestMethod("GET");
            con.setConnectTimeout(300_000);
            con.setDoOutput(false);
            con.setDoInput(true);
            con.setUseCaches(false);
            con.connect();

            int code = con.getResponseCode();
            Response response = new Response();
            response.code = code;
            response.message = con.getResponseMessage();
            response.contentEncoding = con.getContentEncoding();
            if (code == 200) {
                InputStream is = con.getErrorStream();
                if (is == null) {
                    response.stream = con.getInputStream();
                } else {
                    response.code = 500;
                    response.message = string(is, null);
                }
            }
            callback.onResponse(response);
        }
        catch (IOException e) {
            callback.onFailure(e);
        }
        catch (Exception e) {
            callback.onFailure(new IOException(e));
        }
        finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    default boolean remoteCheck(String path, Instant lastModifiedTime, String lastTag
        , Consumer<HeadCheckResponse> onModified) throws IOException {
        HttpURLConnection con = null;
        try {
            con = ((HttpURLConnection) new URL(path).openConnection());
            con.setRequestMethod("HEAD");
            con.setConnectTimeout(300_000);
            con.setDoOutput(false);
            con.setDoInput(true);
            con.setUseCaches(false);
            con.connect();

            int code = con.getResponseCode();
            if (code == 200) {
                long m = con.getHeaderFieldDate("Last-Modified", 0L);
                String tag = con.getHeaderField("ETag");

                boolean modified = lastModifiedTime == null || Instant.ofEpochMilli(m).isAfter(lastModifiedTime);

                if (!modified && lastTag != null) {
                    modified = !lastTag.equals(tag);
                }

                if (modified) {
                    onModified.accept(new HeadCheckResponse(Instant.ofEpochMilli(m), tag));
                }
                return modified;
            }
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
        return false;
    }

    default void request(String path, Scheme scheme, Callback callback) {
        if (path == null || path.isEmpty())
            return;
        switch (scheme) {
            case http:
            case https:
                remote(path, callback);
                break;
            case local:
                Path localPath = Paths.get(path);
                // File not exists
                if (!Files.exists(localPath)) {
                    callback.onFailure(new IOException("文件" + path + "不存在"));
                }
                Response response = new Response();
                response.code = 200;
                try {
                    response.stream = Files.newInputStream(localPath);
                    callback.onResponse(response);
                } catch (IOException e) {
                    callback.onFailure(e);
                }
                break;
        }
    }

//    default void checkStatusAndLoad() {
//        if (isReady()) return;
//        if (!getLoadOnUse())
//            throw new AnalyzerNotReadyException();
//        else {
//            try {
//                refresh();
//            } catch (IOException e) {
//                throw new UncheckedIOException(e);
//            }
//        }
//    }

    /**
     * Reading and convert to string
     *
     * @param stream an {@link InputStream}
     * @param contentEncoding content encode
     * @return string value
     * @throws IOException if I/O error occur
     */
    static String string(InputStream stream, String contentEncoding) throws IOException {
        if (stream == null) {
            return null;
        }
        byte[] bytes = new byte[4096];
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (int n; (n = stream.read(bytes)) > 0; ) {
            bos.write(bytes, 0, n);
        }
        bytes = bos.toByteArray();
        return new String(bytes, 0, bytes.length, contentEncoding != null
            ? contentEncoding : StandardCharsets.UTF_8.name());
    }

    /**
     * List all support protocol
     *
     * @return protocol string
     */
    static String getSupportScheme() {
        Scheme[] schemes = Scheme.values();
        StringJoiner joiner = new StringJoiner(", ");
        for (Scheme scheme : schemes) {
            joiner.add(scheme == Scheme.local ? "本地文件" : scheme.name());
        }
        return joiner.toString();
    }

    /**
     * Create an empty and un-mutable feature map
     *
     * @return {@link FeatureMap}
     */
    static FeatureMap createEmptyFeatureMap() {
        FeatureMap featureMap = new FeatureMap() {
            @Override
            public int size() {
                return 0;
            }

            @Override
            public Set<Map.Entry<String, Integer>> entrySet() {
                return Collections.emptySet(); // Empty set
            }

            @Override
            public int idOf(String string) {
                return -1; // OutOfBound index
            }
        };
        featureMap.mutable = false;
        featureMap.tagSet = new CWSTagSet();
        return featureMap;
    }

    /**
     * Model file scheme
     */
    enum Scheme {
        local, http, https
    }

    /**
     * Http request callback
     */
    interface Callback {
        /**
         * Called when the request could not be executed due to cancellation, a connectivity problem or
         * timeout. Because networks can fail during an exchange, it is possible that the remote server
         * accepted the request before the failure.
         */
        void onFailure(IOException e);

        /**
         * Called when the HTTP response was successfully returned by the remote server. The callback may
         * proceed to read the response body with [Response.body]. The response is still live until its
         * response body is [closed][ResponseBody]. The recipient of the callback may consume the response
         * body on another thread.
         *
         * Note that transport-layer success (receiving a HTTP response code, headers and body) does not
         * necessarily indicate application-layer success: `response` may still indicate an unhappy HTTP
         * response code like 404 or 500.
         */
        void onResponse(RemotePerceptronLexicalAnalyzer.Response response) throws IOException;
    }

    /**
     * Http response
     */
    class Response implements Closeable {
        /* the HTTP status code. */
        protected int code;
        /* the HTTP status message. */
        protected String message;
        protected String contentEncoding;
        protected InputStream stream;

        /**
         * Returns the response as a string.
         *
         * If the response starts with a
         * [Byte Order Mark (BOM)](https://en.wikipedia.org/wiki/Byte_order_mark), it is consumed and
         * used to determine the charset of the response bytes.
         *
         * Otherwise if the response has a `Content-Type` header that specifies a charset, that is used
         * to determine the charset of the response bytes.
         *
         * Otherwise the response bytes are decoded as UTF-8.
         *
         * This method loads entire response body into memory. If the response body is very large this
         * may trigger an [OutOfMemoryError]. Prefer to stream the response body if this is a
         * possibility for your response.
         *
         * @return response string
         * @throws IOException if I/O error occurs
         */
        public String string() throws IOException {
            return RemoteLexicalAnalyzer.string(stream, contentEncoding);
        }

        /**
         * Closes this stream and releases any system resources associated
         * with it.
         *
         * @throws IOException if an I/O error occurs
         */
        @Override
        public void close() throws IOException {
            if (stream != null)
                stream.close();
        }
    }

    /**
     * A `HEAD` check response
     */
    class HeadCheckResponse {
        protected Instant lastModified;
        protected String eTag;

        protected HeadCheckResponse(Instant lastModified, String eTag) {
            this.lastModified = lastModified;
            this.eTag = eTag;
        }
    }
}
