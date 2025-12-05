package com.deepmodel.relation.controller;

import com.deepmodel.relation.dao.BaseappObjectFieldMapper;
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
    
    private final BaseappObjectFieldMapper mapper;
    private final ImpactAnalyzerService service;
    
    public DebugController(BaseappObjectFieldMapper mapper, ImpactAnalyzerService service) {
        this.mapper = mapper;
        this.service = service;
    }
    
    @GetMapping("/api/debug/status")
    public Map<String, Object> debugStatus() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<BaseappObjectField> allRows = mapper.selectAll();
            result.put("totalRecords", allRows.size());
            result.put("databaseConnection", "OK");
            
            if (allRows.size() > 0) {
                BaseappObjectField sample = allRows.get(0);
                result.put("sampleRecord", sample);
            }
            
            // 检查缓存状态
            service.reload();
            result.put("reloadCompleted", true);
            
            // 测试查询几个常见字段
            BaseappObjectField testField1 = service.getFieldInfo("ArReceipt", "originAmount");
            BaseappObjectField testField2 = service.getFieldInfo("ArReceiptItem", "originAmount");
            result.put("ArReceipt.originAmount", testField1 != null);
            result.put("ArReceiptItem.originAmount", testField2 != null);
            
            // 统计对象类型数量
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
            // 测试数据库直接查询
            List<BaseappObjectField> directQuery = mapper.selectByObjectType(objectType);
            result.put("directQueryCount", directQuery.size());
            
            // 测试服务层查询
            BaseappObjectField fieldInfo = service.getFieldInfo(objectType, field);
            result.put("fieldFound", fieldInfo != null);
            if (fieldInfo != null) {
                result.put("fieldDetails", fieldInfo);
            } else {
                result.put("message", "字段未找到");
                result.put("availableFields", directQuery);
            }
            
            // 测试完整分析
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
    
    /**
     * 专门调试为什么某个字段没有找到依赖关系
     */
    @GetMapping("/api/debug/whyNoRelation")
    public Map<String, Object> whyNoRelation(@RequestParam("objectType") String objectType,
                                           @RequestParam("field") String field) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. 检查字段是否存在
            BaseappObjectField fieldInfo = service.getFieldInfo(objectType, field);
            result.put("fieldExists", fieldInfo != null);
            if (fieldInfo != null) {
                result.put("fieldDetails", fieldInfo);
            }
            
            // 2. 检查同对象内的其他字段是否引用了这个字段
            List<BaseappObjectField> sameObjectFields = mapper.selectByObjectType(objectType);
            result.put("sameObjectFieldCount", sameObjectFields.size());
            
            // 查找在表达式中引用了目标字段的字段
            List<BaseappObjectField> referencingFields = sameObjectFields.stream()
                .filter(f -> {
                    String targetField = field;
                    boolean hasRef = false;
                    if (f.getExpression() != null && f.getExpression().contains(targetField)) hasRef = true;
                    if (f.getTriggerExpr() != null && f.getTriggerExpr().contains(targetField)) hasRef = true;
                    if (f.getVirtualExpr() != null && f.getVirtualExpr().contains(targetField)) hasRef = true;
                    return hasRef;
                })
                .collect(Collectors.toList());
            result.put("referencingFieldsCount", referencingFields.size());
            result.put("referencingFields", referencingFields);
            
            // 3. 检查跨对象的回写表达式
            List<BaseappObjectField> allRows = mapper.selectAll();
            List<BaseappObjectField> writeBackFields = allRows.stream()
                .filter(f -> f.getWriteBackExpr() != null && !f.getWriteBackExpr().trim().isEmpty())
                .filter(f -> {
                    String wbExpr = f.getWriteBackExpr();
                    // 检查是否命中当前对象
                    boolean hits = false;
                    if (wbExpr.contains("\"srcObjectType\":\"" + objectType + "\"")) hits = true;
                    if (wbExpr.contains("'srcObjectType':'" + objectType + "'")) hits = true;
                    if (wbExpr.contains("srcItemObjectType='" + objectType + "'")) hits = true;
                    
                    // 检查表达式是否引用了目标字段
                    if (hits && wbExpr.contains(field)) {
                        return true;
                    }
                    return false;
                })
                .collect(Collectors.toList());
            result.put("writeBackFieldsCount", writeBackFields.size());
            result.put("writeBackFields", writeBackFields);
            
            // 4. 检查字段名称匹配
            List<BaseappObjectField> nameVariations = sameObjectFields.stream()
                .filter(f -> {
                    String apiName = f.getApiName();
                    String name = f.getName();
                    return (apiName != null && apiName.equals(field)) || 
                           (name != null && name.equals(field));
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