package com.deepmodel.relation.controller;

import com.deepmodel.relation.model.GraphModels;
import com.deepmodel.relation.service.ImpactAnalyzerService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
public class ImpactController {

    private final ImpactAnalyzerService analyzerService;

    public ImpactController(ImpactAnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
    }

    @GetMapping("/api/reload")
    public String reload(){
        analyzerService.reload();
        return "ok";
    }

    @GetMapping("/api/impact")
    public GraphModels.Graph impact(@RequestParam("objectType") String objectType,
                                    @RequestParam("field") String field,
                                    @RequestParam(value = "depth", defaultValue = "3") int depth){
        return analyzerService.analyze(objectType, field, depth);
    }

    private static String safeId(String id){
        if(id == null) return "";
        return id.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String toMermaid(GraphModels.Graph g){
        StringBuilder sb = new StringBuilder();
        sb.append("graph TD\n");
        Map<String, List<GraphModels.Node>> byObj = new LinkedHashMap<String, List<GraphModels.Node>>();
        for(GraphModels.Node n: g.nodes){
            if(!byObj.containsKey(n.object)) byObj.put(n.object, new ArrayList<GraphModels.Node>());
            byObj.get(n.object).add(n);
        }
        // 节点映射：原始ID -> 安全ID
        Map<String, String> idMap = new HashMap<String, String>();
        for(GraphModels.Node n: g.nodes){
            String raw = n.id; // e.g., ArReceiptItem.originAmount
            String sid = safeId(raw);
            idMap.put(raw, sid);
        }
        for(Map.Entry<String,List<GraphModels.Node>> e: byObj.entrySet()){
            sb.append("  subgraph ").append(e.getKey()).append("\n");
            for(GraphModels.Node n : e.getValue()){
                String raw = n.id;
                String sid = idMap.get(raw);
                String label = raw; // 显示完整对象.字段
                sb.append("    ").append(sid).append("[").append(label.replace("\\", "\\\\").replace("]","\\]")).append("]\n");
            }
            sb.append("  end\n");
        }
        for(GraphModels.Edge e : g.edges){
            String s = idMap.get(e.source);
            String t = idMap.get(e.target);
            if(s==null || t==null) continue;
            sb.append("  ").append(s)
              .append(" -- ").append(e.type)
              .append(" --> ").append(t).append("\n");
        }
        return sb.toString();
    }

    @GetMapping(value = "/api/impact/mermaid", produces = MediaType.TEXT_PLAIN_VALUE)
    public String mermaid(@RequestParam("objectType") String objectType,
                          @RequestParam("field") String field,
                          @RequestParam(value = "depth", defaultValue = "3") int depth){
        GraphModels.Graph g = analyzerService.analyze(objectType, field, depth);
        return toMermaid(g);
    }

    @GetMapping(value = "/api/impact/mermaidPage", produces = MediaType.TEXT_HTML_VALUE)
    public String mermaidPage(@RequestParam("objectType") String objectType,
                              @RequestParam("field") String field,
                              @RequestParam(value = "depth", defaultValue = "3") int depth){
        GraphModels.Graph g = analyzerService.analyze(objectType, field, depth);
        String mer = toMermaid(g);
        return "<!doctype html><html><head><meta charset=\"utf-8\"/>" +
                "<title>Mermaid</title>" +
                "<script src=\"https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js\"></script>" +
                "<script>mermaid.initialize({startOnLoad:true});</script>" +
                "</head><body><div class=\"mermaid\">" + mer.replace("<","&lt;") + "</div></body></html>";
    }

    @GetMapping(value = "/api/impact/dot", produces = MediaType.TEXT_PLAIN_VALUE)
    public String dot(@RequestParam("objectType") String objectType,
                      @RequestParam("field") String field,
                      @RequestParam(value = "depth", defaultValue = "3") int depth){
        GraphModels.Graph g = analyzerService.analyze(objectType, field, depth);
        StringBuilder sb = new StringBuilder();
        sb.append("digraph G {\n  rankdir=LR;\n  node [shape=box, style=rounded];\n");
        for(GraphModels.Node n : g.nodes){
            sb.append("  \"").append(n.id.replace("\"","\\\""))
              .append("\";\n");
        }
        for(GraphModels.Edge e : g.edges){
            sb.append("  \"").append(e.source.replace("\"","\\\""))
              .append("\" -> \"")
              .append(e.target.replace("\"","\\\""))
              .append("\" [label=\"").append(e.type).append("\"];\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
