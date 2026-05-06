package com.deepmodel.relation.service;

import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.CascadeWriteBackInfo;
import com.deepmodel.relation.model.WriteBackExpr;
import com.deepmodel.relation.model.WriteBackRelationInfo;
import com.deepmodel.relation.util.ExprUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 回写触发关系图服务。
 * <p>
 * 从所有含 writeBackExpr 的字段中构建回写触发全景索引：
 * <ul>
 *   <li>srcObject → targetObject → Set&lt;WriteBackRelationInfo&gt;</li>
 *   <li>targetObject → targetField → Set&lt;sourceVars&gt;</li>
 *   <li>级联回写检测（A 回写 B.fieldX，B.fieldX 又回写 C）</li>
 * </ul>
 */
@Service
public class WriteBackRelationService {

    private static final Logger log = LoggerFactory.getLogger(WriteBackRelationService.class);

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            .configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true);

    /** srcObjectType → targetObjectType → Set&lt;WriteBackRelationInfo&gt; */
    private final Map<String, Map<String, Set<WriteBackRelationInfo>>> srcIndex = new ConcurrentHashMap<>();

    /** targetObjectType → targetFieldName → Set&lt;sourceVars&gt; */
    private final Map<String, Map<String, Set<String>>> targetFieldVarsIndex = new ConcurrentHashMap<>();

    /** srcObjectType → List&lt;CascadeWriteBackInfo&gt; */
    private final Map<String, List<CascadeWriteBackInfo>> cascadeIndex = new ConcurrentHashMap<>();

    /**
     * 从字段列表构建回写触发索引。
     *
     * @param allRows 所有字段记录
     */
    public void buildIndex(List<BaseappObjectField> allRows) {
        long t0 = System.currentTimeMillis();
        srcIndex.clear();
        targetFieldVarsIndex.clear();
        cascadeIndex.clear();

        // 第一步：构建 srcObject → targetObject → fields 索引
        for (BaseappObjectField row : allRows) {
            if (row.getWriteBackExpr() == null || row.getWriteBackExpr().trim().isEmpty()) {
                continue;
            }
            try {
                WriteBackExpr wbe = objectMapper.readValue(row.getWriteBackExpr(), WriteBackExpr.class);
                if (wbe == null || wbe.getSrcObjectType() == null || wbe.getSrcObjectType().isEmpty()) {
                    continue;
                }
                WriteBackRelationInfo info = new WriteBackRelationInfo();
                info.setSrcObjectType(wbe.getSrcObjectType());
                info.setTargetObjectType(row.getObjectType());
                info.setTargetFieldName(row.getName());
                info.setExpression(wbe.getExpression());
                info.setIdField(wbe.getIdField());
                info.setCondition(wbe.getCondition());

                // 提取源变量
                Set<String> vars = extractSourceVars(wbe);
                info.setSourceVars(vars);

                // 放入 src 索引
                srcIndex.computeIfAbsent(wbe.getSrcObjectType(), k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(row.getObjectType(), k -> new LinkedHashSet<>())
                        .add(info);

                // 放入 target 字段变量索引
                targetFieldVarsIndex.computeIfAbsent(row.getObjectType(), k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(row.getName(), k -> new LinkedHashSet<>())
                        .addAll(vars);

            } catch (Exception e) {
                log.warn("Failed to parse writeBackExpr for {}.{}: {}", row.getObjectType(), row.getName(), e.getMessage());
            }
        }

        // 第二步：检测级联回写
        buildCascadeIndex();

        long elapsed = System.currentTimeMillis() - t0;
        log.info("Built writeback relation index: {} src entries in {}ms", srcIndex.size(), elapsed);
    }

    /**
     * 从 writeBackExpr 的 expression 和 condition 中提取源对象变量字段。
     */
    private Set<String> extractSourceVars(WriteBackExpr wbe) {
        Set<String> vars = new LinkedHashSet<>();
        // 从 expression 中提取
        if (wbe.getExpression() != null && !wbe.getExpression().isEmpty()) {
            Map<String, Set<String>> exprVars = ExprUtils.extractVariablesFromExpression(wbe.getExpression());
            for (Set<String> fieldSet : exprVars.values()) {
                vars.addAll(fieldSet);
            }
        }
        // 从 condition 中提取
        if (wbe.getCondition() != null && !wbe.getCondition().isEmpty()) {
            Map<String, Set<String>> condVars = ExprUtils.extractVariablesFromExpression(wbe.getCondition());
            for (Set<String> fieldSet : condVars.values()) {
                vars.addAll(fieldSet);
            }
        }
        // idField 本身也是源变量
        if (wbe.getIdField() != null && !wbe.getIdField().isEmpty()) {
            String camelId = wbe.getIdField().contains("_") ? ExprUtils.snakeToCamel(wbe.getIdField()) : wbe.getIdField();
            if (camelId != null) {
                vars.add(camelId);
            }
        }
        return vars;
    }

    /**
     * 构建级联回写索引：当 A 回写 B.fieldX，而 B.fieldX 本身也被 C 回写时，
     * 记录 A → B.fieldX → C.fieldY 的级联链路。
     */
    private void buildCascadeIndex() {
        // 收集所有被回写的 targetObject.targetField 集合
        Map<String, Set<String>> targetFields = new HashMap<>();
        for (Map.Entry<String, Map<String, Set<WriteBackRelationInfo>>> srcEntry : srcIndex.entrySet()) {
            for (Map.Entry<String, Set<WriteBackRelationInfo>> tgtEntry : srcEntry.getValue().entrySet()) {
                for (WriteBackRelationInfo info : tgtEntry.getValue()) {
                    targetFields.computeIfAbsent(info.getTargetObjectType(), k -> new HashSet<>())
                            .add(info.getTargetFieldName());
                }
            }
        }

        // 对每个源对象的回写目标字段，检查该字段是否本身也是某个源对象的回写目标
        for (Map.Entry<String, Map<String, Set<WriteBackRelationInfo>>> srcEntry : srcIndex.entrySet()) {
            String srcObj = srcEntry.getKey();
            for (Map.Entry<String, Set<WriteBackRelationInfo>> tgtEntry : srcEntry.getValue().entrySet()) {
                String midObj = tgtEntry.getKey();
                for (WriteBackRelationInfo info : tgtEntry.getValue()) {
                    String midField = info.getTargetFieldName();
                    // 检查 midObj 是否也是某个源对象——即 midObj 也在 srcIndex 中
                    Map<String, Set<WriteBackRelationInfo>> midTargets = srcIndex.get(midObj);
                    if (midTargets == null) {
                        continue;
                    }
                    // midObj 作为源对象回写了哪些目标
                    for (Map.Entry<String, Set<WriteBackRelationInfo>> cascadeEntry : midTargets.entrySet()) {
                        String cascadeObj = cascadeEntry.getKey();
                        for (WriteBackRelationInfo cascadeInfo : cascadeEntry.getValue()) {
                            // 只有当级联触发的源变量包含了被回写的字段名时，才构成级联
                            if (cascadeInfo.getSourceVars() != null && cascadeInfo.getSourceVars().contains(midField)) {
                                CascadeWriteBackInfo cascade = new CascadeWriteBackInfo(
                                        srcObj, midObj, midField,
                                        cascadeObj, cascadeInfo.getTargetFieldName());
                                cascadeIndex.computeIfAbsent(srcObj, k -> new ArrayList<>()).add(cascade);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 查询源对象触发的所有回写关系。
     *
     * @param srcObjectType 源对象类型名
     * @return targetObject → Set&lt;WriteBackRelationInfo&gt;；未找到返回空 Map
     */
    public Map<String, Set<WriteBackRelationInfo>> getWriteBackExprFields(String srcObjectType) {
        Map<String, Set<WriteBackRelationInfo>> result = srcIndex.get(srcObjectType);
        return result != null ? Collections.unmodifiableMap(result) : Collections.emptyMap();
    }

    /**
     * 查询目标对象每个被回写字段涉及的源变量。
     *
     * @param targetObjectType 目标对象类型名
     * @return targetFieldName → Set&lt;sourceVars&gt;；未找到返回空 Map
     */
    public Map<String, Set<String>> getWriteBackFieldVars(String targetObjectType) {
        Map<String, Set<String>> result = targetFieldVarsIndex.get(targetObjectType);
        return result != null ? Collections.unmodifiableMap(result) : Collections.emptyMap();
    }

    /**
     * 查询源对象的级联回写链路。
     *
     * @param srcObjectType 源对象类型名
     * @return 级联回写信息列表；未找到返回空 List
     */
    public List<CascadeWriteBackInfo> getCascadeWriteBackInfo(String srcObjectType) {
        List<CascadeWriteBackInfo> result = cascadeIndex.get(srcObjectType);
        return result != null ? Collections.unmodifiableList(result) : Collections.emptyList();
    }
}
