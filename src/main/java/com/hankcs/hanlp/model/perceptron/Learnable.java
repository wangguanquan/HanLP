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

import com.hankcs.hanlp.corpus.document.sentence.Sentence;

/**
 * Create by guanquan.wang at 2019-12-06 15:36
 */
public interface Learnable {
    /**
     * 在线学习
     *
     * @param segmentedTaggedSentence 已分词、标好词性和命名实体的人民日报2014格式的句子
     * @return 是否学习成果（失败的原因是句子格式不合法）
     */
    boolean learn(String segmentedTaggedSentence);

    /**
     * 在线学习
     *
     * @param sentence 已分词、标好词性和命名实体的人民日报2014格式的句子
     * @return 是否学习成果（失败的原因是句子格式不合法）
     */
    boolean learn(Sentence sentence);

    /**
     * 保存在线学习的model
     *
     * 保存路经与load路径一致且在文件名后面加时间戳
     *
     * @return true if success
     */
    boolean save();
}
