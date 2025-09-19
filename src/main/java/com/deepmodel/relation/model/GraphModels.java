package com.deepmodel.relation.model;

import java.util.ArrayList;
import java.util.List;

public class GraphModels {
    public static class Node {
        public String id;
        public String object;
        public String field;
        // 元数据（用于前端悬浮展示）
        public String title;
        public String type;
        public String bizType;
        public String apiName;
        public String expression;
        public String triggerExpr;
        public String virtualExpr;

        public Node(){}
        public Node(String object, String field){
            this.object = object; this.field = field; this.id = object + "." + field;
        }
    }
    public static class Edge {
        public String source;
        public String target;
        public String type; // intra | writeBack
        public Edge(){}
        public Edge(String source, String target, String type){
            this.source = source; this.target = target; this.type = type;
        }
    }
    public static class Graph {
        public List<Node> nodes = new ArrayList<>();
        public List<Edge> edges = new ArrayList<>();
    }

    // ===== 对象级聚合模型 =====
    public static class ObjectNode {
        public String object;      // 对象名
        public int fieldCount;     // 该对象在本次分析中涉及的字段数量
        public ObjectNode(){}
        public ObjectNode(String object, int fieldCount){ this.object = object; this.fieldCount = fieldCount; }
    }
    public static class ObjectEdge {
        public String sourceObject;
        public String targetObject;
        public String type; // intra | writeBack
        public int count;   // 聚合的边数量
        public ObjectEdge(){}
        public ObjectEdge(String sourceObject, String targetObject, String type, int count){
            this.sourceObject = sourceObject; this.targetObject = targetObject; this.type = type; this.count = count;
        }
    }
    public static class ObjectGraph {
        public List<ObjectNode> nodes = new ArrayList<>();
        public List<ObjectEdge> edges = new ArrayList<>();
    }

    // ===== 按对象分组的解释模型 =====
    public static class ExplainStep {
        public String type;   // intra | writeBack
        public String source; // a.b
        public String target; // c.d
        public String reason; // 文本解释
        public ExplainStep(){}
        public ExplainStep(String type, String source, String target, String reason){
            this.type = type; this.source = source; this.target = target; this.reason = reason;
        }
    }
    public static class FieldExplain {
        public String object; // 目标对象
        public String field;  // 目标字段
        public List<ExplainStep> steps = new ArrayList<>(); // 从根到该字段的步骤
        public String summary; // 一行摘要
    }
    public static class ExplainGroup {
        public String object;
        public List<FieldExplain> fields = new ArrayList<>();
    }
    public static class ExplainResponse {
        public String rootObject;
        public String rootField;
        public List<ExplainGroup> groups = new ArrayList<>();
    }
}
