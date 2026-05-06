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
 */
@Service
public class WriteBackSqlGenerator {

    private static final Logger log = LoggerFactory.getLogger(WriteBackSqlGenerator.class);

    private final ImpactAnalyzerService impactAnalyzerService;
    private final BaseappObjectFieldMapper mapper;

    /** objectType → appName 缓存 */
    private final Map<String, String> appNameCache = new ConcurrentHashMap<String, String>();

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

        // 确定关联条件字段：idField 或默认推导
        String joinCondition = buildJoinCondition(wb, objectType);

        // 构建 WHERE 条件
        String whereCondition = buildWhereCondition(wb);

        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(targetTable).append(" m\n");
        sql.append("SET ").append(targetColumn).append(" = (\n");
        sql.append("  SELECT ").append(expression).append("\n");
        sql.append("  FROM ").append(srcTable).append("\n");
        sql.append("  WHERE ").append(joinCondition);
        sql.append(" AND is_deleted = false");
        if (whereCondition != null && !whereCondition.isEmpty()) {
            sql.append(" AND ").append(whereCondition);
        }
        sql.append("\n);");

        String result = sql.toString();
        log.info("[WriteBackSqlGen] object={}, field={}, srcObject={}, expression={}, condition={}, idField={}",
                objectType, field, wb.getSrcObjectType(), wb.getExpression(),
                wb.getCondition(), wb.getIdField());

        return result;
    }

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
