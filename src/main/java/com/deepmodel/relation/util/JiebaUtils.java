package com.deepmodel.relation.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.WordDictionary;

/**
 * Jieba 分词工具。领域词典从 classpath {@code dict/*.txt} 自动加载（随 JAR 打包）。
 */
@Slf4j
public final class JiebaUtils {

    private static final String DICT_PATTERN = "classpath:dict/*.txt";
    private static final String SYNONYMS_FILE = "synonyms.txt";
    /** 优先加载的文件（其余按文件名字母序） */
    private static final List<String> PRIORITY_FILES = Arrays.asList(
            "manual.txt",
            "base_object_types.txt",
            "base_object_fields.txt");

    private static final JiebaSegmenter SEGMENTER = new JiebaSegmenter();
    private static volatile boolean initialized;

    private JiebaUtils() {}

    public static JiebaSegmenter getSegmenter() {
        ensureInitialized();
        return SEGMENTER;
    }

    /**
     * 从 classpath {@code dict/synonyms.txt} 加载对象同义词。
     * 格式：{@code ObjectType=同义词1,同义词2}，{@code #} 开头为注释。
     */
    public static Map<String, List<String>> loadSynonyms() {
        Map<String, List<String>> result = new HashMap<>();
        try (InputStream is = JiebaUtils.class.getClassLoader().getResourceAsStream("dict/synonyms.txt")) {
            if (is == null) {
                log.warn("未找到 dict/synonyms.txt，同义词表为空");
                return result;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int eq = line.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String objectType = line.substring(0, eq).trim();
                    String rest = line.substring(eq + 1).trim();
                    if (objectType.isEmpty() || rest.isEmpty()) {
                        continue;
                    }
                    List<String> syns = new ArrayList<>();
                    for (String part : rest.split(",")) {
                        String syn = part.trim();
                        if (!syn.isEmpty()) {
                            syns.add(syn);
                        }
                    }
                    if (!syns.isEmpty()) {
                        result.put(objectType, syns);
                    }
                }
            }
            log.info("JiebaUtils 成功加载同义词表: {} 个对象", result.size());
        } catch (Exception e) {
            log.warn("JiebaUtils 加载同义词表失败: {}", e.getMessage());
        }
        return result;
    }

    private static synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        try {
            WordDictionary wordDict = WordDictionary.getInstance();
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(
                    JiebaUtils.class.getClassLoader());
            Resource[] resources = resolver.getResources(DICT_PATTERN);
            List<Resource> sorted = sortDictResources(resources);
            int loaded = 0;
            for (Resource resource : sorted) {
                String filename = resource.getFilename();
                if (filename == null || SYNONYMS_FILE.equals(filename)) {
                    continue;
                }
                if (loadDictFile(wordDict, resource, filename)) {
                    loaded++;
                }
            }
            log.info("JiebaUtils 初始化完成，共加载 {} 个词典文件", loaded);
        } catch (Exception e) {
            log.error("JiebaUtils 初始化失败", e);
        }
        initialized = true;
    }

    private static List<Resource> sortDictResources(Resource[] resources) {
        List<Resource> list = new ArrayList<>(Arrays.asList(resources));
        list.sort(Comparator
                .comparingInt((Resource r) -> {
                    String name = r.getFilename();
                    if (name == null) {
                        return PRIORITY_FILES.size();
                    }
                    int idx = PRIORITY_FILES.indexOf(name);
                    return idx >= 0 ? idx : PRIORITY_FILES.size();
                })
                .thenComparing(r -> {
                    String name = r.getFilename();
                    return name != null ? name : "";
                }));
        return list;
    }

    private static boolean loadDictFile(WordDictionary wordDict, Resource resource, String filename) {
        try (InputStream is = resource.getInputStream()) {
            File tempFile = File.createTempFile("jieba-dict-", ".txt");
            tempFile.deleteOnExit();

            try (
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
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
            Path path = tempFile.toPath();
            wordDict.loadUserDict(path);
            log.info("JiebaUtils 成功加载词典: {}", filename);
            return true;
        } catch (Exception e) {
            log.warn("JiebaUtils 加载词典 {} 失败: {}", filename, e.getMessage());
            return false;
        }
    }
}
