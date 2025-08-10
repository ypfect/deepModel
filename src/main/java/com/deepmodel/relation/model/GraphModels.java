package com.deepmodel.relation.model;

import java.util.ArrayList;
import java.util.List;

public class GraphModels {
    public static class Node {
        public String id;
        public String object;
        public String field;
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
}
