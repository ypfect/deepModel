package com.deepmodel.relation.controller;

import com.deepmodel.relation.dao.ProjectionMapper;
import com.deepmodel.relation.model.ProjectionModels.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单据映射关系调试接口
 */
@RestController
@RequestMapping("/api/projection/debug")
public class ProjectionDebugController {

    @Autowired
    private ProjectionMapper projectionMapper;

    /**
     * 检查数据库表是否有数据
     * GET /api/projection/debug/check
     */
    @GetMapping("/check")
    public Map<String, Object> checkDatabase() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 检查映射关系表
            List<Projection> projections = projectionMapper.findAllProjections();
            result.put("projectionCount", projections.size());
            result.put("projections", projections);
            
            // 检查对象类型
            List<String> objects = projectionMapper.findAllObjectTypes();
            result.put("objectTypeCount", objects.size());
            result.put("objectTypes", objects);
            
            // 如果有数据，检查第一个映射的详细信息
            if (!projections.isEmpty()) {
                String firstId = projections.get(0).id;
                
                // 主表字段数量
                int fieldCount = projectionMapper.countFieldMappings(firstId);
                result.put("sampleFieldMappingCount", fieldCount);
                
                // 子表数量
                int childCount = projectionMapper.countChildren(firstId);
                result.put("sampleChildCount", childCount);
                
                // 获取实际的字段映射
                List<FieldMapping> fields = projectionMapper.findFieldMappings(firstId);
                result.put("sampleFieldMappings", fields);
                
                // 获取子表
                List<ProjectionChild> children = projectionMapper.findProjectionChildren(firstId);
                result.put("sampleChildren", children);
            }
            
            result.put("status", "ok");
            result.put("message", "数据库检查完成");
            
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
            result.put("error", e.getClass().getName());
            e.printStackTrace();
        }
        
        return result;
    }

    /**
     * 获取原始表数据统计
     * GET /api/projection/debug/table-stats
     */
    @GetMapping("/table-stats")
    public Map<String, Object> getTableStats() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            result.put("baseapp_projection", projectionMapper.findAllProjections().size());
            result.put("object_types", projectionMapper.findAllObjectTypes().size());
            result.put("status", "ok");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        
        return result;
    }
}
