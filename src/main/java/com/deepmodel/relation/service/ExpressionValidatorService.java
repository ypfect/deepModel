package com.deepmodel.relation.service;

import com.deepmodel.relation.enums.ErrorCategory;
import com.deepmodel.relation.enums.ExpressionType;
import com.deepmodel.relation.enums.SeverityLevel;
import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.ValidationErrorItem;
import com.deepmodel.relation.model.ValidationReport;
import com.deepmodel.relation.model.WriteBackExpr;
import com.deepmodel.relation.util.ExprUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ExpressionValidatorService {

    private static final Logger log = LoggerFactory.getLogger(ExpressionValidatorService.class);

    /** 合法的 executingMoment 值（不填等同于 always） */
    private static final Set<String> VALID_EXECUTING_MOMENTS = Set.of(
            "always", "ALWAYS",
            "onlyAfterSave", "onlyAfterSubmit",
            "onlyAfterEffective",
            "onlyBeforeSubmit",
            "dataChanged", "statusChanged",
            "onlyCascade"
    );

    /** SQL 聚合函数名集合（大写） */
    private static final Set<String> AGGREGATE_FUNCTIONS = Set.of(
            "SUM", "COUNT", "AVG", "MIN", "MAX", "STRING_AGG", "ARRAY_AGG",
            "BOOL_AND", "BOOL_OR", "EVERY", "JSONB_AGG", "JSON_AGG"
    );

    /**
     * JSQLParser 在某些表达式中会把 SQL 关键字/字面量解析成 Column，
     * 这些标识符不是真实字段，校验时直接跳过。
     */
    private static final Set<String> SQL_SKIP_IDENTIFIERS = Set.of(
            // SQL 关键字/字面量
            "true", "false", "null", "unknown", "current_timestamp", "current_date",
            "current_time", "localtime", "localtimestamp", "now", "infinity",
            // 系统内置属性（不在元数据字段定义中）
            "commentscount", "attachmentscount", "executepath"
    );

    /** MVEL 关键字/内置，不作为字段引用校验 */
    private static final Set<String> MVEL_SKIP_IDENTIFIERS = Set.of(
            "if", "else", "foreach", "for", "while", "do", "return", "import", "def", "new",
            "instanceof", "empty", "size", "contains", "matches", "with", "assert", "var",
            "true", "false", "null", "this", "systemfields"
    );

    private final ImpactAnalyzerService impactAnalyzerService;
    private final FormulaParserService formulaParserService;
    private final ObjectMapper objectMapper;

    public ExpressionValidatorService(ImpactAnalyzerService impactAnalyzerService, FormulaParserService formulaParserService) {
        this.impactAnalyzerService = impactAnalyzerService;
        this.formulaParserService = formulaParserService;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    }

    public ValidationReport checkSingleObject(String objectType) {
        ValidationReport report = new ValidationReport();
        List<BaseappObjectField> allFields = impactAnalyzerService.getAllFields();
        if (allFields == null) {
            allFields = Collections.emptyList();
        }

        Map<String, List<BaseappObjectField>> groupedFields = allFields.stream()
                .filter(f -> f.getObjectType() != null)
                .collect(Collectors.groupingBy(BaseappObjectField::getObjectType));

        List<BaseappObjectField> targetFields = groupedFields.getOrDefault(objectType, Collections.emptyList());
        if (targetFields.isEmpty()) {
            report.addItem(new ValidationErrorItem(objectType, "N/A", null, ErrorCategory.OBJECT_NOT_FOUND, SeverityLevel.ERROR, "Object not found or has no fields: " + objectType));
            report.setScannedObjectCount(0);
            return report;
        }

        report.setScannedObjectCount(1);
        for (BaseappObjectField field : targetFields) {
            validateFieldExpressions(field, groupedFields, report);
        }

        enrichReportFieldDefinitions(report);
        return report;
    }

    // ===== 进度事件数据结构 =====
    public static class ScanProgress {
        public String type;        // "start" | "progress" | "complete"
        public int totalObjects;
        public int totalFields;
        public int scannedObjects;
        public int scannedFields;
        public String currentObject;
        public int totalObjectCountInEnv;
        public String filterAppName;
        public ValidationReport report; // 仅 complete 时非 null

        public ScanProgress(String type, int totalObjects, int totalFields,
                            int scannedObjects, int scannedFields, String currentObject) {
            this.type = type;
            this.totalObjects = totalObjects;
            this.totalFields = totalFields;
            this.scannedObjects = scannedObjects;
            this.scannedFields = scannedFields;
            this.currentObject = currentObject;
        }
    }

    /** 旧接口：同步扫描（内部调用新接口） */
    public ValidationReport checkAllObjectsInApp(String appName) {
        return checkAllObjectsInApp(appName, null);
    }

    /** 新接口：支持进度回调（SSE 流式推送用） */
    public ValidationReport checkAllObjectsInApp(String appName, java.util.function.Consumer<ScanProgress> progressCallback) {
        ValidationReport report = new ValidationReport();
        List<BaseappObjectField> allFields = impactAnalyzerService.getAllFields();
        if (allFields == null) allFields = Collections.emptyList();

        Map<String, List<BaseappObjectField>> groupedFields = allFields.stream()
                .filter(f -> f.getObjectType() != null)
                .collect(Collectors.groupingBy(BaseappObjectField::getObjectType));

        int totalObjectCountInEnv = groupedFields.size();
        String filterAppName = (appName != null && !appName.trim().isEmpty()) ? appName.trim() : null;
        Set<String> appFilters = parseAppNameFilters(filterAppName);

        List<BaseappObjectField> filteredFields = allFields;
        if (!appFilters.isEmpty()) {
            filteredFields = allFields.stream()
                    .filter(f -> matchesAppFilter(f.getAppName(), appFilters))
                    .collect(Collectors.toList());
        }

        // 按对象分组（保持扫描范围内的对象）
        Map<String, List<BaseappObjectField>> objectGroups = filteredFields.stream()
                .filter(f -> f.getObjectType() != null)
                .collect(Collectors.groupingBy(BaseappObjectField::getObjectType, java.util.LinkedHashMap::new, Collectors.toList()));

        int totalObjects = objectGroups.size();
        int totalFields  = filteredFields.size();

        report.setTotalObjectCountInEnv(totalObjectCountInEnv);
        report.setFilterAppName(filterAppName);

        if (progressCallback != null) {
            ScanProgress start = new ScanProgress("start", totalObjects, totalFields, 0, 0, null);
            start.totalObjectCountInEnv = totalObjectCountInEnv;
            start.filterAppName = filterAppName;
            progressCallback.accept(start);
        }

        int scannedObjects = 0;
        int scannedFields  = 0;

        for (Map.Entry<String, List<BaseappObjectField>> entry : objectGroups.entrySet()) {
            String objectType = entry.getKey();
            List<BaseappObjectField> fields = entry.getValue();

            if (progressCallback != null) {
                progressCallback.accept(new ScanProgress("progress", totalObjects, totalFields,
                        scannedObjects, scannedFields, objectType));
            }

            for (BaseappObjectField field : fields) {
                validateFieldExpressions(field, groupedFields, report);
                scannedFields++;
            }
            scannedObjects++;
        }

        report.setScannedObjectCount(scannedObjects);
        report.setIssueObjectCount((int) report.getItems().stream()
                .map(ValidationErrorItem::getObjectType)
                .filter(Objects::nonNull)
                .distinct()
                .count());
        enrichReportFieldDefinitions(report);
        return report;
    }

    /** 为每条诊断项附上字段 JSON 定义（实体元数据片段或库表字段快照）。 */
    private void enrichReportFieldDefinitions(ValidationReport report) {
        if (report.getItems() == null) {
            return;
        }
        for (ValidationErrorItem item : report.getItems()) {
            if (item.getFieldDefinitionJson() != null && !item.getFieldDefinitionJson().isBlank()) {
                continue;
            }
            String def = resolveFieldDefinitionJson(item.getObjectType(), item.getFieldName());
            if (def != null) {
                item.setFieldDefinitionJson(def);
            }
        }
    }

    private String resolveFieldDefinitionJson(String objectType, String fieldName) {
        if (objectType == null || fieldName == null || fieldName.isBlank() || "N/A".equals(fieldName)) {
            return null;
        }
        BaseappObjectField field = impactAnalyzerService.getFieldInfo(objectType, fieldName);
        if (field == null) {
            List<BaseappObjectField> rows = impactAnalyzerService.getFieldsByObject(objectType);
            if (rows != null) {
                for (BaseappObjectField r : rows) {
                    if (fieldName.equals(r.getName()) || fieldName.equals(r.getApiName())) {
                        field = r;
                        break;
                    }
                }
            }
        }
        if (field == null) {
            return null;
        }
        if (field.getMetadataJson() != null && !field.getMetadataJson().isBlank()) {
            return field.getMetadataJson();
        }
        return buildFieldRowJsonFallback(field);
    }

    private String buildFieldRowJsonFallback(BaseappObjectField field) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("objectType", field.getObjectType());
        map.put("name", field.getName());
        if (field.getApiName() != null) map.put("apiName", field.getApiName());
        if (field.getTitle() != null) map.put("title", field.getTitle());
        if (field.getType() != null) map.put("type", field.getType());
        if (field.getBizType() != null) map.put("bizType", field.getBizType());
        if (field.getExpression() != null && !field.getExpression().isBlank()) map.put("expression", field.getExpression());
        if (field.getTriggerExpr() != null && !field.getTriggerExpr().isBlank()) map.put("triggerExpr", field.getTriggerExpr());
        if (field.getVirtualExpr() != null && !field.getVirtualExpr().isBlank()) map.put("virtualExpr", field.getVirtualExpr());
        if (field.getWriteBackExpr() != null && !field.getWriteBackExpr().isBlank()) map.put("writeBackExpr", field.getWriteBackExpr());
        if (field.getReferInfo() != null && !field.getReferInfo().isBlank()) map.put("referInfo", field.getReferInfo());
        if (field.getSourceInfo() != null && !field.getSourceInfo().isBlank()) map.put("sourceInfo", field.getSourceInfo());
        if (field.getEnumType() != null) map.put("enumType", field.getEnumType());
        if (field.getIsDisabled() != null) map.put("isDisabled", field.getIsDisabled());
        if (field.getIsMasterField() != null) map.put("isMasterField", field.getIsMasterField());
        if (field.getIsCustomizedField() != null) map.put("isCustomizedField", field.getIsCustomizedField());
        if (field.getDescription() != null) map.put("description", field.getDescription());
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return map.toString();
        }
    }

    /** 支持逗号分隔的多模块，精确匹配 appName（忽略大小写）。 */
    private static Set<String> parseAppNameFilters(String appName) {
        if (appName == null || appName.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String part : appName.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static boolean matchesAppFilter(String fieldAppName, Set<String> appFilters) {
        if (fieldAppName == null || fieldAppName.isBlank() || appFilters.isEmpty()) {
            return false;
        }
        String lower = fieldAppName.toLowerCase(Locale.ROOT);
        for (String filter : appFilters) {
            if (lower.equals(filter)) {
                return true;
            }
        }
        return false;
    }

    private void validateFieldExpressions(BaseappObjectField field, Map<String, List<BaseappObjectField>> groupedFields, ValidationReport report) {
        // 1. 计算 expression（MVEL，非 SQL）
        if (field.getExpression() != null && !field.getExpression().trim().isEmpty()) {
            validateMvelExpression(field, field.getExpression(), field.getObjectType(), groupedFields, report);
        }

        // 2. triggerExpr
        if (field.getTriggerExpr() != null && !field.getTriggerExpr().trim().isEmpty()) {
            validateSqlExpr(field, field.getTriggerExpr(), ExpressionType.TRIGGER, field.getObjectType(), groupedFields, report);
        }

        // 3. writeBackExpr (重点扩展)
        if (field.getWriteBackExpr() != null && !field.getWriteBackExpr().trim().isEmpty()) {
            try {
                List<WriteBackExpr> writeBacks = objectMapper.readValue(field.getWriteBackExpr(), new TypeReference<List<WriteBackExpr>>() {});
                for (WriteBackExpr wb : writeBacks) {
                    validateWriteBackExpr(field, wb, groupedFields, report);
                }
            } catch (JsonProcessingException e) {
                report.addItem(new ValidationErrorItem(field.getObjectType(), field.getName(), ExpressionType.WRITE_BACK, ErrorCategory.FATAL_PARSE_ERROR, SeverityLevel.FATAL, "Failed to parse writeBackExpr JSON: " + e.getMessage()));
            }
        }

        // 4. 枚举值校验
        validateEnumReferences(field, groupedFields, report);
    }

    // ========== WriteBack 专项校验 ==========

    /**
     * 对单个 WriteBackExpr 进行全面校验
     */
    private void validateWriteBackExpr(BaseappObjectField field, WriteBackExpr wb, Map<String, List<BaseappObjectField>> groupedFields, ValidationReport report) {

        // --- Rule 1: 必填字段检查 ---
        if (wb.getSrcObjectType() == null || wb.getSrcObjectType().trim().isEmpty()) {
            report.addItem(new ValidationErrorItem(field.getObjectType(), field.getName(),
                    ExpressionType.WRITE_BACK, ErrorCategory.MISSING_REQUIRED_FIELD, SeverityLevel.ERROR,
                    "writeBackExpr 缺少必填属性 srcObjectType"));
        }
        if (wb.getIdField() == null || wb.getIdField().trim().isEmpty()) {
            report.addItem(new ValidationErrorItem(field.getObjectType(), field.getName(),
                    ExpressionType.WRITE_BACK, ErrorCategory.MISSING_REQUIRED_FIELD, SeverityLevel.ERROR,
                    "writeBackExpr 缺少必填属性 idField"));
        }
        if (wb.getExpression() == null || wb.getExpression().trim().isEmpty()) {
            report.addItem(new ValidationErrorItem(field.getObjectType(), field.getName(),
                    ExpressionType.WRITE_BACK, ErrorCategory.MISSING_REQUIRED_FIELD, SeverityLevel.ERROR,
                    "writeBackExpr 缺少必填属性 expression"));
        }

        // --- Rule 2: executingMoment 合法枚举值校验（不填=always，视为合法）---
        if (wb.getExecutingMoment() != null && !wb.getExecutingMoment().trim().isEmpty()) {
            if (!VALID_EXECUTING_MOMENTS.contains(wb.getExecutingMoment().trim())) {
                report.addItem(new ValidationErrorItem(field.getObjectType(), field.getName(),
                        ExpressionType.WRITE_BACK, ErrorCategory.INVALID_EXECUTING_MOMENT, SeverityLevel.ERROR,
                        "executingMoment 值 '" + wb.getExecutingMoment() + "' 不在合法范围: " + VALID_EXECUTING_MOMENTS));
            }
        }

        // --- Rule 3: srcObjectType 存在性校验 ---
        String srcCtx = wb.getSrcObjectType() != null ? wb.getSrcObjectType() : field.getObjectType();
        if (wb.getSrcObjectType() != null && !wb.getSrcObjectType().isEmpty()) {
            if (!groupedFields.containsKey(wb.getSrcObjectType())) {
                report.addItem(new ValidationErrorItem(field.getObjectType(), field.getName(),
                        ExpressionType.WRITE_BACK, ErrorCategory.OBJECT_NOT_FOUND, SeverityLevel.ERROR,
                        "writeBackExpr.srcObjectType '" + wb.getSrcObjectType() + "' 在元数据中未找到"));
            }
        }

        // --- Rule 4: idField 字段存在性校验（在源对象中查找） ---
        if (wb.getIdField() != null && !wb.getIdField().isEmpty() && groupedFields.containsKey(srcCtx)) {
            if (wb.getIdField().contains(".")) {
                // 点号路径：A.B → 在 srcObjectType 找 ${A}Id 字段 → 通过 referInfo 找引用对象 → 检查 B 字段
                validateDottedIdField(field, wb, srcCtx, groupedFields, report);
            } else {
                boolean idFieldFound = fieldExistsInObject(wb.getIdField(), srcCtx, groupedFields);
                if (!idFieldFound) {
                    report.addItem(new ValidationErrorItem(field.getObjectType(), field.getName(),
                            ExpressionType.WRITE_BACK, ErrorCategory.FIELD_NOT_FOUND, SeverityLevel.ERROR,
                            "writeBackExpr.idField '" + wb.getIdField() + "' 在源对象 '" + srcCtx + "' 中未找到"));
                }
            }
        }

        // --- Rule 5: expression 回写聚合表达式语法检查 ---
        if (wb.getExpression() != null && !wb.getExpression().trim().isEmpty()) {
            validateSqlExpr(field, wb.getExpression(), ExpressionType.WRITE_BACK, srcCtx, groupedFields, report);
            // 额外检测嵌套聚合
            detectNestedAggregate(field, wb.getExpression(), ExpressionType.WRITE_BACK, report);
        }

        // --- Rule 6: condition 过滤条件检查 ---
        if (wb.getCondition() != null && !wb.getCondition().trim().isEmpty()) {
            validateSqlExpr(field, wb.getCondition(), ExpressionType.WRITE_BACK, srcCtx, groupedFields, report);
        }

        // --- Rule 7: validateExpr 回写校验表达式专项检查 (核心新增功能) ---
        if (wb.getValidateExpr() != null && !wb.getValidateExpr().trim().isEmpty()) {
            validateWriteBackValidateExpr(field, wb, groupedFields, report);
        }

        // --- Rule 8: onlyCascade 时机有效性 —— expression 引用的源字段必须能被级联触发 ---
        //
        // onlyCascade 的触发链有两种合法路径：
        //   路径1（直接）: 下游字段本身有 writeBackExpr → 被回写时直接触发级联
        //   路径2（间接）: 下游字段是 trigger 字段（有 triggerExpr），
        //                   且 triggerExpr 中引用了有 writeBackExpr 的字段
        //                   → writeBack 字段更新 → trigger 字段重算 → 间接触发级联
        //
        // 两条路径都不满足 → Dead Config，报 ERROR
        if ("onlyCascade".equals(wb.getExecutingMoment())
                && wb.getSrcObjectType() != null
                && groupedFields.containsKey(wb.getSrcObjectType())
                && wb.getExpression() != null
                && !wb.getExpression().trim().isEmpty()) {

            List<String> referencedColumns = new ArrayList<>();
            try {
                Expression parsedExpr = CCJSqlParserUtil.parseExpression(wb.getExpression().trim());
                parsedExpr.accept(new ExpressionVisitorAdapter() {
                    @Override
                    public void visit(Column column) {
                        referencedColumns.add(column.getColumnName());
                    }
                });
            } catch (Exception e) {
                // expression 解析失败已在 Rule 5 中报告，此处静默跳过
            }

            List<BaseappObjectField> srcFieldList = groupedFields.get(wb.getSrcObjectType());

            for (String colName : referencedColumns) {
                String normalizedCol = colName.replace("_", "").toLowerCase();
                Optional<BaseappObjectField> srcFieldOpt = srcFieldList.stream()
                        .filter(f -> {
                            String n = f.getName() != null ? f.getName().replace("_", "").toLowerCase() : "";
                            String a = f.getApiName() != null ? f.getApiName().replace("_", "").toLowerCase() : "";
                            return normalizedCol.equals(n) || normalizedCol.equals(a);
                        })
                        .findFirst();

                if (!srcFieldOpt.isPresent()) {
                    // 字段不存在：Rule 4 已报 FIELD_NOT_FOUND，此处跳过
                    continue;
                }

                BaseappObjectField srcField = srcFieldOpt.get();

                // --- 路径1：直接回写字段 ---
                boolean hasWriteBack = srcField.getWriteBackExpr() != null
                        && !srcField.getWriteBackExpr().trim().isEmpty();
                if (hasWriteBack) {
                    continue; // ✅ 合法，有直接级联来源
                }

                // --- 路径2：trigger 字段，且 triggerExpr 中引用了 writeBack 字段 ---
                boolean validViaTrigger = false;
                String triggerExpr = srcField.getTriggerExpr();
                if (triggerExpr != null && !triggerExpr.trim().isEmpty()) {
                    List<String> triggerCols = new ArrayList<>();
                    try {
                        Expression parsedTrigger = CCJSqlParserUtil.parseExpression(triggerExpr.trim());
                        parsedTrigger.accept(new ExpressionVisitorAdapter() {
                            @Override
                            public void visit(Column column) {
                                triggerCols.add(column.getColumnName());
                            }
                        });
                    } catch (Exception e) {
                        // trigger 解析失败，保守处理：不能确认合法，继续后续判断
                    }

                    // 检查 triggerExpr 引用的列中是否有任意一个是回写字段
                    for (String triggerCol : triggerCols) {
                        String normalizedTriggerCol = triggerCol.replace("_", "").toLowerCase();
                        boolean triggerColIsWriteBack = srcFieldList.stream()
                                .filter(f -> {
                                    String n = f.getName() != null ? f.getName().replace("_", "").toLowerCase() : "";
                                    String a = f.getApiName() != null ? f.getApiName().replace("_", "").toLowerCase() : "";
                                    return normalizedTriggerCol.equals(n) || normalizedTriggerCol.equals(a);
                                })
                                .anyMatch(f -> f.getWriteBackExpr() != null && !f.getWriteBackExpr().trim().isEmpty());
                        if (triggerColIsWriteBack) {
                            validViaTrigger = true;
                            break;
                        }
                    }
                }

                if (validViaTrigger) {
                    continue; // ✅ 合法，触发字段被 writeBack 字段驱动，可间接级联
                }

                // --- 两条路径都不满足 → Dead Config ---
                String reason;
                if (triggerExpr != null && !triggerExpr.trim().isEmpty()) {
                    reason = "字段 `" + colName + "` 是 trigger 字段，但其 triggerExpr 中没有引用任何 writeBackExpr 字段，"
                            + "trigger 不会被级联驱动，onlyCascade 仍然永远不会触发。";
                } else {
                    reason = "字段 `" + colName + "` 既没有 writeBackExpr 也没有 triggerExpr，"
                            + "不存在任何级联触发来源。";
                }
                report.addItem(new ValidationErrorItem(
                        field.getObjectType(), field.getName(),
                        ExpressionType.WRITE_BACK, ErrorCategory.INVALID_CASCADE_TARGET, SeverityLevel.ERROR,
                        "executingMoment 为 `onlyCascade`，但级联链条无效（Dead Config）。"
                        + " 源对象 `" + wb.getSrcObjectType() + "` 中：" + reason
                        + " 请确保 `" + colName + "` 本身是回写字段，或其 triggerExpr 引用了回写字段。"));
            }
        }

    }

    /**
     * 对 validateExpr 的专项校验：
     *
     * Q7Link WriteBackWorker 的校验逻辑：回写 SQL 执行后，会用 validateExpr 构造如下 SQL：
     * SELECT string_agg(CASE WHEN {validateExpr} THEN m.id END, ',') AS "fieldName" FROM ... WHERE ...
     *
     * 因此 validateExpr 会被嵌入 string_agg(CASE WHEN ...) 聚合上下文中。
     * 如果 validateExpr 本身包含 sum/count 等聚合函数，就会导致 PostgreSQL 报错：
     * "不允许嵌套调用聚合函数"
     *
     * 合法的 validateExpr 应该引用的是「已回写后」的**目标行级字段**，不需要再 sum。
     */
    private void validateWriteBackValidateExpr(BaseappObjectField field, WriteBackExpr wb, Map<String, List<BaseappObjectField>> groupedFields, ValidationReport report) {
        String validateExpr = wb.getValidateExpr().trim();

        // 7a. 基本 SQL 语法解析
        String cleanExpr = validateExpr.replace("${SystemFields}", " ");
        try {
            Expression parsed = CCJSqlParserUtil.parseExpression(cleanExpr);

            // 7b. 核心规则：检测 validateExpr 中是否包含聚合函数
            // 这是用户示例中的实际错误根源
            List<String> foundAggregates = new ArrayList<>();
            parsed.accept(new ExpressionVisitorAdapter() {
                @Override
                public void visit(Function function) {
                    String funcName = function.getName().toUpperCase();
                    if (AGGREGATE_FUNCTIONS.contains(funcName)) {
                        foundAggregates.add(function.toString());
                    }
                    super.visit(function);
                }
            });

            if (!foundAggregates.isEmpty()) {
                report.addItem(new ValidationErrorItem(field.getObjectType(), field.getName(),
                        ExpressionType.WRITE_BACK_VALIDATE, ErrorCategory.AGGREGATE_IN_VALIDATE_EXPR, SeverityLevel.FATAL,
                        "validateExpr 内含聚合函数 " + foundAggregates +
                                "，运行时会被嵌入 string_agg(CASE WHEN ... ) 导致 PostgreSQL 报错「不允许嵌套调用聚合函数」。" +
                                "validateExpr 应引用回写后的行级字段值，不需要再次聚合。" +
                                " 建议去掉 sum/count 包裹，直接使用字段名。" +
                                " 原始表达式：" + validateExpr));
            }

            // 7c. 字段存在性校验 — validateExpr 的上下文是「目标对象」（拥有 writeBackExpr 的实体）
            parsed.accept(new ExpressionVisitorAdapter() {
                @Override
                public void visit(Column column) {
                    validateColumn(field, column, ExpressionType.WRITE_BACK_VALIDATE, field.getObjectType(), groupedFields, report);
                }

                @Override
                public void visit(Function function) {
                    // 递归检查函数参数中的列引用
                    super.visit(function);
                }
            });

        } catch (Exception e) {
            String shortMsg = e.getMessage() != null ? e.getMessage().split("\n")[0] : "Parse error";
            report.addItem(new ValidationErrorItem(field.getObjectType(), field.getName(),
                    ExpressionType.WRITE_BACK_VALIDATE, ErrorCategory.FATAL_PARSE_ERROR, SeverityLevel.FATAL,
                    "validateExpr 解析失败: " + shortMsg + " | 原始表达式: " + validateExpr));
        }

        // 7d. 有 validateExpr 但缺少 validateMessage 的警告
        if (wb.getValidateMessage() == null || wb.getValidateMessage().trim().isEmpty()) {
            report.addItem(new ValidationErrorItem(field.getObjectType(), field.getName(),
                    ExpressionType.WRITE_BACK_VALIDATE, ErrorCategory.MISSING_REQUIRED_FIELD, SeverityLevel.WARNING,
                    "配置了 validateExpr 但未配置 validateMessage，校验失败时用户将看到默认技术错误提示"));
        }
    }

    // ========== 枚举值校验 ==========

    /** 匹配表达式中单引号包裹的枚举引用: 'EnumName.value' */
    private static final Pattern ENUM_REF_PATTERN = Pattern.compile("'([A-Za-z]\\w*)\\.(\\w+)'");

    /**
     * 扫描字段的所有表达式，检查其中引用的枚举值是否在枚举定义中存在。
     * 例如 'RevenueConfirmationStatusEnum.done' → 检查 done 是否在 RevenueConfirmationStatusEnum 的 enumValueDefs 中。
     */
    private void validateEnumReferences(BaseappObjectField field,
                                        Map<String, List<BaseappObjectField>> groupedFields,
                                        ValidationReport report) {
        Map<String, Set<String>> enumValueMap = impactAnalyzerService.getEnumValueMap();
        if (enumValueMap.isEmpty()) return;

        // 收集所有需要检查的表达式文本
        List<String> expressions = new ArrayList<>();
        if (field.getExpression() != null && !field.getExpression().trim().isEmpty()) {
            expressions.add(field.getExpression());
        }
        if (field.getTriggerExpr() != null && !field.getTriggerExpr().trim().isEmpty()) {
            expressions.add(field.getTriggerExpr());
        }
        if (field.getVirtualExpr() != null && !field.getVirtualExpr().trim().isEmpty()) {
            expressions.add(field.getVirtualExpr());
        }

        // writeBackExpr 是 JSON 数组，需要提取每个元素的 expression 和 condition
        if (field.getWriteBackExpr() != null && !field.getWriteBackExpr().trim().isEmpty()) {
            try {
                List<WriteBackExpr> writeBacks = objectMapper.readValue(
                        field.getWriteBackExpr(), new TypeReference<List<WriteBackExpr>>() {});
                for (WriteBackExpr wb : writeBacks) {
                    if (wb.getExpression() != null && !wb.getExpression().trim().isEmpty()) {
                        expressions.add(wb.getExpression());
                    }
                    if (wb.getCondition() != null && !wb.getCondition().trim().isEmpty()) {
                        expressions.add(wb.getCondition());
                    }
                    if (wb.getValidateExpr() != null && !wb.getValidateExpr().trim().isEmpty()) {
                        expressions.add(wb.getValidateExpr());
                    }
                }
            } catch (JsonProcessingException e) {
                // writeBackExpr JSON 解析失败已在其他规则中报告，此处跳过
            }
        }

        // 去重：同一字段内相同的枚举引用只报一次
        Set<String> reported = new HashSet<>();

        for (String exprText : expressions) {
            Matcher matcher = ENUM_REF_PATTERN.matcher(exprText);
            while (matcher.find()) {
                String enumName = matcher.group(1);
                String enumValue = matcher.group(2);
                String refKey = enumName + "." + enumValue;
                if (reported.contains(refKey)) continue;
                reported.add(refKey);

                Set<String> validValues = enumValueMap.get(enumName);
                if (validValues == null) {
                    // 仅当名称看起来像枚举（包含 Enum/Status/Type 后缀）时才报 WARNING
                    if (enumName.endsWith("Enum") || enumName.endsWith("Status") || enumName.endsWith("Type")) {
                        report.addItem(new ValidationErrorItem(
                                field.getObjectType(), field.getName(),
                                ExpressionType.ENUM, ErrorCategory.ENUM_TYPE_NOT_FOUND, SeverityLevel.WARNING,
                                "表达式中引用了枚举类型 `" + enumName + "` 但在元数据中未找到该枚举的定义"));
                    }
                    continue;
                }

                if (!validValues.contains(enumValue)) {
                    report.addItem(new ValidationErrorItem(
                            field.getObjectType(), field.getName(),
                            ExpressionType.ENUM, ErrorCategory.ENUM_VALUE_NOT_FOUND, SeverityLevel.ERROR,
                            "表达式中引用了 `" + enumName + "." + enumValue
                                    + "` 但该值不在枚举定义中。合法值: " + validValues));
                }
            }
        }
    }

    // ========== 通用校验方法 ==========

    /**
     * 检测表达式中是否存在嵌套聚合函数（例如 sum(sum(x)) 或聚合函数参数里再套聚合）
     */
    private void detectNestedAggregate(BaseappObjectField field, String sql, ExpressionType type, ValidationReport report) {
        String cleanSql = sql.trim().replace("${SystemFields}", " ");
        try {
            Expression parsed = CCJSqlParserUtil.parseExpression(cleanSql);
            // 用一个简单的递归 depth tracker
            checkNestedAggregateRecursive(field, parsed, type, 0, report);
        } catch (Exception e) {
            // 解析错误已经在 validateSqlExpr 中报告过了
        }
    }

    private void checkNestedAggregateRecursive(BaseappObjectField field, Expression expr, ExpressionType type, int aggregateDepth, ValidationReport report) {
        if (expr instanceof Function) {
            Function func = (Function) expr;
            String funcName = func.getName().toUpperCase();
            int newDepth = AGGREGATE_FUNCTIONS.contains(funcName) ? aggregateDepth + 1 : aggregateDepth;

            if (newDepth > 1) {
                report.addItem(new ValidationErrorItem(field.getObjectType(), field.getName(),
                        type, ErrorCategory.NESTED_AGGREGATE, SeverityLevel.FATAL,
                        "检测到嵌套聚合函数: " + func + "，PostgreSQL 不允许 aggregate 内部嵌套 aggregate"));
            }

            if (func.getParameters() != null) {
                for (Expression arg : func.getParameters().getExpressions()) {
                    checkNestedAggregateRecursive(field, arg, type, newDepth, report);
                }
            }
        }
    }

    /**
     * 计算表达式（expression）为 MVEL，不做 SQL/JSQLParser 解析；
     * 仅校验主表/子表/跨对象字段引用是否存在。
     */
    private void validateMvelExpression(BaseappObjectField currentField, String mvel,
                                        String contextObject,
                                        Map<String, List<BaseappObjectField>> groupedFields,
                                        ValidationReport report) {
        if (mvel == null || mvel.isBlank() || contextObject == null) {
            return;
        }
        List<BaseappObjectField> contextFields = groupedFields.getOrDefault(contextObject, Collections.emptyList());
        if (contextFields.isEmpty()) {
            report.addItem(new ValidationErrorItem(currentField.getObjectType(), currentField.getName(),
                    ExpressionType.EXPRESSION, ErrorCategory.OBJECT_NOT_FOUND, SeverityLevel.ERROR,
                    "Context object not found: " + contextObject));
            return;
        }

        Map<String, Set<String>> varsByScope = ExprUtils.extractVariablesFromExpression(mvel);
        for (Map.Entry<String, Set<String>> entry : varsByScope.entrySet()) {
            String scope = entry.getKey();
            for (String varName : entry.getValue()) {
                if (shouldSkipMvelIdentifier(varName)) {
                    continue;
                }
                if (ExprUtils.KEY_MAIN.equals(scope)) {
                    if (!fieldExistsInObject(contextFields, varName) && !isRelationRefOnObject(contextFields, varName)) {
                        report.addItem(new ValidationErrorItem(currentField.getObjectType(), currentField.getName(),
                                ExpressionType.EXPRESSION, ErrorCategory.FIELD_NOT_FOUND, SeverityLevel.ERROR,
                                "MVEL 表达式引用了不存在的字段 `" + varName + "`（对象 `" + contextObject + "`）"));
                    }
                } else {
                    String detailObject = findDetailObjectForListField(scope, contextFields);
                    List<BaseappObjectField> detailFields = detailObject != null
                            ? groupedFields.getOrDefault(detailObject, Collections.emptyList())
                            : Collections.emptyList();
                    if (detailFields.isEmpty()) {
                        continue;
                    }
                    if (!fieldExistsInObject(detailFields, varName)) {
                        report.addItem(new ValidationErrorItem(currentField.getObjectType(), currentField.getName(),
                                ExpressionType.EXPRESSION, ErrorCategory.FIELD_NOT_FOUND, SeverityLevel.ERROR,
                                "MVEL 子表引用 `" + scope + "." + varName + "` 在对象 `"
                                        + (detailObject != null ? detailObject : scope) + "` 中未找到对应字段"));
                    }
                }
            }
        }

        Map<String, String> crossRefs = ExprUtils.extractCrossObjectRefs(mvel);
        for (Map.Entry<String, String> ref : crossRefs.entrySet()) {
            String fkField = ref.getKey();
            String refField = ref.getValue();
            if (shouldSkipMvelIdentifier(fkField) || shouldSkipMvelIdentifier(refField)) {
                continue;
            }
            if (!fieldExistsInObject(contextFields, fkField) && !isRelationRefOnObject(contextFields, fkField)) {
                report.addItem(new ValidationErrorItem(currentField.getObjectType(), currentField.getName(),
                        ExpressionType.EXPRESSION, ErrorCategory.FIELD_NOT_FOUND, SeverityLevel.ERROR,
                        "MVEL 外键字段 `" + fkField + "` 在对象 `" + contextObject + "` 中不存在"));
                continue;
            }
            String targetObject = resolveReferTargetObject(contextFields, fkField);
            if (targetObject == null) {
                continue;
            }
            List<BaseappObjectField> targetFields = groupedFields.getOrDefault(targetObject, Collections.emptyList());
            if (!targetFields.isEmpty()
                    && !fieldExistsInObject(targetFields, refField)
                    && !isRelationRefOnObject(targetFields, refField)) {
                report.addItem(new ValidationErrorItem(currentField.getObjectType(), currentField.getName(),
                        ExpressionType.EXPRESSION, ErrorCategory.FIELD_NOT_FOUND, SeverityLevel.ERROR,
                        "MVEL 引用 `" + fkField + "." + refField + "` 在对象 `" + targetObject + "` 中未找到字段"));
            }
        }
    }

    private static boolean shouldSkipMvelIdentifier(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        return MVEL_SKIP_IDENTIFIERS.contains(name.toLowerCase(Locale.ROOT))
                || SQL_SKIP_IDENTIFIERS.contains(name.toLowerCase(Locale.ROOT));
    }

    private static boolean fieldExistsInObject(List<BaseappObjectField> fields, String fieldName) {
        String normalized = fieldName.replace("_", "").toLowerCase(Locale.ROOT);
        for (BaseappObjectField f : fields) {
            String name = f.getName() != null ? f.getName().replace("_", "").toLowerCase(Locale.ROOT) : "";
            String apiName = f.getApiName() != null ? f.getApiName().replace("_", "").toLowerCase(Locale.ROOT) : "";
            if (normalized.equals(name) || normalized.equals(apiName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRelationRefOnObject(List<BaseappObjectField> fields, String fieldName) {
        String normalized = fieldName.replace("_", "").toLowerCase(Locale.ROOT);
        String withId = normalized + "id";
        for (BaseappObjectField f : fields) {
            String name = f.getName() != null ? f.getName().replace("_", "").toLowerCase(Locale.ROOT) : "";
            String apiName = f.getApiName() != null ? f.getApiName().replace("_", "").toLowerCase(Locale.ROOT) : "";
            if (withId.equals(name) || withId.equals(apiName)) {
                return true;
            }
        }
        return false;
    }

    private String findDetailObjectForListField(String listFieldName, List<BaseappObjectField> fields) {
        for (BaseappObjectField f : fields) {
            if (!listFieldName.equals(f.getName()) && !listFieldName.equals(f.getApiName())) {
                continue;
            }
            if (f.getType() == null || !"list".equalsIgnoreCase(f.getType().trim())) {
                continue;
            }
            if (f.getRefObjectType() != null && !f.getRefObjectType().isBlank()) {
                return f.getRefObjectType();
            }
            if (f.getSourceInfo() == null || f.getSourceInfo().isBlank()) {
                continue;
            }
            try {
                JsonNode si = objectMapper.readTree(f.getSourceInfo());
                String sourceEntity = si.path("sourceEntityName").asText(null);
                if (sourceEntity != null && !sourceEntity.isBlank()) {
                    return sourceEntity;
                }
            } catch (JsonProcessingException ignored) {
                // 与 ExpressionFieldService 一致的宽松解析
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
        return null;
    }

    private String resolveReferTargetObject(List<BaseappObjectField> contextFields, String fkFieldName) {
        String norm = fkFieldName.replace("_", "").toLowerCase(Locale.ROOT);
        for (BaseappObjectField f : contextFields) {
            String name = f.getName() != null ? f.getName().replace("_", "").toLowerCase(Locale.ROOT) : "";
            String api = f.getApiName() != null ? f.getApiName().replace("_", "").toLowerCase(Locale.ROOT) : "";
            if (!norm.equals(name) && !norm.equals(api)) {
                continue;
            }
            if (f.getRefObjectType() != null && !f.getRefObjectType().isBlank()) {
                return f.getRefObjectType();
            }
            if (f.getReferInfo() != null && !f.getReferInfo().isBlank()) {
                try {
                    JsonNode ri = objectMapper.readTree(f.getReferInfo());
                    JsonNode entities = ri.path("referEntities");
                    if (entities.isArray() && !entities.isEmpty()) {
                        String refer = entities.get(0).path("referEntityName").asText(null);
                        if (refer != null && !refer.isBlank()) {
                            return refer;
                        }
                    }
                } catch (JsonProcessingException ignored) {
                }
            }
        }
        return null;
    }

    private void validateSqlExpr(BaseappObjectField currentField, String sql, ExpressionType type, String contextObject, Map<String, List<BaseappObjectField>> groupedFields, ValidationReport report) {
        String cleanSql = sql.trim().replace("${SystemFields}", " ");
        try {
            Expression jsExpr = CCJSqlParserUtil.parseExpression(cleanSql);
            jsExpr.accept(new ExpressionVisitorAdapter() {
                @Override
                public void visit(Function function) {
                    validateFunction(currentField, function, type, contextObject, groupedFields, report);
                    super.visit(function);
                }

                @Override
                public void visit(Column column) {
                    validateColumn(currentField, column, type, contextObject, groupedFields, report);
                }
            });
        } catch (Exception e) {
            String shortMsg = e.getMessage() != null ? e.getMessage().split("\n")[0] : "Parse error";
            report.addItem(new ValidationErrorItem(currentField.getObjectType(), currentField.getName(), type, ErrorCategory.FATAL_PARSE_ERROR, SeverityLevel.FATAL, shortMsg));
        }
    }

    private void validateColumn(BaseappObjectField currentField, Column column, ExpressionType type,
                                String contextObject, Map<String, List<BaseappObjectField>> groupedFields,
                                ValidationReport report) {
        String colName = column.getColumnName();
        if (colName == null || colName.isEmpty()) return;

        // 跳过 SQL 关键字 / 字面量（true, false, null 等被 JSQLParser 当作 Column 的情况）
        if (SQL_SKIP_IDENTIFIERS.contains(colName.toLowerCase())) return;

        // 处理表限定符：xxx.a → 去 xxx 对象里找 a 字段
        // 若 xxx 在 groupedFields 中能匹配到，则切换上下文；否则属于外部引用，直接跳过
        String resolvedContext = contextObject;
        if (column.getTable() != null && column.getTable().getName() != null) {
            String tableQualifier = column.getTable().getName();
            String matchedObject = groupedFields.keySet().stream()
                    .filter(k -> k.equalsIgnoreCase(tableQualifier)
                            || k.replace("_", "").equalsIgnoreCase(tableQualifier.replace("_", "")))
                    .findFirst()
                    .orElse(null);
            if (matchedObject != null) {
                resolvedContext = matchedObject; // 切换到表限定符指定的对象
            } else {
                return; // 外部引用（别名或外部对象），无法校验，跳过
            }
        }

        List<BaseappObjectField> contextFields = groupedFields.getOrDefault(resolvedContext, Collections.emptyList());
        if (contextFields.isEmpty()) {
            report.addItem(new ValidationErrorItem(currentField.getObjectType(), currentField.getName(),
                    type, ErrorCategory.OBJECT_NOT_FOUND, SeverityLevel.ERROR,
                    "Context object not found: " + resolvedContext));
            return;
        }

        String normalizedColName = colName.replace("_", "").toLowerCase();

        // 跳过系统内置字段（SQL_SKIP_IDENTIFIERS 已在上方过滤 lowercase，这里再过一遍 normalized 形式）
        if (SQL_SKIP_IDENTIFIERS.contains(normalizedColName)) return;

        boolean found = contextFields.stream().anyMatch(f -> {
            String name = f.getName() != null ? f.getName().replace("_", "").toLowerCase() : "";
            String apiName = f.getApiName() != null ? f.getApiName().replace("_", "").toLowerCase() : "";
            return normalizedColName.equals(name) || normalizedColName.equals(apiName);
        });

        if (!found) {
            // 如果无 table qualifier（即未切换上下文），还要检查是否是关联对象引用名称：
            // 如 'contractSubjectMatterItem' 对应字段 'contractSubjectMatterItemId'。
            boolean isRelationRef = column.getTable() == null
                    && contextFields.stream().anyMatch(f -> {
                        String name   = f.getName()    != null ? f.getName().replace("_", "").toLowerCase()    : "";
                        String apiName = f.getApiName() != null ? f.getApiName().replace("_", "").toLowerCase() : "";
                        String withId = normalizedColName + "id";
                        return withId.equals(name) || withId.equals(apiName);
                    });
            if (isRelationRef) return; // relation 引用，跳过

            report.addItem(new ValidationErrorItem(currentField.getObjectType(), currentField.getName(),
                    type, ErrorCategory.FIELD_NOT_FOUND, SeverityLevel.ERROR,
                    "Field `" + colName + "` not found in `" + resolvedContext + "`"));
        }
    }

    private void validateFunction(BaseappObjectField currentField, Function function, ExpressionType type,
                                  String contextObject, Map<String, List<BaseappObjectField>> groupedFields,
                                  ValidationReport report) {
        // 函数类型检查已移除：
        //   COUNT 可作用于任意类型，STRING_AGG 用于字符串聚合，
        //   SUM/AVG 在 writeBackExpr 中通常包裹 CASE WHEN 等复合表达式（非简单 Column），
        //   metadata 的 type 字段也不总能准确反映 DB 层实际类型，误报率高。
        // 目前 validateFunction 仅作为扩展点保留，供将来需要时添加更精准的函数级校验。
    }

    // ========== 辅助方法 ==========

    /**
     * 点号路径 idField 的两级校验：
     *   idField = "contract.frameContractId"
     *   1. 在 srcObjectType 中找 "contractId"(模块名+Id) 或 "contract" 字段
     *   2. 解析该字段的 referInfo 找到引用对象（如 ContractView.json）
     *   3. 校验 ContractView.json 中是否存在 "frameContractId"
     */
    private void validateDottedIdField(BaseappObjectField field, WriteBackExpr wb, String srcCtx,
                                       Map<String, List<BaseappObjectField>> groupedFields,
                                       ValidationReport report) {
        String[] parts = wb.getIdField().split("\\.", 2);
        String modulePrefix = parts[0];   // e.g. "contract"
        String leafField   = parts[1];    // e.g. "frameContractId"

        // Step1: 在 srcObjectType 中查找 "${prefix}Id" 或 "${prefix}" 字段
        List<BaseappObjectField> srcFields = groupedFields.get(srcCtx);
        String normalizedPrefix = modulePrefix.replace("_", "").toLowerCase();

        Optional<BaseappObjectField> refFieldOpt = srcFields.stream()
                .filter(f -> {
                    String n = f.getName()    != null ? f.getName().replace("_",    "").toLowerCase() : "";
                    String a = f.getApiName() != null ? f.getApiName().replace("_", "").toLowerCase() : "";
                    // 匹配 "prefixId" 或 "prefix"
                    return (normalizedPrefix + "id").equals(n) || (normalizedPrefix + "id").equals(a)
                            || normalizedPrefix.equals(n) || normalizedPrefix.equals(a);
                })
                .findFirst();

        if (!refFieldOpt.isPresent()) {
            report.addItem(new ValidationErrorItem(field.getObjectType(), field.getName(),
                    ExpressionType.WRITE_BACK, ErrorCategory.FIELD_NOT_FOUND, SeverityLevel.ERROR,
                    "writeBackExpr.idField '" + wb.getIdField() + "' 中的关联字段 '"
                    + modulePrefix + "Id' 在源对象 '" + srcCtx + "' 中未找到"));
            return;
        }

        // Step2: 获取引用对象名（先用 refObjectType，再尝试解析 referInfo）
        BaseappObjectField refField = refFieldOpt.get();
        String refEntityName = refField.getRefObjectType();
        if ((refEntityName == null || refEntityName.trim().isEmpty()) && refField.getReferInfo() != null) {
            refEntityName = extractFirstReferEntityName(refField.getReferInfo());
        }

        if (refEntityName == null || refEntityName.trim().isEmpty()) {
            // 无法确定引用对象，跳过第二级校验
            return;
        }

        // Step3: 在引用对象中校验叶字段
        if (!groupedFields.containsKey(refEntityName)) {
            // 引用对象不在已知元数据中（外部对象），跳过
            return;
        }

        boolean leafFound = fieldExistsInObject(leafField, refEntityName, groupedFields);
        if (!leafFound) {
            report.addItem(new ValidationErrorItem(field.getObjectType(), field.getName(),
                    ExpressionType.WRITE_BACK, ErrorCategory.FIELD_NOT_FOUND, SeverityLevel.ERROR,
                    "writeBackExpr.idField '" + wb.getIdField() + "' 中的字段 '" + leafField
                    + "' 在引用对象 '" + refEntityName + "' 中未找到"));
        }
    }

    /**
     * 解析 referInfo JSON 提取第一个 referEntityName。
     * 支持两种格式：
     *   格式A（对象）: { "referEntities": [ { "referEntityName": "ContractView.json" } ] }
     *   格式B（数组）: [ { "referEntityName": "ContractView.json" } ]
     */
    private String extractFirstReferEntityName(String referInfoJson) {
        if (referInfoJson == null || referInfoJson.trim().isEmpty()) return null;
        try {
            JsonNode root = objectMapper.readTree(referInfoJson);
            JsonNode entities;
            if (root.isArray()) {
                // 格式B：referInfo 本身就是数组
                entities = root;
            } else {
                // 格式A：referInfo 是对象，referEntities 是子字段
                entities = root.get("referEntities");
            }
            if (entities != null && entities.isArray() && entities.size() > 0) {
                JsonNode nameNode = entities.get(0).get("referEntityName");
                if (nameNode != null && !nameNode.isNull()) {
                    return nameNode.asText();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse referInfo JSON: {}", referInfoJson);
        }
        return null;
    }

    private boolean fieldExistsInObject(String fieldName, String objectType, Map<String, List<BaseappObjectField>> groupedFields) {
        List<BaseappObjectField> fields = groupedFields.getOrDefault(objectType, Collections.emptyList());
        String normalized = fieldName.replace("_", "").toLowerCase();
        return fields.stream().anyMatch(f -> {
            String name = f.getName() != null ? f.getName().replace("_", "").toLowerCase() : "";
            String apiName = f.getApiName() != null ? f.getApiName().replace("_", "").toLowerCase() : "";
            return normalized.equals(name) || normalized.equals(apiName);
        });
    }
}
