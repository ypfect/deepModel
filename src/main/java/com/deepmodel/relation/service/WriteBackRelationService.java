package com.deepmodel.relation.service;

import com.deepmodel.relation.env.EnvSnapshot;
import com.deepmodel.relation.env.EnvSnapshotManager;
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
 * 回写触发关系图服务（stateless，state 全部存于 {@link EnvSnapshot}）。
 *
 * 索引语义：
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

    private final EnvSnapshotManager snapshotManager;

    public WriteBackRelationService(EnvSnapshotManager snapshotManager) {
        this.snapshotManager = snapshotManager;
    }

    /**
     * 从字段列表构建回写触发索引，结果写入指定 {@link EnvSnapshot}。
     */
    public void buildIndex(EnvSnapshot snapshot, List<BaseappObjectField> allRows) {
        long t0 = System.currentTimeMillis();
        snapshot.wbSrcIndex.clear();
        snapshot.wbTargetFieldVarsIndex.clear();
        snapshot.wbCascadeIndex.clear();

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

                Set<String> vars = extractSourceVars(wbe);
                info.setSourceVars(vars);

                snapshot.wbSrcIndex.computeIfAbsent(wbe.getSrcObjectType(), k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(row.getObjectType(), k -> new LinkedHashSet<>())
                        .add(info);

                snapshot.wbTargetFieldVarsIndex.computeIfAbsent(row.getObjectType(), k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(row.getName(), k -> new LinkedHashSet<>())
                        .addAll(vars);

            } catch (Exception e) {
                log.warn("Failed to parse writeBackExpr for {}.{}: {}", row.getObjectType(), row.getName(), e.getMessage());
            }
        }

        buildCascadeIndex(snapshot);

        long elapsed = System.currentTimeMillis() - t0;
        log.info("Built writeback relation index for env={}: {} src entries in {}ms",
                snapshot.env, snapshot.wbSrcIndex.size(), elapsed);
    }

    private Set<String> extractSourceVars(WriteBackExpr wbe) {
        Set<String> vars = new LinkedHashSet<>();
        if (wbe.getExpression() != null && !wbe.getExpression().isEmpty()) {
            Map<String, Set<String>> exprVars = ExprUtils.extractVariablesFromExpression(wbe.getExpression());
            for (Set<String> fieldSet : exprVars.values()) {
                vars.addAll(fieldSet);
            }
        }
        if (wbe.getCondition() != null && !wbe.getCondition().isEmpty()) {
            Map<String, Set<String>> condVars = ExprUtils.extractVariablesFromExpression(wbe.getCondition());
            for (Set<String> fieldSet : condVars.values()) {
                vars.addAll(fieldSet);
            }
        }
        if (wbe.getIdField() != null && !wbe.getIdField().isEmpty()) {
            String camelId = wbe.getIdField().contains("_") ? ExprUtils.snakeToCamel(wbe.getIdField()) : wbe.getIdField();
            if (camelId != null) {
                vars.add(camelId);
            }
        }
        return vars;
    }

    private void buildCascadeIndex(EnvSnapshot snapshot) {
        Map<String, Set<String>> targetFields = new HashMap<>();
        for (Map.Entry<String, Map<String, Set<WriteBackRelationInfo>>> srcEntry : snapshot.wbSrcIndex.entrySet()) {
            for (Map.Entry<String, Set<WriteBackRelationInfo>> tgtEntry : srcEntry.getValue().entrySet()) {
                for (WriteBackRelationInfo info : tgtEntry.getValue()) {
                    targetFields.computeIfAbsent(info.getTargetObjectType(), k -> new HashSet<>())
                            .add(info.getTargetFieldName());
                }
            }
        }

        for (Map.Entry<String, Map<String, Set<WriteBackRelationInfo>>> srcEntry : snapshot.wbSrcIndex.entrySet()) {
            String srcObj = srcEntry.getKey();
            for (Map.Entry<String, Set<WriteBackRelationInfo>> tgtEntry : srcEntry.getValue().entrySet()) {
                String midObj = tgtEntry.getKey();
                for (WriteBackRelationInfo info : tgtEntry.getValue()) {
                    String midField = info.getTargetFieldName();
                    Map<String, Set<WriteBackRelationInfo>> midTargets = snapshot.wbSrcIndex.get(midObj);
                    if (midTargets == null) {
                        continue;
                    }
                    for (Map.Entry<String, Set<WriteBackRelationInfo>> cascadeEntry : midTargets.entrySet()) {
                        String cascadeObj = cascadeEntry.getKey();
                        for (WriteBackRelationInfo cascadeInfo : cascadeEntry.getValue()) {
                            if (cascadeInfo.getSourceVars() != null && cascadeInfo.getSourceVars().contains(midField)) {
                                CascadeWriteBackInfo cascade = new CascadeWriteBackInfo(
                                        srcObj, midObj, midField,
                                        cascadeObj, cascadeInfo.getTargetFieldName());
                                snapshot.wbCascadeIndex.computeIfAbsent(srcObj, k -> new ArrayList<>()).add(cascade);
                            }
                        }
                    }
                }
            }
        }
    }

    /** 查询源对象触发的所有回写关系。 */
    public Map<String, Set<WriteBackRelationInfo>> getWriteBackExprFields(String srcObjectType) {
        EnvSnapshot snap = snapshotManager.current();
        Map<String, Set<WriteBackRelationInfo>> result = snap.wbSrcIndex.get(srcObjectType);
        return result != null ? Collections.unmodifiableMap(result) : Collections.emptyMap();
    }

    /** 查询目标对象每个被回写字段涉及的源变量。 */
    public Map<String, Set<String>> getWriteBackFieldVars(String targetObjectType) {
        EnvSnapshot snap = snapshotManager.current();
        Map<String, Set<String>> result = snap.wbTargetFieldVarsIndex.get(targetObjectType);
        return result != null ? Collections.unmodifiableMap(result) : Collections.emptyMap();
    }

    /** 查询源对象的级联回写链路。 */
    public List<CascadeWriteBackInfo> getCascadeWriteBackInfo(String srcObjectType) {
        EnvSnapshot snap = snapshotManager.current();
        List<CascadeWriteBackInfo> result = snap.wbCascadeIndex.get(srcObjectType);
        return result != null ? Collections.unmodifiableList(result) : Collections.emptyList();
    }
}
