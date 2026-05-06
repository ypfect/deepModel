package com.deepmodel.relation.service;

import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.ExpressionFieldInfo;
import com.deepmodel.relation.util.ExprUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表达式字段依赖层级服务。
 * <p>
 * 构建对象内表达式字段的变量依赖 → 反向映射 → 层级排序，
 * 输出 {@link ExpressionFieldInfo} 视图。
 * <p>
 * 参考 platform: SmartLoader.buildEntityExpressionFields / buildEntityFieldToExprFields / calcLevelToExprFields
 */
@Service
public class ExpressionFieldService {

    private static final Logger log = LoggerFactory.getLogger(ExpressionFieldService.class);

    /** objectType → ExpressionFieldInfo */
    private final Map<String, ExpressionFieldInfo> index = new ConcurrentHashMap<>();

    /** 主表→子表列表映射（从 ImpactAnalyzerService 获取） */
    private Map<String, Set<String>> mainToDetails = Collections.emptyMap();

    /**
     * 构建全量表达式字段依赖索引。
     *
     * @param allRows        所有字段记录
     * @param mainToDetails  主表→子表列表映射
     */
    public void buildIndex(List<BaseappObjectField> allRows, Map<String, Set<String>> mainToDetails) {
        long t0 = System.currentTimeMillis();
        this.mainToDetails = mainToDetails != null ? mainToDetails : Collections.emptyMap();
        index.clear();

        // 按对象分组
        Map<String, List<BaseappObjectField>> byObj = new LinkedHashMap<>();
        for (BaseappObjectField row : allRows) {
            byObj.computeIfAbsent(row.getObjectType(), k -> new ArrayList<>()).add(row);
        }

        // 预先收集所有子表名称（避免遍历顺序导致子表被独立处理）
        Set<String> allDetailEntities = new HashSet<>();
        for (Set<String> details : this.mainToDetails.values()) {
            allDetailEntities.addAll(details);
        }

        // 对每个"主表"对象构建 ExpressionFieldInfo（含子表字段合并）
        for (Map.Entry<String, List<BaseappObjectField>> entry : byObj.entrySet()) {
            String objectType = entry.getKey();
            // 跳过子表对象（会被合并到主表中）
            if (allDetailEntities.contains(objectType)) {
                continue;
            }

            // 收集主表 + 所有子表的字段
            List<BaseappObjectField> allFields = new ArrayList<>(entry.getValue());
            Set<String> detailEntities = this.mainToDetails.get(objectType);
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
                index.put(objectType, info);
            }
        }

        long elapsed = System.currentTimeMillis() - t0;
        log.info("Built expression field index: {} entities in {}ms", index.size(), elapsed);
    }

    /**
     * 为单个对象构建表达式字段依赖视图。
     */
    private ExpressionFieldInfo buildForEntity(String mainObjectType, List<BaseappObjectField> fields) {
        // Step 1: buildExpressionFields - 提取有 expression 的字段及其变量依赖
        // key: "ObjectType.fieldName" → Set<"ObjectType.varField">
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
                        // 主表字段：用当前字段所属对象限定
                        qualifiedVar = f.getObjectType() + "." + varField;
                    } else {
                        // 子表字段：prefix 是 LIST 字段名，需要找到对应的子表对象
                        // 在字段列表中查找 prefix 对应的子表对象
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

        // Step 2: buildFieldToExprFields - 反转映射
        Map<String, Set<String>> fieldToExprFields = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : exprFieldToVars.entrySet()) {
            String exprField = entry.getKey();
            for (String varField : entry.getValue()) {
                fieldToExprFields.computeIfAbsent(varField, k -> new LinkedHashSet<>()).add(exprField);
            }
        }

        // Step 3: buildLevelToExprFields - 拓扑排序计算层级
        Map<Integer, Set<String>> levelToFields = calcLevelToExprFields(exprFieldToVars, fieldToExprFields);

        ExpressionFieldInfo info = new ExpressionFieldInfo();
        info.setObjectType(mainObjectType);
        info.setExprFieldToVars(exprFieldToVars);
        info.setNoVarExprFields(noVarExprFields);
        info.setFieldToExprFields(fieldToExprFields);
        info.setLevelToFields(levelToFields);
        return info;
    }

    /**
     * 根据 LIST 字段名找到对应的子表对象类型。
     */
    private String findDetailObjectForListField(String listFieldName, List<BaseappObjectField> fields) {
        for (BaseappObjectField f : fields) {
            if (f.getName().equals(listFieldName) && "LIST".equalsIgnoreCase(f.getType())) {
                // sourceInfo 中包含子表对象名
                if (f.getSourceInfo() != null && f.getSourceInfo().contains("sourceEntityName")) {
                    // 简单解析 sourceEntityName
                    int idx = f.getSourceInfo().indexOf("sourceEntityName");
                    if (idx >= 0) {
                        String after = f.getSourceInfo().substring(idx);
                        // 找引号中的值
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

    /**
     * 拓扑排序计算层级。
     * <p>
     * 参考 platform calcLevelToExprFields:
     * <ul>
     *   <li>level -1: 纯变量字段（不是表达式字段）</li>
     *   <li>level 0: 只依赖纯变量的表达式字段</li>
     *   <li>level N: 依赖 level N-1 表达式字段的表达式字段</li>
     * </ul>
     */
    private Map<Integer, Set<String>> calcLevelToExprFields(
            Map<String, Set<String>> exprFieldToVars,
            Map<String, Set<String>> fieldToExprFields) {

        Map<Integer, Set<String>> result = new TreeMap<>();

        // 所有表达式字段
        Set<String> allExprFields = new HashSet<>(exprFieldToVars.keySet());
        // 所有变量字段（出现在依赖中但本身不是表达式字段的）
        Set<String> pureVarFields = new LinkedHashSet<>();
        for (Set<String> vars : exprFieldToVars.values()) {
            for (String v : vars) {
                if (!allExprFields.contains(v)) {
                    pureVarFields.add(v);
                }
            }
        }

        // level -1: 纯变量
        if (!pureVarFields.isEmpty()) {
            result.put(-1, pureVarFields);
        }

        // 逐层计算
        Set<String> resolved = new HashSet<>(pureVarFields);
        Set<String> remaining = new LinkedHashSet<>(allExprFields);
        int level = 0;
        int maxIterations = allExprFields.size() + 1; // 防止无限循环（循环依赖）

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
                // 剩余的都有循环依赖，放到最高层级
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

    /**
     * 查询指定对象的表达式字段依赖视图。
     *
     * @param objectType 对象类型名（主表名）
     * @return ExpressionFieldInfo；未找到返回 null
     */
    public ExpressionFieldInfo getExpressionFieldInfo(String objectType) {
        return index.get(objectType);
    }
}
