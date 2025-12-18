package com.deepmodel.relation.controller;

import com.deepmodel.relation.model.BaseappObjectField;
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

    @GetMapping("/api/impact/meta/objects")
    public Set<String> getObjects() {
        return analyzerService.getAllObjectTypes();
    }

    @GetMapping("/api/impact/meta/fields")
    public List<String> getFields(@RequestParam("objectType") String objectType) {
        return analyzerService.getFieldsForObject(objectType);
    }

    // 新增：字段详情
    @GetMapping("/api/impact/fieldInfo")
    public BaseappObjectField fieldInfo(@RequestParam("objectType") String objectType,
                                        @RequestParam("field") String field){
        return analyzerService.getFieldInfo(objectType, field);
    }

    @GetMapping("/api/impact")
    public GraphModels.Graph impact(@RequestParam("objectType") String objectType,
                                    @RequestParam("field") String field,
                                    @RequestParam(value = "depth", defaultValue = "3") int depth,
                                    @RequestParam(value = "relType", defaultValue = "0") int relType,
                                    @RequestParam(value = "direction", defaultValue = "downstream") String direction){
        boolean includeUpstream = !"downstream".equalsIgnoreCase(direction);
        return analyzerService.analyze(objectType, field, depth, relType, includeUpstream);
    }

    /**
     * 批量影响分析接口
     * @param objectType 对象类型
     * @param fields 字段列表，多个字段用逗号分隔
     * @param depth 深度
     * @param relType 关系类型
     * @param direction 方向
     * @return 合并后的分析结果
     */
    @GetMapping("/api/impact/batch")
    public GraphModels.Graph impactBatch(@RequestParam("objectType") String objectType,
                                         @RequestParam("fields") String fields,
                                         @RequestParam(value = "depth", defaultValue = "3") int depth,
                                         @RequestParam(value = "relType", defaultValue = "0") int relType,
                                         @RequestParam(value = "direction", defaultValue = "downstream") String direction){
        boolean includeUpstream = !"downstream".equalsIgnoreCase(direction);
        // 解析字段列表
        List<String> fieldList = new ArrayList<>();
        if (fields != null && !fields.trim().isEmpty()) {
            String[] parts = fields.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    fieldList.add(trimmed);
                }
            }
        }
        if (fieldList.isEmpty()) {
            return new GraphModels.Graph();
        }
        return analyzerService.analyzeBatch(objectType, fieldList, depth, relType, includeUpstream);
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
        Map<String, String> idMap = new HashMap<String, String>();
        for(GraphModels.Node n: g.nodes){
            String raw = n.id;
            String sid = safeId(raw);
            idMap.put(raw, sid);
        }
        for(Map.Entry<String,List<GraphModels.Node>> e: byObj.entrySet()){
            sb.append("  subgraph ").append(e.getKey()).append("\n");
            for(GraphModels.Node n : e.getValue()){
                String raw = n.id; String sid = idMap.get(raw);
                String label = raw;
                sb.append("    ").append(sid).append("[").append(label.replace("\\", "\\\\").replace("]","\\]")).append("]\n");
            }
            sb.append("  end\n");
        }
        for(GraphModels.Edge e : g.edges){
            String s = idMap.get(e.source); String t = idMap.get(e.target);
            if(s==null || t==null) continue;
            sb.append("  ").append(s).append(" -- ").append(e.type).append(" --> ").append(t).append("\n");
        }
        return sb.toString();
    }

    @GetMapping(value = "/api/impact/mermaid", produces = MediaType.TEXT_PLAIN_VALUE)
    public String mermaid(@RequestParam("objectType") String objectType,
                          @RequestParam("field") String field,
                          @RequestParam(value = "depth", defaultValue = "3") int depth,
                          @RequestParam(value = "relType", defaultValue = "0") int relType,
                          @RequestParam(value = "direction", defaultValue = "downstream") String direction){
        boolean includeUpstream = !"downstream".equalsIgnoreCase(direction);
        GraphModels.Graph g = analyzerService.analyze(objectType, field, depth, relType, includeUpstream);
        return toMermaid(g);
    }

    @GetMapping(value = "/api/impact/mermaidPage", produces = MediaType.TEXT_HTML_VALUE)
    public String mermaidPage(@RequestParam("objectType") String objectType,
                              @RequestParam("field") String field,
                              @RequestParam(value = "depth", defaultValue = "3") int depth,
                              @RequestParam(value = "relType", defaultValue = "0") int relType,
                              @RequestParam(value = "direction", defaultValue = "downstream") String direction){
        boolean includeUpstream = !"downstream".equalsIgnoreCase(direction);
        GraphModels.Graph g = analyzerService.analyze(objectType, field, depth, relType, includeUpstream);
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
                      @RequestParam(value = "depth", defaultValue = "3") int depth,
                      @RequestParam(value = "relType", defaultValue = "0") int relType,
                      @RequestParam(value = "direction", defaultValue = "downstream") String direction){
        boolean includeUpstream = !"downstream".equalsIgnoreCase(direction);
        GraphModels.Graph g = analyzerService.analyze(objectType, field, depth, relType, includeUpstream);
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

    @GetMapping("/api/impact/objects")
    public GraphModels.ObjectGraph impactObjects(@RequestParam("objectType") String objectType,
                                                 @RequestParam("field") String field,
                                                 @RequestParam(value = "depth", defaultValue = "3") int depth,
                                                 @RequestParam(value = "relType", defaultValue = "0") int relType){
        return analyzerService.analyzeObjects(objectType, field, depth, relType);
    }

    @GetMapping("/api/impact/objectEdgeDetails")
    public List<GraphModels.Edge> objectEdgeDetails(@RequestParam("objectType") String objectType,
                                                    @RequestParam("field") String field,
                                                    @RequestParam(value = "depth", defaultValue = "3") int depth,
                                                    @RequestParam(value = "relType", defaultValue = "0") int relType,
                                                    @RequestParam("sourceObject") String sourceObject,
                                                    @RequestParam("targetObject") String targetObject,
                                                    @RequestParam("type") String type){
        return analyzerService.objectEdgeDetails(objectType, field, depth, relType, sourceObject, targetObject, type);
    }

    // ===== 调试接口：查看视图依赖关系 =====
    @GetMapping("/api/debug/viewDeps")
    public Map<String, Object> debugViewDeps() {
        return analyzerService.getViewDependenciesDebugInfo();
    }

    // ===== 解释接口 =====
    @GetMapping("/api/impact/explain")
    public GraphModels.ExplainResponse explain(@RequestParam("objectType") String objectType,
                                               @RequestParam("field") String field,
                                               @RequestParam(value = "depth", defaultValue = "3") int depth,
                                               @RequestParam(value = "relType", defaultValue = "0") int relType){
        return analyzerService.explain(objectType, field, depth, relType);
    }

    @GetMapping(value = "/api/impact/explainPage", produces = MediaType.TEXT_HTML_VALUE)
    public String explainPage(){
        return "<!doctype html><html lang=\"zh\"><head><meta charset=\"utf-8\"/>"+
                "<title>按对象分组的解释</title>"+
                "<style>body{font-family:Arial;margin:0}#toolbar{padding:10px 12px;background:#f5f7fa;border-bottom:1px solid #e5e7eb;display:flex;gap:8px;align-items:center;flex-wrap:wrap}#content{padding:12px}details{border:1px solid #e5e7eb;border-radius:8px;margin:10px 0;background:#fff}summary{cursor:pointer;padding:8px 12px;font-weight:bold;background:#f9fafb}ul{margin:8px 16px;padding-left:18px}code{background:#f3f4f6;padding:2px 6px;border-radius:4px}</style>"+
                "</head><body>"+
                "<div id=\"toolbar\">"+
                "对象 <input id=\"obj\" value=\"ArReceiptItem\"/>"+
                " 字段 <input id=\"fld\" value=\"originAmount\"/>"+
                " 深度 <input id=\"dep\" type=\"number\" value=\"3\" min=\"1\" max=\"6\"/>"+
                " 关系类型 <select id=\"rel\"><option value=\"0\" selected>全部</option><option value=\"1\">回写</option><option value=\"2\">触发</option></select>"+
                " <button id=\"btn\">查询</button>"+
                "</div>"+
                "<div id=\"content\"></div>"+
                "<script>(function(){\n"+
                "async function load(){ const o=document.getElementById('obj').value.trim(); const f=document.getElementById('fld').value.trim(); const d=parseInt(document.getElementById('dep').value||'3',10); const r=parseInt(document.getElementById('rel').value||'0',10); const url=`/api/impact/explain?objectType=${encodeURIComponent(o)}&field=${encodeURIComponent(f)}&depth=${encodeURIComponent(d)}&relType=${encodeURIComponent(r)}`; const resp = await fetch(url); if(!resp.ok){ alert('请求失败'); return; } const data = await resp.json(); render(data);}\n"+
                "function esc(s){ return (s||'').replace(/[&<>\"]/g, c=>({ '&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;' }[c])); }\n"+
                "function stepLine(s){ return `<li><code>${esc(s.source)}</code> -- <b>${esc(s.type)}</b> --> <code>${esc(s.target)}</code> · ${esc(s.reason||'')}</li>`; }\n"+
                "function render(data){ const ct = document.getElementById('content'); ct.innerHTML=''; if(!data||!data.groups||data.groups.length===0){ ct.textContent='无结果'; return; } const header=document.createElement('div'); header.style.margin='6px 2px'; header.innerHTML=`根：<code>${esc(data.rootObject)}.${esc(data.rootField)}</code>`; ct.appendChild(header); data.groups.forEach(g=>{ const det=document.createElement('details'); det.open=true; const sum=document.createElement('summary'); sum.textContent=g.object; det.appendChild(sum); (g.fields||[]).forEach(fe=>{ const h=document.createElement('div'); h.style.padding='8px 12px'; const title=document.createElement('div'); title.style.fontWeight='bold'; title.textContent=fe.object + '.' + fe.field; const ul=document.createElement('ul'); (fe.steps||[]).forEach(st=>{ const li=document.createElement('li'); li.innerHTML = stepLine(st); ul.appendChild(li); }); const sm=document.createElement('div'); sm.style.color='#666'; sm.style.marginTop='6px'; sm.textContent = fe.summary||''; h.appendChild(title); h.appendChild(ul); h.appendChild(sm); det.appendChild(h); }); ct.appendChild(det); }); }\n"+
                "document.getElementById('btn').onclick=load; load();\n"+
                "})();</script>"+
                "</body></html>";
    }

    @GetMapping(value = "/api/impact/objectsPage", produces = MediaType.TEXT_HTML_VALUE)
    public String impactObjectsPage(){
        return "<!doctype html><html lang=\"zh\"><head><meta charset=\"utf-8\"/>"+
                "<title>对象级影响图</title>"+
                "<style>body{font-family:Arial;margin:0}#toolbar{padding:10px 12px;background:#f5f7fa;border-bottom:1px solid #e5e7eb;display:flex;gap:8px;align-items:center;flex-wrap:wrap}#objFilters{max-height:84px;overflow:auto;padding:6px 10px;border-top:1px solid #e5e7eb;background:#fafafa;display:flex;flex-wrap:wrap;gap:12px;align-items:center}#cy{width:100%;height:calc(100vh - 112px)}label{margin-right:6px}</style>"+
                "<script src=\"https://cdn.jsdelivr.net/npm/cytoscape@3.28.0/dist/cytoscape.min.js\"></script>"+
                "</head><body>"+
                "<div id=\"toolbar\">"+
                "对象 <input id=\"obj\" value=\"ArReceipt\"/>"+
                " 字段 <input id=\"fld\" value=\"originAmount\"/>"+
                " 深度 <input id=\"dep\" type=\"number\" value=\"3\" min=\"1\" max=\"6\"/>"+
                " 关系类型 <select id=\"rel\"><option value=\"0\" selected>全部</option><option value=\"1\">回写</option><option value=\"2\">触发</option></select>"+
                " <button id=\"btn\">查询</button>"+
                " <span style=\"margin-left:auto;font-size:12px;color:#666\">对象过滤：下方勾选</span>"+
                "</div>"+
                "<div id=\"objFilters\"></div>"+
                "<div id=\"cy\"></div>"+
                "<script>(function(){\n"+
                "const cy = cytoscape({ container: document.getElementById('cy'), elements: [], style: [\n"+
                "{ selector: 'node', style: { 'shape':'round-rectangle','background-color':'#f0f9ff','border-color':'#0ea5e9','border-width':1,'label':'data(label)','padding':10,'text-wrap':'wrap','text-max-width':120 } },\n"+
                "{ selector: 'edge', style: { 'width':2,'curve-style':'bezier','target-arrow-shape':'triangle','label':ele=>ele.data('type')+'('+ele.data('count')+')','font-size':10,'text-rotation':'autorotate','line-color':ele=>ele.data('type')==='writeBack'?'#16a34a':'#2563eb','target-arrow-color':ele=>ele.data('type')==='writeBack'?'#16a34a':'#2563eb' } },\n"+
                "{ selector: '.hiddenObj', style: { 'display':'none' } }\n"+
                "], layout: { name: 'breadthfirst', directed:true, padding: 20 } });\n"+
                "function color(obj){ let h=0; for(let i=0;i<obj.length;i++){ h=(h*31+obj.charCodeAt(i))>>>0;} h%=360; return `hsl(${h},70%,90%)`; }\n"+
                "function toElements(data){ const es=[]; const seenN=new Set(); const seenE=new Set(); (data.nodes||[]).forEach(n=>{ if(seenN.has(n.object)) return; seenN.add(n.object); es.push({ data:{ id:n.object, label: n.object + ' ('+n.fieldCount+')' }, style:{ 'background-color': color(n.object) } }); }); (data.edges||[]).forEach(e=>{ const id=e.sourceObject+'->'+e.type+'->'+e.targetObject; if(seenE.has(id)) return; seenE.add(id); es.push({ data:{ id, source:e.sourceObject, target:e.targetObject, type:e.type, count:e.count } }); }); return es; }\n"+
                "function rebuildObjectFilters(){ const panel = document.getElementById('objFilters'); panel.innerHTML=''; const objs = cy.nodes().map(n=>n.id()).sort(); if(objs.length===0){ panel.textContent='无对象可过滤'; return; } const btnAll=document.createElement('button'); btnAll.textContent='全选'; btnAll.onclick=()=>{ setAll(true); }; const btnNone=document.createElement('button'); btnNone.textContent='全不选'; btnNone.onclick=()=>{ setAll(false); }; panel.appendChild(btnAll); panel.appendChild(btnNone); objs.forEach(obj=>{ const label=document.createElement('label'); const ck=document.createElement('input'); ck.type='checkbox'; ck.value=obj; ck.checked=true; ck.onchange=applyObjectFilters; label.appendChild(ck); label.appendChild(document.createTextNode(' '+obj)); panel.appendChild(label); }); }\n"+
                "function setAll(val){ document.querySelectorAll('#objFilters input[type=checkbox]').forEach(ck=>{ ck.checked=val; }); applyObjectFilters(); }\n"+
                "function applyObjectFilters(){ const allowed=new Set(); document.querySelectorAll('#objFilters input[type=checkbox]').forEach(ck=>{ if(ck.checked) allowed.add(ck.value); }); cy.batch(()=>{ cy.nodes().forEach(n=>{ if(allowed.has(n.id())) n.removeClass('hiddenObj'); else n.addClass('hiddenObj'); }); cy.edges().forEach(e=>{ const sHidden=e.source().hasClass('hiddenObj'); const tHidden=e.target().hasClass('hiddenObj'); if(sHidden||tHidden) e.addClass('hiddenObj'); else e.removeClass('hiddenObj'); }); }); }\n"+
                "async function load(){ const o=document.getElementById('obj').value.trim(); const f=document.getElementById('fld').value.trim(); const d=parseInt(document.getElementById('dep').value||'3',10); const r=parseInt(document.getElementById('rel').value||'0',10); const resp = await fetch(`/api/impact/objects?objectType=${encodeURIComponent(o)}&field=${encodeURIComponent(f)}&depth=${encodeURIComponent(d)}&relType=${encodeURIComponent(r)}`); const data = await resp.json(); cy.elements().remove(); cy.add(toElements(data)); cy.layout({ name:'breadthfirst', directed:true, padding:20 }).run(); rebuildObjectFilters(); applyObjectFilters(); }\n"+
                "document.getElementById('btn').onclick=load; load();\n"+
                "})();</script>"+
                "</body></html>";
    }
}
