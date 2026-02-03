package com.deepmodel.relation.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 单据映射关系可视化数据模型
 */
public class ProjectionModels {

    /**
     * 映射关系基本信息
     */
    public static class Projection {
        public String id;
        public String objectType;
        public String toObjectType;
        public String criteriaStr;
        
        public Projection() {}
        
        public Projection(String id, String objectType, String toObjectType, String criteriaStr) {
            this.id = id;
            this.objectType = objectType;
            this.toObjectType = toObjectType;
            this.criteriaStr = criteriaStr;
        }
    }

    /**
     * 映射关系节点（用于图谱展示）
     */
    public static class ProjectionNode {
        public String id;
        public String objectType;
        public String toObjectType;
        public String projectionId;
        public String criteriaStr;
        public boolean hasChildren;
        public int fieldMappingCount;
        public int childMappingCount;
        
        public ProjectionNode() {}
    }

    /**
     * 映射关系边（用于图谱展示）
     */
    public static class ProjectionEdge {
        public String id;
        public String source;
        public String target;
        public String projectionId;
        public String label;
        public boolean hasChildren;
        
        public ProjectionEdge() {}
        
        public ProjectionEdge(String source, String target, String projectionId) {
            this.source = source;
            this.target = target;
            this.projectionId = projectionId;
            this.id = projectionId;
        }
    }

    /**
     * 映射关系图谱
     */
    public static class ProjectionGraph {
        public List<ProjectionNode> nodes = new ArrayList<>();
        public List<ProjectionEdge> edges = new ArrayList<>();
    }

    /**
     * 字段映射配置
     */
    public static class FieldMapping {
        public String id;
        public String objectField;
        public String toObjectField;
        public String expr;
        public String fieldMappingTypeId;
        
        public FieldMapping() {}
    }

    /**
     * 字段映射详情（用于详情面板展示）
     */
    public static class FieldMappingDetail {
        public String fromField;
        public String toField;
        public String mappingType;
        public String expression;
        public String level;  // main / child
        public String childObjectType;
        
        public FieldMappingDetail() {}
        
        public FieldMappingDetail(String fromField, String toField, String mappingType, String expression, String level) {
            this.fromField = fromField;
            this.toField = toField;
            this.mappingType = mappingType;
            this.expression = expression;
            this.level = level;
        }
    }

    /**
     * 子表映射配置
     */
    public static class ProjectionChild {
        public String id;
        public String objectType;
        public String toObjectType;
        public String criteriaStr;
        public int fieldCount;
        
        public ProjectionChild() {}
    }

    /**
     * 子表字段映射配置
     */
    public static class ProjectionChildFieldMapping {
        public String id;
        public String objectField;
        public String toObjectField;
        public String expr;
        public String fieldMappingTypeId;
        
        public ProjectionChildFieldMapping() {}
    }

    /**
     * 映射详情（包含主表和子表的所有字段映射）
     */
    public static class ProjectionDetail {
        public Projection projection;
        public List<FieldMappingDetail> mainFieldMappings = new ArrayList<>();
        public List<ChildMappingDetail> children = new ArrayList<>();
        
        public ProjectionDetail() {}
    }

    /**
     * 子表映射详情
     */
    public static class ChildMappingDetail {
        public ProjectionChild child;
        public List<FieldMappingDetail> fieldMappings = new ArrayList<>();
        
        public ChildMappingDetail() {}
        
        public ChildMappingDetail(ProjectionChild child) {
            this.child = child;
        }
    }

    /**
     * 字段追踪结果
     */
    public static class FieldTrackResult {
        public String projectionId;
        public String objectType;
        public String toObjectType;
        public String objectField;
        public String toObjectField;
        public String expr;
        public String level;  // main / child
        public String childObjectType;
        public String childToObjectType;
        
        public FieldTrackResult() {}
    }

    /**
     * 映射链路（用于追踪上下游）
     */
    public static class MappingChain {
        public String startObject;
        public List<ChainNode> chain = new ArrayList<>();
        
        public MappingChain() {}
        
        public MappingChain(String startObject) {
            this.startObject = startObject;
        }
    }

    /**
     * 链路节点
     */
    public static class ChainNode {
        public String objectType;
        public String projectionId;
        public String direction;  // upstream / downstream
        
        public ChainNode() {}
        
        public ChainNode(String objectType, String projectionId, String direction) {
            this.objectType = objectType;
            this.projectionId = projectionId;
            this.direction = direction;
        }
    }

    /**
     * 对象节点（用于图谱展示）
     */
    public static class ObjectNode {
        public String objectType;
        public int upstreamCount;
        public int downstreamCount;
        
        public ObjectNode() {}
        
        public ObjectNode(String objectType) {
            this.objectType = objectType;
        }
    }
}
