package com.deepmodel.relation.controller;

import com.deepmodel.relation.env.EnvContext;
import com.deepmodel.relation.env.EnvResolver;
import com.deepmodel.relation.env.EnvSnapshotManager;
import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.GraphModels;
import com.deepmodel.relation.service.ImpactAnalyzerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class DebugController {

    private final ImpactAnalyzerService service;
    private final EnvResolver envResolver;
    private final EnvSnapshotManager snapshotManager;

    public DebugController(ImpactAnalyzerService service,
                           EnvResolver envResolver,
                           EnvSnapshotManager snapshotManager) {
        this.service = service;
        this.envResolver = envResolver;
        this.snapshotManager = snapshotManager;
    }

    @GetMapping("/api/debug/status")
    public Map<String, Object> debugStatus() {
        Map<String, Object> result = new HashMap<>();
        try {
            String env = EnvContext.currentOrNull();
            result.put("env", env);
            result.put("metadataSource", "GraphQL");

            if (env == null || env.isBlank()) {
                result.put("error", "未选择环境（请在页面顶部选择环境，或请求带 X-Env header）");
                return result;
            }

            try {
                result.put("graphqlUrl", envResolver.getGraphqlUrl(env));
                result.put("writeBackSqlApiUrl", envResolver.getWriteBackSqlApiUrl(env));
            } catch (Exception e) {
                result.put("envResolveError", e.getMessage());
            }

            List<BaseappObjectField> allRows = service.getAllFields();
            result.put("totalRecords", allRows.size());
            result.put("loadedEnvs", snapshotManager.loadedEnvs());

            if (!allRows.isEmpty()) {
                result.put("sampleRecord", allRows.get(0));
            }

            BaseappObjectField testField1 = service.getFieldInfo("ArReceipt", "originAmount");
            BaseappObjectField testField2 = service.getFieldInfo("ArReceiptItem", "originAmount");
            result.put("ArReceipt.originAmount", testField1 != null);
            result.put("ArReceiptItem.originAmount", testField2 != null);

            Map<String, Long> objectCount = new HashMap<>();
            for (BaseappObjectField field : allRows) {
                String objType = field.getObjectType();
                objectCount.put(objType, objectCount.getOrDefault(objType, 0L) + 1);
            }
            result.put("objectTypeCount", objectCount);

        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            result.put("stackTrace", getStackTrace(e));
        }
        return result;
    }

    @GetMapping("/api/debug/testQuery")
    public Map<String, Object> testQuery(@RequestParam("objectType") String objectType,
                                        @RequestParam("field") String field) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<BaseappObjectField> directQuery = service.getFieldsByObject(objectType);
            result.put("directQueryCount", directQuery.size());

            BaseappObjectField fieldInfo = service.getFieldInfo(objectType, field);
            result.put("fieldFound", fieldInfo != null);
            if (fieldInfo != null) {
                result.put("fieldDetails", fieldInfo);
            } else {
                result.put("message", "字段未找到");
                result.put("availableFields", directQuery);
            }

            try {
                GraphModels.Graph graph = service.analyze(objectType, field, 2, 0, false);
                result.put("analysisWorking", true);
                result.put("nodeCount", graph.nodes.size());
                result.put("edgeCount", graph.edges.size());
            } catch (Exception e) {
                result.put("analysisError", e.getMessage());
            }

        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            result.put("stackTrace", getStackTrace(e));
        }
        return result;
    }

    @GetMapping("/api/debug/whyNoRelation")
    public Map<String, Object> whyNoRelation(@RequestParam("objectType") String objectType,
                                           @RequestParam("field") String field) {
        Map<String, Object> result = new HashMap<>();
        try {
            BaseappObjectField fieldInfo = service.getFieldInfo(objectType, field);
            result.put("fieldExists", fieldInfo != null);
            if (fieldInfo != null) {
                result.put("fieldDetails", fieldInfo);
            }

            List<BaseappObjectField> sameObjectFields = service.getFieldsByObject(objectType);
            result.put("sameObjectFieldCount", sameObjectFields.size());

            List<BaseappObjectField> referencingFields = sameObjectFields.stream()
                .filter(f -> {
                    boolean hasRef = false;
                    if (f.getExpression() != null && f.getExpression().contains(field)) hasRef = true;
                    if (f.getTriggerExpr() != null && f.getTriggerExpr().contains(field)) hasRef = true;
                    if (f.getVirtualExpr() != null && f.getVirtualExpr().contains(field)) hasRef = true;
                    return hasRef;
                })
                .collect(Collectors.toList());
            result.put("referencingFieldsCount", referencingFields.size());
            result.put("referencingFields", referencingFields);

            List<BaseappObjectField> allRows = service.getAllFields();
            List<BaseappObjectField> writeBackFields = allRows.stream()
                .filter(f -> f.getWriteBackExpr() != null && !f.getWriteBackExpr().trim().isEmpty())
                .filter(f -> {
                    String wbExpr = f.getWriteBackExpr();
                    boolean hits = wbExpr.contains("\"srcObjectType\":\"" + objectType + "\"")
                            || wbExpr.contains("'srcObjectType':'" + objectType + "'")
                            || wbExpr.contains("srcItemObjectType='" + objectType + "'");
                    return hits && wbExpr.contains(field);
                })
                .collect(Collectors.toList());
            result.put("writeBackFieldsCount", writeBackFields.size());
            result.put("writeBackFields", writeBackFields);

            List<BaseappObjectField> nameVariations = sameObjectFields.stream()
                .filter(f -> {
                    String apiName = f.getApiName();
                    String name = f.getName();
                    return (apiName != null && apiName.equals(field))
                            || (name != null && name.equals(field));
                })
                .collect(Collectors.toList());
            result.put("nameVariationsCount", nameVariations.size());
            result.put("nameVariations", nameVariations);

        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            result.put("stackTrace", getStackTrace(e));
        }
        return result;
    }

    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}
