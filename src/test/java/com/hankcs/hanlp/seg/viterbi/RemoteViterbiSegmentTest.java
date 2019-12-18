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

package com.hankcs.hanlp.seg.viterbi;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.Viterbi.RemoteViterbiSegment;
import com.hankcs.hanlp.seg.Viterbi.ViterbiSegment;
import junit.framework.TestCase;

import java.io.IOException;

/**
 * Create by guanquan.wang at 2019-12-12 10:25
 */
public class RemoteViterbiSegmentTest extends TestCase {

    public void testDefaultConstructor() {
        HanLP.Config.enableDebug();
        HanLP.Config.IOAdapter = null;
        RemoteViterbiSegment segment = new RemoteViterbiSegment();
        System.out.println(segment.seg("光电传感器O300.GR-11135876(Baumer)可推荐".toLowerCase()));
    }

    public void testConstructor() throws IOException {
        HanLP.Config.enableDebug();
        HanLP.Config.IOAdapter = null;
        RemoteViterbiSegment segment = new RemoteViterbiSegment("http://localhost/hanlp/CustomDictionary.txt.bin");
        System.out.println(segment.seg("光电传感器O300.GR-11135876(Baumer)可推荐".toLowerCase()));
    }

    public synchronized void testLazy() throws IOException, InterruptedException {
        HanLP.Config.enableDebug();
        HanLP.Config.IOAdapter = null;
        RemoteViterbiSegment segment = new RemoteViterbiSegment();
        segment.lazy("http://localhost/hanlp/catg%20cg.bin;http://localhost/hanlp/brand%20br.bin");
        System.out.println(segment.seg("光电传感器O300.GR-11135876(Baumer)可推荐".toLowerCase()));
        System.out.println(segment.seg("明装式控制箱 XK-B1/-Y 正泰".toLowerCase()));
        System.out.println(segment.seg("正泰断路器 DZ47　3P32A 正泰".toLowerCase()));

        wait(20_000);
        if (segment.isReady()) {
            System.out.println(segment.seg("光电传感器O300.GR-11135876(Baumer)可推荐".toLowerCase()));
            System.out.println(segment.seg("明装式控制箱 XK-B1/-Y 正泰".toLowerCase()));
            System.out.println(segment.seg("正泰断路器 DZ47　3P32A 正泰".toLowerCase()));
        }
    }

    public void testViterbi() {
//        HanLP.Config.enableDebug();
        HanLP.Config.IOAdapter = null;
        ViterbiSegment segment = new ViterbiSegment("data/dictionary/mro/mro.bin;" +
            "data/dictionary/mro/brand.txt;" +
            "data/dictionary/mro/b2r_seller_goods-categoryNames.keyword.txt;" +
            "data/dictionary/mro/goods_name_chinese_3.txt;" +
            "data/dictionary/mro/搜索服务-词典.txt"
            , true);
//        ViterbiSegment segment = new ViterbiSegment("data/dictionary/custom/catg cg.bin;" +
//            "data/dictionary/custom/b2r_seller_goods-categoryNames.keyword.txt"
//            , true);
//        "data/dictionary/custom/b2r_seller_goods-categoryNames.keyword.txt"
//        ViterbiSegment segment = new ViterbiSegment("data/dictionary/custom/1664964549.bin");
        System.out.println(segment.seg("光电传感器O300.GR-11135876(Baumer)可推荐".toLowerCase()));
        System.out.println(segment.seg("明装式控制箱 XK-B1/-Y 正泰".toLowerCase()));
        System.out.println(segment.seg("正泰断路器 DZ47　3P32A 正泰".toLowerCase()));
    }
}
