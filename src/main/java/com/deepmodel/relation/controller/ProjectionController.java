package com.deepmodel.relation.controller;

import com.deepmodel.relation.model.ProjectionModels.*;
import com.deepmodel.relation.service.ProjectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单据映射关系可视化接口
 */
@RestController
@RequestMapping("/api/projection")
public class ProjectionController {

    @Autowired
    private ProjectionService projectionService;

    /**
     * 获取所有映射关系图谱
     * GET /api/projection/graph
     */
    @GetMapping("/graph")
    public ProjectionGraph getProjectionGraph() {
        return projectionService.getProjectionGraph();
    }

    /**
     * 获取指定对象的映射链路
     * GET /api/projection/chain?objectType={type}&direction={upstream|downstream|both}&depth={depth}
     */
    @GetMapping("/chain")
    public ProjectionGraph getObjectChain(
            @RequestParam String objectType,
            @RequestParam(defaultValue = "both") String direction,
            @RequestParam(defaultValue = "5") int depth) {
        return projectionService.getObjectChain(objectType, direction, depth);
    }

    /**
     * 获取映射详情（包含所有字段配置）
     * GET /api/projection/detail/{projectionId}
     */
    @GetMapping("/detail/{projectionId}")
    public ProjectionDetail getProjectionDetail(@PathVariable String projectionId) {
        return projectionService.getProjectionDetail(projectionId);
    }

    /**
     * 字段追踪
     * GET /api/projection/track-field?field={fieldName}
     */
    @GetMapping("/track-field")
    public List<FieldTrackResult> trackField(@RequestParam String field) {
        return projectionService.trackField(field);
    }

    /**
     * 获取所有对象类型列表
     * GET /api/projection/objects
     */
    @GetMapping("/objects")
    public List<String> getAllObjectTypes() {
        return projectionService.getAllObjectTypes();
    }

    /**
     * 获取对象统计信息
     * GET /api/projection/stats?objectType={type}
     */
    @GetMapping("/stats")
    public Map<String, Object> getObjectStats(@RequestParam String objectType) {
        return projectionService.getObjectStats(objectType);
    }

    /**
     * 检测循环依赖
     * GET /api/projection/detect-cycles
     */
    @GetMapping("/detect-cycles")
    public Map<String, Object> detectCycles() {
        List<List<String>> cycles = projectionService.detectCycles();
        Map<String, Object> result = new HashMap<>();
        result.put("hasCycles", !cycles.isEmpty());
        result.put("cycleCount", cycles.size());
        result.put("cycles", cycles);
        return result;
    }

    /**
     * 健康检查
     * GET /api/projection/health
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        try {
            List<String> objects = projectionService.getAllObjectTypes();
            health.put("status", "ok");
            health.put("objectCount", objects.size());
            health.put("message", "单据映射可视化服务运行正常");
        } catch (Exception e) {
            health.put("status", "error");
            health.put("message", e.getMessage());
        }
        return health;
    }
}
