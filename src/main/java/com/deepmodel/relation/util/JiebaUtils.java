package com.deepmodel.relation.util;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.WordDictionary;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class JiebaUtils {
    private static final JiebaSegmenter segmenter = new JiebaSegmenter();

    static {
        try {
            WordDictionary wordDict = WordDictionary.getInstance();
            List<String> dictFiles = Arrays.asList(
                    "dict/manual.txt",
                    "dict/base_object_types.txt",
                    "dict/base_object_fields.txt");

            for (String fileStr : dictFiles) {
                try (InputStream is = JiebaUtils.class.getClassLoader().getResourceAsStream(fileStr)) {
                    if (is != null) {
                        File tempFile = File.createTempFile("jieba-dict-", ".txt");
                        tempFile.deleteOnExit();

                        try (
                                BufferedReader br = new BufferedReader(
                                        new InputStreamReader(is, StandardCharsets.UTF_8));
                                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                                        new FileOutputStream(tempFile), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                String[] tokens = line.split("\\s+");
                                if (tokens.length >= 1 && !tokens[0].trim().isEmpty()) {
                                    bw.write(tokens[0].trim() + " 99999 n");
                                    bw.newLine();
                                }
                            }
                        }
                        wordDict.loadUserDict(tempFile.toPath());
                        log.info("JiebaUtils 成功加载词典: {}", fileStr);
                    }
                } catch (Exception e) {
                    log.warn("JiebaUtils 加载词典 {} 失败: {}", fileStr, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("JiebaUtils 初始化失败", e);
        }
    }

    public static JiebaSegmenter getSegmenter() {
        return segmenter;
    }
}
