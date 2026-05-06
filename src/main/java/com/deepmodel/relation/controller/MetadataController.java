package com.deepmodel.relation.controller;

import com.deepmodel.relation.model.CascadeWriteBackInfo;
import com.deepmodel.relation.model.ExpressionFieldInfo;
import com.deepmodel.relation.model.WriteBackRelationInfo;
import com.deepmodel.relation.service.EntityReferenceService;
import com.deepmodel.relation.service.ExpressionFieldService;
import com.deepmodel.relation.service.WriteBackRelationService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 元数据查询 REST API 控制器。
 * <p>
 * 提供回写触发关系图、表达式字段依赖层级、对象引用关系图的查询能力。
 */
@RestController
@RequestMapping("/api/metadata")
public class MetadataController {

    private final WriteBackRelationService writeBackRelationService;
    private final ExpressionFieldService expressionFieldService;
    private final EntityReferenceService entityReferenceService;

    public MetadataController(WriteBackRelationService writeBackRelationService,
                              ExpressionFieldService expressionFieldService,
                              EntityReferenceService entityReferenceService) {
        this.writeBackRelationService = writeBackRelationService;
        this.expressionFieldService = expressionFieldService;
        this.entityReferenceService = entityReferenceService;
    }

    // ==================== US1: 回写触发关系 ====================

    /**
     * 查询源对象触发的所有回写关系。
     */
    @GetMapping("/writeback-relations/{objectType}")
    public Map<String, Object> getWriteBackRelations(@PathVariable String objectType) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "success");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("srcObjectType", objectType);
        Map<String, Set<WriteBackRelationInfo>> targets = writeBackRelationService.getWriteBackExprFields(objectType);
        data.put("targets", targets);
        result.put("data", data);
        return result;
    }

    /**
     * 查询目标对象被回写字段涉及的源变量。
     */
    @GetMapping("/writeback-field-vars/{objectType}")
    public Map<String, Object> getWriteBackFieldVars(@PathVariable String objectType) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "success");
        result.put("data", writeBackRelationService.getWriteBackFieldVars(objectType));
        return result;
    }

    /**
     * 查询源对象的级联回写链路。
     */
    @GetMapping("/writeback-cascade/{objectType}")
    public Map<String, Object> getWriteBackCascade(@PathVariable String objectType) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "success");
        List<CascadeWriteBackInfo> cascades = writeBackRelationService.getCascadeWriteBackInfo(objectType);
        result.put("data", cascades);
        return result;
    }

    // ==================== US2: 表达式字段依赖 ====================

    /**
     * 查询指定对象内表达式字段的变量依赖和计算层级。
     */
    @GetMapping("/expression-fields/{objectType}")
    public Map<String, Object> getExpressionFields(@PathVariable String objectType) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "success");
        ExpressionFieldInfo info = expressionFieldService.getExpressionFieldInfo(objectType);
        result.put("data", info);
        return result;
    }

    // ==================== US3: 对象引用关系 ====================

    /**
     * 查询指定对象被谁引用。
     */
    @GetMapping("/refer-relations/{objectType}")
    public Map<String, Object> getReferRelations(@PathVariable String objectType) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "success");
        result.put("data", entityReferenceService.getReferRelations(objectType));
        return result;
    }

    /**
     * 查询全量引用关系（全景图）。
     */
    @GetMapping("/refer-relations")
    public Map<String, Object> getAllReferRelations() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "success");
        result.put("data", entityReferenceService.getAllReferRelations());
        return result;
    }
}
