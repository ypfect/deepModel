package com.deepmodel.relation.service;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 移植自 ai-server 的 Neo4j NLP 图搜索门面。
 * 负责通过带权重的 Cypher 查询在图数据库中进行自然语言评分匹配。
 */
@Slf4j
@Service
public class Neo4jSearchFacade {

    private final Driver neo4jDriver;

    @Autowired
    public Neo4jSearchFacade(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    /**
     * 提取核心词（脱除常见业务后缀，防止匹配泛滥）
     */
    private String extractCoreWord(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        String[] suffixes = {
                "行", "列表", "明细", "子表", "明细表", "明细列表",
                "详情", "信息", "数据", "项目", "内容", "子项",
                "计数", "总数", "合计", "汇总", "统计",
                "编号", "代码", "标识", "ID", "Id", "id", "参照"
        };

        String coreWord = text.trim();
        for (String suffix : suffixes) {
            if (coreWord.endsWith(suffix) && coreWord.length() > suffix.length() + 1) {
                coreWord = coreWord.substring(0, coreWord.length() - suffix.length());
                break; // 只去除一次，避免过度裁剪
            }
        }
        return coreWord.trim();
    }

    /**
     * 在全图中扫描，寻找与目标意图最匹配的基准对象（Base Object）
     *
     * @param keyword  自然语言搜索词
     * @param tenantId 可选，租户过滤（ot.tenantId = $tenantId OR ot.tenantId IS NULL）
     * @param appName  可选，appName 过滤（toLower(ot.appName) CONTAINS toLower($appName)）
     * @return 最匹配的 ObjectType 的 name (例如 "ArContract")，若无匹配返回 null
     */
    public String findBestBaseObjectByCypher(String keyword, String tenantId, String appName) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }

        // 使用已加载全局业务词库的统一分词器 (JiebaUtils)
        com.huaban.analysis.jieba.JiebaSegmenter segmenter = com.deepmodel.relation.util.JiebaUtils.getSegmenter();
        java.util.List<String> tokens = segmenter.sentenceProcess(keyword.trim());

        java.util.Set<String> stopWords = new java.util.HashSet<>(java.util.Arrays.asList(
                "的", "地", "得", "了", "啊", "和", "这", "那",
                "中", "上", "下", "对应的", "并", "所有", "等",
                "行", "列表", "明细", "子表", "明细表", "明细列表",
                "详情", "信息", "数据", "项目", "内容", "子项",
                "计数", "总数", "合计", "汇总", "统计",
                "编号", "代码", "标识", "ID", "Id", "id", "参照",
                "对应", "相关", "获取", "得到", "返回", "尴展"));

        java.util.List<String> validTerms = new java.util.ArrayList<>();
        for (String t : tokens) {
            String trimmed = t.trim();
            if (trimmed.length() >= 1 && !stopWords.contains(trimmed)) {
                validTerms.add(trimmed);
            }
        }

        // 如果全被过滤了，至少拿去壳的 coreWord 兜底
        if (validTerms.isEmpty()) {
            String core = extractCoreWord(keyword.trim());
            if (!core.isEmpty()) {
                validTerms.add(core);
            } else {
                validTerms.add(keyword.trim());
            }
        }

        // 构造 WHERE 子句过滤：只需包含任意一个词
        StringBuilder whereConditions = new StringBuilder();
        for (int i = 0; i < validTerms.size(); i++) {
            if (i > 0)
                whereConditions.append(" OR ");
            String paramName = "$term" + i;
            whereConditions.append(String.format(
                    "(ot.name CONTAINS %s OR ot.title CONTAINS %s OR %s CONTAINS ot.name OR %s CONTAINS ot.title)",
                    paramName, paramName, paramName, paramName));
        }

        // 修改 ai-server 计分体系：引入词条位置权重（越靠前的词权重越高，比如“应收合同”应大于“项目分类”）
        // 以及加入模糊匹配字符长短差值偏置（避免过长或无关后缀的同前缀表混淆）
        StringBuilder extraWhere = new StringBuilder();
        extraWhere.append(" AND NOT (ot.title ENDS WITH '视图' OR ot.title ENDS WITH '草稿')");
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            extraWhere.append(" AND (ot.tenantId = $tenantId OR ot.tenantId IS NULL)");
        }
        if (appName != null && !appName.trim().isEmpty()) {
            extraWhere.append(" AND (toLower(ot.appName) CONTAINS toLower($appName) OR ot.appName = $appName)");
        }
        String cypher = "MATCH (ot:ObjectType)\n" +
                "WHERE (" + whereConditions.toString()
                + ")" + extraWhere.toString() + "\n" +
                "WITH ot\n" +
                "UNWIND range(0, size($searchTerms)-1) as i\n" +
                "WITH ot, i, $searchTerms[i] as term\n" +
                "WITH ot, i, term, \n" +
                "    CASE \n" +
                "        WHEN ot.name = term OR ot.title = term THEN 1.0\n" +
                "        WHEN ot.title IN [term + '明细', term + '明细表', term + '表', term + '单'] THEN 0.98\n" + // 奖励作为业务主体延伸的子表/单据
                "        WHEN ot.title STARTS WITH term THEN 0.95 - (size(ot.title) - size(term)) * 0.01\n" +
                "        WHEN ot.name CONTAINS term OR ot.title CONTAINS term THEN 0.8 - (size(ot.title) - size(term)) * 0.005\n"
                +
                "        WHEN term CONTAINS ot.name OR term CONTAINS ot.title THEN 0.6\n" +
                "        ELSE 0.0\n" +
                "    END as rawScore\n" +
                "// 按词组倒序赋予位置权重：比如共2个词，第0个权重为 1.5，第1个权重为 1.0\n" +
                "WITH ot, sum(rawScore * (1.0 + (size($searchTerms) - 1 - i) * 0.5)) as finalScore\n" +
                "WHERE finalScore >= 0.6\n" +
                "RETURN ot.name as objectName, ot.title as objectTitle, finalScore as similarity\n" +
                "ORDER BY similarity DESC\n" +
                "LIMIT 1";

        try (Session session = neo4jDriver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("searchTerms", validTerms);
            for (int i = 0; i < validTerms.size(); i++) {
                params.put("term" + i, validTerms.get(i));
            }
            if (tenantId != null && !tenantId.trim().isEmpty()) {
                params.put("tenantId", tenantId.trim());
            }
            if (appName != null && !appName.trim().isEmpty()) {
                params.put("appName", appName.trim());
            }
            log.info("执行 Neo4j 高阶意图识别: 关键词='{}', 核心分词={}, tenantId={}, appName={} Cypher 评分查询已发送...",
                    keyword, validTerms, tenantId, appName);
            Result result = session.run(cypher, params);

            if (result.hasNext()) {
                Record record = result.next();
                String objName = record.get("objectName").asString();
                double sim = record.get("similarity").asDouble();
                log.info("Neo4j 搜索命中图谱起点对象: {} ({}分)", objName, sim);
                return objName;
            } else {
                log.warn("Neo4j 未能从搜索词 '{}' (分词: {}) 中匹配到高于阈值的业务对象", keyword, validTerms);
            }
        } catch (Exception e) {
            log.error("在 Neo4j 中执行最优起点查询失败: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * 在全图中扫描，返回 topK 与目标意图最匹配的对象候选（用于预检索）
     *
     * @param keyword 自然语言搜索词
     * @param limit   最大返回数量
     * @return 按相似度降序的对象列表，每项含 name、title、similarity
     */
    public java.util.List<java.util.Map<String, Object>> findTopKBaseObjectsByCypher(String keyword, int limit) {
        if (keyword == null || keyword.trim().isEmpty() || limit <= 0) {
            return java.util.Collections.emptyList();
        }

        com.huaban.analysis.jieba.JiebaSegmenter segmenter = com.deepmodel.relation.util.JiebaUtils.getSegmenter();
        java.util.List<String> tokens = segmenter.sentenceProcess(keyword.trim());

        java.util.Set<String> stopWords = new java.util.HashSet<>(java.util.Arrays.asList(
                "的", "地", "得", "了", "啊", "和", "这", "那",
                "中", "上", "下", "对应的", "并", "所有", "等",
                "行", "列表", "明细", "子表", "明细表", "明细列表",
                "详情", "信息", "数据", "项目", "内容", "子项",
                "计数", "总数", "合计", "汇总", "统计",
                "编号", "代码", "标识", "ID", "Id", "id", "参照",
                "对应", "相关", "获取", "得到", "返回", "尴展"));

        java.util.List<String> validTerms = new java.util.ArrayList<>();
        for (String t : tokens) {
            String trimmed = t.trim();
            if (trimmed.length() >= 1 && !stopWords.contains(trimmed)) {
                validTerms.add(trimmed);
            }
        }
        if (validTerms.isEmpty()) {
            String core = extractCoreWord(keyword.trim());
            if (!core.isEmpty()) {
                validTerms.add(core);
            } else {
                validTerms.add(keyword.trim());
            }
        }

        StringBuilder whereConditions = new StringBuilder();
        for (int i = 0; i < validTerms.size(); i++) {
            if (i > 0)
                whereConditions.append(" OR ");
            String paramName = "$term" + i;
            whereConditions.append(String.format(
                    "(ot.name CONTAINS %s OR ot.title CONTAINS %s OR %s CONTAINS ot.name OR %s CONTAINS ot.title)",
                    paramName, paramName, paramName, paramName));
        }

        String cypher = "MATCH (ot:ObjectType)\n" +
                "WHERE (" + whereConditions.toString()
                + ") AND NOT (ot.title ENDS WITH '视图' OR ot.title ENDS WITH '草稿')\n" +
                "WITH ot\n" +
                "UNWIND range(0, size($searchTerms)-1) as i\n" +
                "WITH ot, i, $searchTerms[i] as term\n" +
                "WITH ot, i, term, \n" +
                "    CASE \n" +
                "        WHEN ot.name = term OR ot.title = term THEN 1.0\n" +
                "        WHEN ot.title IN [term + '明细', term + '明细表', term + '表', term + '单'] THEN 0.98\n" +
                "        WHEN ot.title STARTS WITH term THEN 0.95 - (size(ot.title) - size(term)) * 0.01\n" +
                "        WHEN ot.name CONTAINS term OR ot.title CONTAINS term THEN 0.8 - (size(ot.title) - size(term)) * 0.005\n" +
                "        WHEN term CONTAINS ot.name OR term CONTAINS ot.title THEN 0.6\n" +
                "        ELSE 0.0\n" +
                "    END as rawScore\n" +
                "WITH ot, sum(rawScore * (1.0 + (size($searchTerms) - 1 - i) * 0.5)) as finalScore\n" +
                "WHERE finalScore >= 0.6\n" +
                "RETURN ot.name as objectName, ot.title as objectTitle, finalScore as similarity\n" +
                "ORDER BY similarity DESC\n" +
                "LIMIT " + Math.min(limit, 20);

        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        try (Session session = neo4jDriver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("searchTerms", validTerms);
            for (int i = 0; i < validTerms.size(); i++) {
                params.put("term" + i, validTerms.get(i));
            }
            Result result = session.run(cypher, params);
            while (result.hasNext()) {
                Record record = result.next();
                java.util.Map<String, Object> item = new HashMap<>();
                item.put("name", record.get("objectName").asString());
                org.neo4j.driver.Value titleVal = record.get("objectTitle");
                item.put("title", titleVal.isNull() ? record.get("objectName").asString() : titleVal.asString());
                item.put("similarity", record.get("similarity").asDouble());
                out.add(item);
            }
        } catch (Exception e) {
            log.warn("Neo4j topK 查询失败，回退到内存匹配: {}", e.getMessage());
        }
        return out;
    }
}
