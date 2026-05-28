package com.deepmodel.relation.service;

import com.deepmodel.relation.env.EnvSnapshot;
import com.deepmodel.relation.env.EnvSnapshotManager;
import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.ExpressionFieldInfo;
import com.deepmodel.relation.util.ExprUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 表达式字段依赖层级服务（stateless，state 全部存于 {@link EnvSnapshot}）。
 * <p>
 * 构建对象内表达式字段的变量依赖 → 反向映射 → 层级排序，
 * 输出 {@link ExpressionFieldInfo} 视图。
 * <p>
 * 参考 platform: SmartLoader.buildEntityExpressionFields / buildEntityFieldToExprFields / calcLevelToExprFields
 */
@Service
public class ExpressionFieldService {

    private static final Logger log = LoggerFactory.getLogger(ExpressionFieldService.class);

    private final EnvSnapshotManager snapshotManager;

    public ExpressionFieldService(EnvSnapshotManager snapshotManager) {
        this.snapshotManager = snapshotManager;
    }

    /**
     * 构建全量表达式字段依赖索引，结果写入指定 {@link EnvSnapshot}。
     *
     * @param snapshot       目标快照
     * @param allRows        所有字段记录
     * @param mainToDetails  主表→子表列表映射
     */
    public void buildIndex(EnvSnapshot snapshot, List<BaseappObjectField> allRows,
                           Map<String, Set<String>> mainToDetails) {
        long t0 = System.currentTimeMillis();
        snapshot.exprFieldIndex.clear();
        Map<String, Set<String>> safeMainToDetails = mainToDetails != null ? mainToDetails : Collections.emptyMap();

        Map<String, List<BaseappObjectField>> byObj = new LinkedHashMap<>();
        for (BaseappObjectField row : allRows) {
            byObj.computeIfAbsent(row.getObjectType(), k -> new ArrayList<>()).add(row);
        }

        Set<String> allDetailEntities = new HashSet<>();
        for (Set<String> details : safeMainToDetails.values()) {
            allDetailEntities.addAll(details);
        }

        for (Map.Entry<String, List<BaseappObjectField>> entry : byObj.entrySet()) {
            String objectType = entry.getKey();
            if (allDetailEntities.contains(objectType)) {
                continue;
            }

            List<BaseappObjectField> allFields = new ArrayList<>(entry.getValue());
            Set<String> detailEntities = safeMainToDetails.get(objectType);
            if (detailEntities != null) {
                for (String detailEntity : detailEntities) {
                    List<BaseappObjectField> detailFields = byObj.get(detailEntity);
                    if (detailFields != null) {
                        allFields.addAll(detailFields);
                    }
                }
            }

            ExpressionFieldInfo info = buildForEntity(objectType, allFields);
            if (info != null && !info.getExprFieldToVars().isEmpty()) {
                snapshot.exprFieldIndex.put(objectType, info);
            }
        }

        long elapsed = System.currentTimeMillis() - t0;
        log.info("Built expression field index for env={}: {} entities in {}ms",
                snapshot.env, snapshot.exprFieldIndex.size(), elapsed);
    }

    private ExpressionFieldInfo buildForEntity(String mainObjectType, List<BaseappObjectField> fields) {
        Map<String, Set<String>> exprFieldToVars = new LinkedHashMap<>();
        Set<String> noVarExprFields = new LinkedHashSet<>();
        Set<String> allFieldNames = new HashSet<>();

        for (BaseappObjectField f : fields) {
            String qualifiedName = f.getObjectType() + "." + f.getName();
            allFieldNames.add(qualifiedName);
        }

        for (BaseappObjectField f : fields) {
            if (f.getExpression() == null || f.getExpression().trim().isEmpty()) {
                continue;
            }
            String qualifiedName = f.getObjectType() + "." + f.getName();
            Map<String, Set<String>> varsMap = ExprUtils.extractVariablesFromExpression(f.getExpression());

            Set<String> qualifiedVars = new LinkedHashSet<>();
            for (Map.Entry<String, Set<String>> ve : varsMap.entrySet()) {
                String prefix = ve.getKey();
                for (String varField : ve.getValue()) {
                    String qualifiedVar;
                    if (ExprUtils.KEY_MAIN.equals(prefix)) {
                        qualifiedVar = f.getObjectType() + "." + varField;
                    } else {
                        String detailObj = findDetailObjectForListField(prefix, fields);
                        if (detailObj != null) {
                            qualifiedVar = detailObj + "." + varField;
                        } else {
                            qualifiedVar = f.getObjectType() + "." + prefix + "." + varField;
                        }
                    }
                    qualifiedVars.add(qualifiedVar);
                }
            }

            if (qualifiedVars.isEmpty()) {
                noVarExprFields.add(qualifiedName);
            } else {
                exprFieldToVars.put(qualifiedName, qualifiedVars);
            }
        }

        if (exprFieldToVars.isEmpty() && noVarExprFields.isEmpty()) {
            return null;
        }

        Map<String, Set<String>> fieldToExprFields = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : exprFieldToVars.entrySet()) {
            String exprField = entry.getKey();
            for (String varField : entry.getValue()) {
                fieldToExprFields.computeIfAbsent(varField, k -> new LinkedHashSet<>()).add(exprField);
            }
        }

        Map<Integer, Set<String>> levelToFields = calcLevelToExprFields(exprFieldToVars, fieldToExprFields);

        ExpressionFieldInfo info = new ExpressionFieldInfo();
        info.setObjectType(mainObjectType);
        info.setExprFieldToVars(exprFieldToVars);
        info.setNoVarExprFields(noVarExprFields);
        info.setFieldToExprFields(fieldToExprFields);
        info.setLevelToFields(levelToFields);
        return info;
    }

    private String findDetailObjectForListField(String listFieldName, List<BaseappObjectField> fields) {
        for (BaseappObjectField f : fields) {
            if (f.getName().equals(listFieldName) && "LIST".equalsIgnoreCase(f.getType())) {
                if (f.getSourceInfo() != null && f.getSourceInfo().contains("sourceEntityName")) {
                    int idx = f.getSourceInfo().indexOf("sourceEntityName");
                    if (idx >= 0) {
                        String after = f.getSourceInfo().substring(idx);
                        int firstQuote = after.indexOf('"', after.indexOf(':'));
                        if (firstQuote >= 0) {
                            int secondQuote = after.indexOf('"', firstQuote + 1);
                            if (secondQuote > firstQuote) {
                                return after.substring(firstQuote + 1, secondQuote);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private Map<Integer, Set<String>> calcLevelToExprFields(
            Map<String, Set<String>> exprFieldToVars,
            Map<String, Set<String>> fieldToExprFields) {

        Map<Integer, Set<String>> result = new TreeMap<>();

        Set<String> allExprFields = new HashSet<>(exprFieldToVars.keySet());
        Set<String> pureVarFields = new LinkedHashSet<>();
        for (Set<String> vars : exprFieldToVars.values()) {
            for (String v : vars) {
                if (!allExprFields.contains(v)) {
                    pureVarFields.add(v);
                }
            }
        }

        if (!pureVarFields.isEmpty()) {
            result.put(-1, pureVarFields);
        }

        Set<String> resolved = new HashSet<>(pureVarFields);
        Set<String> remaining = new LinkedHashSet<>(allExprFields);
        int level = 0;
        int maxIterations = allExprFields.size() + 1;

        while (!remaining.isEmpty() && level < maxIterations) {
            Set<String> currentLevel = new LinkedHashSet<>();
            Iterator<String> it = remaining.iterator();
            while (it.hasNext()) {
                String exprField = it.next();
                Set<String> deps = exprFieldToVars.get(exprField);
                if (deps == null || resolved.containsAll(deps)) {
                    currentLevel.add(exprField);
                    it.remove();
                }
            }

            if (currentLevel.isEmpty()) {
                log.warn("Circular dependency detected, placing {} fields at level {}", remaining.size(), level);
                result.put(level, remaining);
                break;
            }

            result.put(level, currentLevel);
            resolved.addAll(currentLevel);
            level++;
        }

        return result;
    }

    /** 查询指定对象的表达式字段依赖视图。 */
    public ExpressionFieldInfo getExpressionFieldInfo(String objectType) {
        return snapshotManager.current().exprFieldIndex.get(objectType);
    }
}
