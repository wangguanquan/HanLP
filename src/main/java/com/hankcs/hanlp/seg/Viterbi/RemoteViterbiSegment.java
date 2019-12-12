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

import com.hankcs.hanlp.collection.trie.DoubleArrayTrie;
import com.hankcs.hanlp.dictionary.CoreDictionary;
import com.hankcs.hanlp.dictionary.CustomDictionary;
import com.hankcs.hanlp.seg.Remote;
import com.hankcs.hanlp.utility.TextUtility;
import sun.misc.Contended;

import java.io.File;
import java.io.IOException;

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

    public RemoteViterbiSegment() { }

    /**
     * @param customPath local/remote model path
     *                   Use `;` to connect multiple paths
     */
    public RemoteViterbiSegment(String customPath)
    {
        loadCustomDic(customPath, false);
    }

    /**
     * @param customPath local/remote model path
     *                   Use `;` to connect multiple paths
     * @param cache      cache bin file if true
     */
    public RemoteViterbiSegment(String customPath, boolean cache)
    {
        loadCustomDic(customPath, cache);
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

    @Override
    public boolean refresh() throws IOException {
        return false;
    }

    @Override
    public boolean refresh(boolean mandatory) throws IOException {
        return false;
    }

    /**
     * Load customize dictionary
     *
     * @param customPath local/remote model path
     *                   Use `;` to connect multiple paths
     * @param isCache cache bin file if true
     */
    protected void loadCustomDic(String customPath, boolean isCache) {
        if (TextUtility.isBlank(customPath))
        {
            return;
        }
//        logger.info("开始加载自定义词典:" + customPath);
//        DoubleArrayTrie<CoreDictionary.Attribute> dat = new DoubleArrayTrie<>();
//        String[] path = customPath.split(";");
//        String mainPath = path[0];
//        StringBuilder combinePath = new StringBuilder();
//        for (String aPath : path)
//        {
//            combinePath.append(aPath.trim());
//        }
//        File file = new File(mainPath);
//        mainPath = file.getParent() + "/" + Math.abs(combinePath.toString().hashCode());
//        mainPath = mainPath.replace("\\", "/");
//        if (CustomDictionary.loadMainDictionary(mainPath, path, dat, isCache))
//        {
//            this.setDat(dat);
//        }
    }
}
