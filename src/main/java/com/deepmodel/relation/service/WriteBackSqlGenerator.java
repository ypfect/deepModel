package com.deepmodel.relation.service;

import com.deepmodel.relation.dao.BaseappObjectFieldMapper;
import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.WriteBackExpr;
import com.deepmodel.relation.util.ExprUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地回写 SQL 生成器。
 * <p>
 * 参考 platform WriteBackWorker 的 EQL 模板，在本地生成等效的 PostgreSQL UPDATE 语句，
 * 替代通过 HTTP 调用外部服务获取回写 SQL 的方式。
 * <p>
 * 核心模板：
 * <pre>
 *   UPDATE {targetTable} m
 *   SET {column} = (SELECT {expression} FROM {srcTable} WHERE {idField} = m.id AND is_deleted = false {condition});
 * </pre>
 * <p>
 * 对齐线上 GenSqlByModelFiledController.writebackField2sql 的完整逻辑：
 * <ol>
 *   <li>COALESCE 防空值包装（根据目标字段类型自动添加）</li>
 *   <li>ExecutingMoment → billStatus WHERE 条件追加</li>
 *   <li>变更单 isChangeBill 过滤条件追加</li>
 * </ol>
 */
@Service
public class WriteBackSqlGenerator {

    private static final Logger log = LoggerFactory.getLogger(WriteBackSqlGenerator.class);

    private final ImpactAnalyzerService impactAnalyzerService;
    private final BaseappObjectFieldMapper mapper;

    /** objectType → appName 缓存 */
    private final Map<String, String> appNameCache = new ConcurrentHashMap<String, String>();

    /**
     * ExecutingMoment 到 billFullStatus IN 条件的静态映射。
     * 对齐 platform WriteBackExecutingMoment.getAppendCriteria() 的输出。
     */
    private static final Map<String, String> MOMENT_CRITERIA = new LinkedHashMap<>();
    static {
        // onlyAfterSubmit → OR(billFullStatus IN (...), (billFullStatus='BillStatus.excepted' AND billStatus IN (...)))
        MOMENT_CRITERIA.put("onlyAfterSubmit",
                "(billFullStatus in ('BillStatus.submitting','BillStatus.submitted','BillStatus.approving',"
                        + "'BillStatus.effecting','BillStatus.effective','BillStatus.finishing','BillStatus.finished',"
                        + "'BillStatus.closing','BillStatus.opening','BillStatus.closed') "
                        + "or (billFullStatus = 'BillStatus.excepted' "
                        + "and billStatus in ('BillStatus.submitted','BillStatus.approving')))");
        // onlyAfterEffective → AND(billFullStatus IN (...))
        MOMENT_CRITERIA.put("onlyAfterEffective",
                "billFullStatus in ('BillStatus.effective','BillStatus.finishing','BillStatus.finished',"
                        + "'BillStatus.closing','BillStatus.opening','BillStatus.closed')");
        // always / onlyAfterSave / onlyCascade → 不追加条件
    }

    public WriteBackSqlGenerator(ImpactAnalyzerService impactAnalyzerService,
                                 BaseappObjectFieldMapper mapper) {
        this.impactAnalyzerService = impactAnalyzerService;
        this.mapper = mapper;
    }

    /**
     * 为指定的回写字段生成 PostgreSQL UPDATE SQL。
     *
     * @param objectType 目标对象类型（被回写的对象）
     * @param field      目标字段名（驼峰格式）
     * @param wb         解析后的 WriteBackExpr
     * @return 生成的 UPDATE SQL；无法生成时返回 null
     */
    public String generateSql(String objectType, String field, WriteBackExpr wb) {
        if (wb == null || wb.getSrcObjectType() == null || wb.getExpression() == null) {
            log.warn("[WriteBackSqlGen] 回写表达式不完整, object={}, field={}", objectType, field);
            return null;
        }

        String targetTable = objectTypeToTableName(objectType);
        String targetColumn = fieldCamelToColumnName(field, objectType);
        String srcTable = objectTypeToTableName(wb.getSrcObjectType());
        String expression = convertFormulaToSnakeCase(wb.getExpression(), wb.getSrcObjectType());

        // 1. COALESCE 防空值包装
        BaseappObjectField targetFieldDef = impactAnalyzerService.getFieldInfo(objectType, field);
        expression = wrapCoalesce(expression, targetFieldDef);

        // 确定关联条件字段：idField 或默认推导
        String joinCondition = buildJoinCondition(wb, objectType);

        // 构建 WHERE 条件（从 writeBackExpr.condition）
        String condition = buildWhereCondition(wb);

        // 2. ExecutingMoment → billStatus 条件追加
        String momentCondition = buildMomentCondition(wb, wb.getSrcObjectType());
        condition = mergeConditions(momentCondition, condition);

        // 3. 变更单 isChangeBill 过滤
        condition = appendChangeBillCondition(wb.getSrcObjectType(), condition);

        // 包装 condition
        String conditionClause = "";
        if (condition != null && !condition.isEmpty()) {
            conditionClause = " and (" + condition + ")";
        }

        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(targetTable).append(" m\n");
        sql.append("SET ").append(targetColumn).append(" = (\n");
        sql.append("  SELECT ").append(expression).append("\n");
        sql.append("  FROM ").append(srcTable).append("\n");
        sql.append("  WHERE ").append(joinCondition);
        sql.append(" AND is_deleted = false");
        sql.append(conditionClause);
        sql.append("\n);");

        String result = sql.toString();
        log.info("[WriteBackSqlGen] object={}, field={}, srcObject={}, expression={}, condition={}, idField={}, moment={}",
                objectType, field, wb.getSrcObjectType(), wb.getExpression(),
                wb.getCondition(), wb.getIdField(), wb.getExecutingMoment());

        return result;
    }

    // ─── COALESCE 防空值包装 ───

    /**
     * 根据目标字段类型对表达式添加 COALESCE 包装，防止更新为 null 值。
     * 对齐线上 GenSqlByModelFiledController.getHandledWriteBackExpr()。
     */
    private String wrapCoalesce(String expression, BaseappObjectField fieldDef) {
        if (expression == null) return expression;
        String upper = expression.toUpperCase().replace(" ", "");
        // 已有 COALESCE 或 COUNT，不再添加
        if (upper.startsWith("COALESCE(") || upper.startsWith("COUNT(")) {
            return expression;
        }

        String fieldType = fieldDef != null ? fieldDef.getType() : null;
        if (fieldType == null || fieldType.isEmpty()) {
            return expression;
        }

        switch (fieldType.toUpperCase()) {
            case "BIGDECIMAL":
            case "INTEGER":
            case "LONG":
                return String.format("COALESCE(%s, 0)", expression);
            case "BOOLEAN":
                return String.format("COALESCE(%s, false)", expression);
            case "STRING":
                return String.format("COALESCE(%s, '')", expression);
            default:
                return expression;
        }
    }

    // ─── ExecutingMoment 条件 ───

    /**
     * 根据 executingMoment 生成 billStatus/billFullStatus 的过滤条件。
     * 对齐线上 GenSqlByModelFiledController.appendMomentCriteria()。
     * <p>
     * 当源对象是子表时，需要通过 rootBillFieldPath 路径引用主表的 billFullStatus。
     */
    private String buildMomentCondition(WriteBackExpr wb, String srcObjectType) {
        String moment = wb.getExecutingMoment();
        if (moment == null || moment.trim().isEmpty() || "always".equals(moment) || "onlyAfterSave".equals(moment) || "onlyCascade".equals(moment)) {
            return null;
        }

        String criteriaTemplate = MOMENT_CRITERIA.get(moment);
        if (criteriaTemplate == null) {
            return null;
        }

        // 查找源对象到主表的路径
        String rootBillFieldPath = getRootBillFieldPath(srcObjectType);
        if (rootBillFieldPath != null && !rootBillFieldPath.isEmpty()) {
            // 子表场景：将 billFullStatus/billStatus 替换为 rootBillFieldPath.billFullStatus
            criteriaTemplate = criteriaTemplate.replace("billFullStatus", rootBillFieldPath + ".billFullStatus");
            criteriaTemplate = criteriaTemplate.replace("billStatus", rootBillFieldPath + ".billStatus");
        }

        // 转换驼峰字段名为下划线
        return convertFormulaToSnakeCase(criteriaTemplate, srcObjectType);
    }

    /**
     * 递归查找源对象到主表的 FK 字段路径。
     * 对齐线上 GenSqlByModelFiledController.getRootBillFieldPath()。
     */
    private String getRootBillFieldPath(String srcObjectType) {
        try {
            Map<String, String> detailToMainMap = impactAnalyzerService.getDetailToMain();
            String mainEntity = detailToMainMap.get(srcObjectType);
            if (mainEntity == null) {
                // 源对象不是子表
                return "";
            }

            // 找到指向主表的 FK 字段名（约定：主表名首字母小写 + "Id"）
            String fkFieldName = Character.toLowerCase(mainEntity.charAt(0)) + mainEntity.substring(1) + "Id";

            // 如果主表本身也是子表，递归查找
            String parentPath = getRootBillFieldPath(mainEntity);
            if (parentPath != null && !parentPath.isEmpty()) {
                return fkFieldName + "." + parentPath;
            }

            return fkFieldName;
        } catch (Exception e) {
            log.warn("Failed to get rootBillFieldPath for entity: {}", srcObjectType, e);
            return "";
        }
    }

    // ─── 变更单 isChangeBill 过滤 ───

    /**
     * 如果源对象支持变更单，追加 isChangeBill=false 条件。
     * 对齐线上 GenSqlByModelFiledController.appendCondition()。
     */
    private String appendChangeBillCondition(String srcObjectType, String condition) {
        try {
            if (impactAnalyzerService.isSupportChangeBill(srcObjectType)) {
                if (condition != null && !condition.isEmpty()) {
                    if (condition.contains("isChangeBill")) {
                        // condition 已含 isChangeBill，追加 any('{t,f}') 兼容
                        return String.format("(%s) and is_change_bill = any('{t,f}')", condition);
                    } else {
                        return String.format("(%s) and is_change_bill = false", condition);
                    }
                } else {
                    return "is_change_bill = false";
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check change bill support for entity: {}", srcObjectType, e);
        }
        return condition;
    }

    // ─── 条件合并工具 ───

    private String mergeConditions(String first, String second) {
        boolean hasFirst = first != null && !first.isEmpty();
        boolean hasSecond = second != null && !second.isEmpty();
        if (hasFirst && hasSecond) {
            return String.format("(%s) and (%s)", first, second);
        }
        if (hasFirst) return first;
        if (hasSecond) return second;
        return null;
    }

    // ─── 原有方法（未改动） ───

    /**
     * 构建 JOIN 条件（源表如何关联到目标表）。
     * <p>
     * 优先使用 writeBackExpr 中的 idField，否则按对象名推导（如 ArContract → contractId）。
     */
    private String buildJoinCondition(WriteBackExpr wb, String targetObjectType) {
        String idField = wb.getIdField();
        if (idField != null && !idField.trim().isEmpty()) {
            // idField 可能是级联路径如 "contractId.id"，只取第一段作为外键列名
            String fk = idField.contains(".") ? idField.substring(0, idField.indexOf('.')) : idField;
            String fkColumn = fieldCamelToColumnName(fk, wb.getSrcObjectType());
            return fkColumn + " = m.id";
        }
        // 默认推导：目标对象名首字母小写 + "Id" → snake_case
        String defaultFk = Character.toLowerCase(targetObjectType.charAt(0))
                + targetObjectType.substring(1) + "Id";
        String defaultFkColumn = ExprUtils.camelToSnake(defaultFk);
        return defaultFkColumn + " = m.id";
    }

    /**
     * 构建 WHERE 过滤条件（从 writeBackExpr.condition 转换为 SQL）。
     */
    private String buildWhereCondition(WriteBackExpr wb) {
        String condition = wb.getCondition();
        if (condition == null || condition.trim().isEmpty()) {
            return null;
        }
        // 将 condition 中的驼峰字段名转为下划线
        return convertFormulaToSnakeCase(condition, wb.getSrcObjectType());
    }

    // ─── 工具方法（与 UpgradeScriptService 中的逻辑一致）───

    private String objectTypeToTableName(String objectType) {
        if (objectType == null || objectType.trim().isEmpty()) return "";
        String snake = ExprUtils.camelToSnake(objectType);
        String appName = appNameCache.computeIfAbsent(objectType, k -> {
            String name = mapper.selectAppNameByObjectType(k);
            return name != null ? name.trim() : "";
        });
        if (!appName.isEmpty()) {
            return appName + "_" + snake;
        }
        return snake;
    }

    private String fieldCamelToColumnName(String fieldCamel, String objectType) {
        BaseappObjectField def = impactAnalyzerService.getFieldInfo(objectType, fieldCamel);
        if (def != null && def.getName() != null && !def.getName().trim().isEmpty()) {
            String name = def.getName().trim();
            if (name.contains("_")) return name;
            return ExprUtils.camelToSnake(name);
        }
        return ExprUtils.camelToSnake(fieldCamel);
    }

    private String convertFormulaToSnakeCase(String formula, String objectType) {
        if (formula == null || formula.trim().isEmpty()) return formula;

        Set<String> camelFields = ExprUtils.extractCamelFieldsFromSql(formula);
        Map<String, String> fieldMap = new HashMap<String, String>();
        for (String camelField : camelFields) {
            BaseappObjectField def = impactAnalyzerService.getFieldInfo(objectType, camelField);
            if (def == null) {
                fieldMap.put(camelField, ExprUtils.camelToSnake(camelField));
                continue;
            }
            if (def.getName() != null && !def.getName().trim().isEmpty()) {
                fieldMap.put(camelField, def.getName().trim());
            } else {
                fieldMap.put(camelField, ExprUtils.camelToSnake(camelField));
            }
        }

        String result = formula;
        List<Map.Entry<String, String>> sorted = new ArrayList<Map.Entry<String, String>>(fieldMap.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<String, String>>() {
            @Override
            public int compare(Map.Entry<String, String> a, Map.Entry<String, String> b) {
                return Integer.compare(b.getKey().length(), a.getKey().length());
            }
        });

        for (Map.Entry<String, String> e : sorted) {
            String camel = e.getKey();
            String snake = e.getValue();
            String pattern = "(?<!\\.)\\b" + java.util.regex.Pattern.quote(camel) + "\\b";
            result = result.replaceAll(pattern, snake);
        }
        return result;
    }
}
