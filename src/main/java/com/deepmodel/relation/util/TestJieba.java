package com.deepmodel.relation.util;

import com.huaban.analysis.jieba.JiebaSegmenter;
import java.util.List;

public class TestJieba {
    public static void main(String[] args) {
        JiebaSegmenter segmenter = JiebaUtils.getSegmenter();
        String text = "应收合同标的的项目分类";
        List<String> tokens = segmenter.sentenceProcess(text);
        System.out.println("sentenceProcess: " + tokens);
        System.out.println("process (INDEX): " + segmenter.process(text, JiebaSegmenter.SegMode.INDEX));
    }
}
