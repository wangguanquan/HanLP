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
import com.hankcs.hanlp.seg.Remote;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Create by guanquan.wang at 2019-12-12 10:02
 */
public interface RemoteLexicalAnalyzer extends Remote {

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

}
