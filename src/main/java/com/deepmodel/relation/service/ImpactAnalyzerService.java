package com.deepmodel.relation.service;

import com.deepmodel.relation.dao.BaseappObjectFieldMapper;
import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.GraphModels;
import com.deepmodel.relation.model.WriteBackExpr;
import com.deepmodel.relation.util.ExprUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ImpactAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(ImpactAnalyzerService.class);

    private final BaseappObjectFieldMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            .configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true);

    // 缓存
    private volatile List<BaseappObjectField> allRows = Collections.emptyList();
    private final Map<String, List<BaseappObjectField>> rowsByObject = new ConcurrentHashMap<String, List<BaseappObjectField>>();

    public ImpactAnalyzerService(BaseappObjectFieldMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    public void loadCache() {
        reload();
    }

    public synchronized void reload() {
        List<BaseappObjectField> rows = mapper.selectAll();
        Map<String, List<BaseappObjectField>> byObj = rows.stream().collect(Collectors.groupingBy(BaseappObjectField::getObjectType));
        rowsByObject.clear();
        rowsByObject.putAll(byObj);
        allRows = rows;
        log.info("缓存加载完成: 对象数={}, 记录数={}", rowsByObject.size(), allRows.size());
    }

    private String canonicalFieldName(BaseappObjectField r){
        if(r.getApiName()!=null && !r.getApiName().trim().isEmpty()) return r.getApiName().trim();
        return r.getName()!=null ? r.getName().trim() : null;
    }

    // 新增：获取字段元数据
    public BaseappObjectField getFieldInfo(String objectType, String fieldCamel){
        List<BaseappObjectField> rows = rowsByObject.get(objectType);
        if(rows == null) return null;
        for(BaseappObjectField r : rows){
            String camel = canonicalFieldName(r);
            if(camel != null && camel.equals(fieldCamel)){
                return r;
            }
        }
        return null;
    }

    private void fillNodeMeta(GraphModels.Node node){
        BaseappObjectField info = getFieldInfo(node.object, node.field);
        if(info != null){
            node.title = info.getTitle();
            node.type = info.getType();
            node.bizType = info.getBizType();
            node.apiName = canonicalFieldName(info);
            node.expression = info.getExpression();
            node.triggerExpr = info.getTriggerExpr();
            node.virtualExpr = info.getVirtualExpr();
        }
    }

    private List<String> collectCamelRefs(BaseappObjectField row){
        Set<String> refs = new HashSet<String>();
        if(row.getTriggerExpr()!=null) refs.addAll(ExprUtils.extractCamelFieldsFromSql(row.getTriggerExpr()));
        if(row.getExpression()!=null) refs.addAll(ExprUtils.extractCamelFieldsFromSql(row.getExpression()));
        if(row.getVirtualExpr()!=null) refs.addAll(ExprUtils.extractCamelFieldsFromSql(row.getVirtualExpr()));
        return new ArrayList<String>(refs);
    }

    private boolean writebackHitsCurrentObject(WriteBackExpr wb, String currentObject){
        if(wb==null) return false;
        if(currentObject.equals(wb.getSrcObjectType())) return true;
        String cond = wb.getCondition();
        if(cond!=null && cond.contains("srcItemObjectType='"+currentObject+"'")) return true;
        return false;
    }

    private static String optText(JsonNode n, String field){
        if(n==null) return null;
        JsonNode v = n.get(field);
        return (v!=null && !v.isNull()) ? v.asText() : null;
    }

    private WriteBackExpr parseWriteBack(String text){
        if(text==null || text.trim().isEmpty()) return null;
        String raw = text.trim();
        try{
            WriteBackExpr wb = objectMapper.readValue(raw, WriteBackExpr.class);
            if(wb!=null && wb.getSrcObjectType()!=null && wb.getExpression()!=null){
                return wb;
            }
        }catch(Exception ignored){ }
        try{
            JsonNode node;
            try{
                node = objectMapper.readTree(raw);
            }catch(Exception e){
                node = objectMapper.readTree(raw.replace('\'', '"'));
            }
            if(node==null) return null;
            java.util.function.Function<JsonNode, WriteBackExpr> pick = (jn) -> {
                String src = optText(jn, "srcObjectType");
                String expr = optText(jn, "expression");
                String cond = optText(jn, "condition");
                if(src!=null && expr!=null){
                    WriteBackExpr wb = new WriteBackExpr();
                    wb.setSrcObjectType(src);
                    wb.setExpression(expr);
                    wb.setCondition(cond);
                    return wb;
                }
                return null;
            };
            if(node.isArray()){
                for(JsonNode it : node){
                    WriteBackExpr wb = pick.apply(it);
                    if(wb!=null) return wb;
                }
                return null;
            }else{
                return pick.apply(node);
            }
        }catch(Exception e){
            log.debug("writeBackExpr 解析失败，原始: {}", text);
            return null;
        }
    }

    private List<Map.Entry<String,String>> buildIntraDependencies(String objectType, String sourceFieldCamel){
        List<BaseappObjectField> rows = rowsByObject.getOrDefault(objectType, Collections.<BaseappObjectField>emptyList());
        List<Map.Entry<String,String>> out = new ArrayList<Map.Entry<String,String>>();
        for(BaseappObjectField r: rows){
            String targetCamel = canonicalFieldName(r);
            if(targetCamel==null) continue;
            if(targetCamel.equals(sourceFieldCamel)) continue;
            List<String> refs = collectCamelRefs(r);
            if(refs.contains(sourceFieldCamel)){
                out.add(new AbstractMap.SimpleEntry<String,String>(objectType, targetCamel));
            }
        }
        log.info("intra: {}.{} -> {} 条", objectType, sourceFieldCamel, out.size());
        return out;
    }

    private List<String> buildIntraUpstreamDependencies(String objectType, String targetFieldCamel){
        List<BaseappObjectField> rows = rowsByObject.getOrDefault(objectType, Collections.<BaseappObjectField>emptyList());
        for(BaseappObjectField r : rows){
            String camel = canonicalFieldName(r);
            if(camel == null) continue;
            if(camel.equals(targetFieldCamel)){
                Set<String> refs = new HashSet<String>();
                if(r.getTriggerExpr()!=null) refs.addAll(ExprUtils.extractCamelFieldsFromSql(r.getTriggerExpr()));
                if(r.getExpression()!=null)   refs.addAll(ExprUtils.extractCamelFieldsFromSql(r.getExpression()));
                if(r.getVirtualExpr()!=null)  refs.addAll(ExprUtils.extractCamelFieldsFromSql(r.getVirtualExpr()));
                refs.remove(targetFieldCamel);
                List<String> list = new ArrayList<String>(refs);
                log.info("upstream: {}.{} <- {} 条", objectType, targetFieldCamel, list.size());
                return list;
            }
        }
        log.info("upstream: {}.{} <- 0 条", objectType, targetFieldCamel);
        return Collections.<String>emptyList();
    }

    private List<Map.Entry<String,String>> buildCrossObjectDependencies(String objectType, String sourceFieldCamel){
        List<Map.Entry<String,String>> out = new ArrayList<Map.Entry<String,String>>();
        int scanned = 0;
        for(BaseappObjectField r: allRows){
            scanned++;
            WriteBackExpr wb = parseWriteBack(r.getWriteBackExpr());
            if(!writebackHitsCurrentObject(wb, objectType)) continue;
            String expr = (wb!=null)? wb.getExpression(): null;
            Set<String> refs = ExprUtils.extractCamelFieldsFromSql(expr);
            if(refs.contains(sourceFieldCamel)){
                String dstObj = r.getObjectType();
                if(objectType.equals(dstObj)) continue;
                String dstFld = canonicalFieldName(r);
                if(dstFld!=null) out.add(new AbstractMap.SimpleEntry<String,String>(dstObj, dstFld));
            }
        }
        log.info("writeBack: candidates(scanned)={}, hitEdges={}", scanned, out.size());
        return out;
    }

    private boolean addEdgeIfAbsent(List<GraphModels.Edge> edges, Set<String> edgeSet, String src, String dst, String type){
        String key = src + "|" + type + "|" + dst;
        if(edgeSet.contains(key)) return false;
        edgeSet.add(key);
        edges.add(new GraphModels.Edge(src, dst, type));
        return true;
    }

    public GraphModels.Graph analyze(String objectType, String fieldCamel, int depth, int relType){
        // 兼容旧调用：默认包含上游展开
        return analyze(objectType, fieldCamel, depth, relType, true);
    }

    /**
     * 分析依赖图
     * @param includeUpstream 是否包含上游展开（上游 -> 当前，再继续向外扩）。
     *                        当为 false 时，只走从根出发的下游影响链，统计口径会与 explain 页面一致。
     */
    public GraphModels.Graph analyze(String objectType, String fieldCamel, int depth, int relType, boolean includeUpstream){
        boolean includeWriteBack = (relType == 0 || relType == 1);
        boolean includeIntra = (relType == 0 || relType == 2);

        log.info("analyze start object={}, field={}, depth={}, includeWB={}, includeIntra={}", objectType, fieldCamel, depth, includeWriteBack, includeIntra);
        GraphModels.Graph g = new GraphModels.Graph();
        Set<String> nodeSet = new HashSet<String>();
        List<GraphModels.Edge> edges = g.edges;
        Set<String> edgeSet = new HashSet<String>();

        Deque<String> q = new ArrayDeque<String>();
        Map<String,Integer> level = new HashMap<String,Integer>();

        String startId = objectType+"."+fieldCamel;
        q.offer(startId); level.put(startId, 0);
        nodeSet.add(startId);
        GraphModels.Node startNode = new GraphModels.Node(objectType, fieldCamel);
        fillNodeMeta(startNode);
        g.nodes.add(startNode);

        while(!q.isEmpty()){
            String cur = q.poll();
            int d = level.get(cur);
            if(d>=depth) continue;
            int dot = cur.indexOf('.');
            String obj = cur.substring(0,dot);
            String fld = cur.substring(dot+1);

            if(includeIntra){
                for(Map.Entry<String,String> e : buildIntraDependencies(obj, fld)){
                    String nid = e.getKey()+"."+e.getValue();
                    addEdgeIfAbsent(edges, edgeSet, cur, nid, "intra");
                    if(!nodeSet.contains(nid)){
                        nodeSet.add(nid);
                        GraphModels.Node n = new GraphModels.Node(e.getKey(), e.getValue());
                        fillNodeMeta(n);
                        g.nodes.add(n);
                        q.offer(nid); level.put(nid, d+1);
                    }
                }
            }
            if(includeWriteBack){
                for(Map.Entry<String,String> e : buildCrossObjectDependencies(obj, fld)){
                    String nid = e.getKey()+"."+e.getValue();
                    addEdgeIfAbsent(edges, edgeSet, cur, nid, "writeBack");
                    if(!nodeSet.contains(nid)){
                        nodeSet.add(nid);
                        GraphModels.Node n = new GraphModels.Node(e.getKey(), e.getValue());
                        fillNodeMeta(n);
                        g.nodes.add(n);
                        q.offer(nid); level.put(nid, d+1);
                    }
                }
            }
            if(includeUpstream){
                List<String> upstream = buildIntraUpstreamDependencies(obj, fld);
                if(includeIntra){
                    for(String upstreamFld : upstream){
                        String uId = obj+"."+upstreamFld;
                        addEdgeIfAbsent(edges, edgeSet, uId, cur, "intra");
                        if(!nodeSet.contains(uId)){
                            nodeSet.add(uId);
                            GraphModels.Node n = new GraphModels.Node(obj, upstreamFld);
                            fillNodeMeta(n);
                            g.nodes.add(n);
                            q.offer(uId); level.put(uId, d+1);
                        }
                    }
                }else if(includeWriteBack){
                    for(String upstreamFld : upstream){
                        String uId = obj+"."+upstreamFld;
                        if(!nodeSet.contains(uId)){
                            nodeSet.add(uId);
                            GraphModels.Node n = new GraphModels.Node(obj, upstreamFld);
                            fillNodeMeta(n);
                            g.nodes.add(n);
                            q.offer(uId); level.put(uId, d+1);
                        }
                    }
                }
            }
        }
        log.info("analyze finish nodes={}, edges={}", g.nodes.size(), g.edges.size());
        return g;
    }

    // ===== 解释：按对象分组列出受影响字段及推导路径 =====
    public GraphModels.ExplainResponse explain(String objectType, String fieldCamel, int depth, int relType){
        GraphModels.Graph g = analyze(objectType, fieldCamel, depth, relType);
        // 建立邻接与反向索引
        Map<String, List<GraphModels.Edge>> outEdges = new LinkedHashMap<String, List<GraphModels.Edge>>();
        Map<String, List<GraphModels.Edge>> inEdges  = new LinkedHashMap<String, List<GraphModels.Edge>>();
        for(GraphModels.Edge e : g.edges){
            outEdges.computeIfAbsent(e.source, k-> new ArrayList<GraphModels.Edge>()).add(e);
            inEdges.computeIfAbsent(e.target, k-> new ArrayList<GraphModels.Edge>()).add(e);
        }
        String root = objectType + "." + fieldCamel;
        // 反向 BFS 求最短路径树（从每个节点回溯到 root）
        Deque<String> q = new ArrayDeque<String>();
        q.offer(root);
        Map<String,String> prev = new HashMap<String,String>(); // prev[v] = u 表示 u -> v
        Map<String,GraphModels.Edge> prevEdge = new HashMap<String,GraphModels.Edge>();
        Set<String> visited = new HashSet<String>(); visited.add(root);
        while(!q.isEmpty()){
            String u = q.poll();
            List<GraphModels.Edge> outs = outEdges.getOrDefault(u, Collections.<GraphModels.Edge>emptyList());
            for(GraphModels.Edge e : outs){
                String v = e.target;
                if(!visited.contains(v)){
                    visited.add(v); prev.put(v, u); prevEdge.put(v, e); q.offer(v);
                }
            }
        }
        // 将终点按对象分组（排除 root 本身）
        Map<String, GraphModels.ExplainGroup> groups = new LinkedHashMap<String, GraphModels.ExplainGroup>();
        for(GraphModels.Node n : g.nodes){
            if((n.object + "." + n.field).equals(root)) continue;
            // 只收录能从 root 到达的节点
            if(!prev.containsKey(n.object + "." + n.field) && !root.equals(n.object + "." + n.field)){
                // 若没有直接 prev，但也可能与 root 同层（无边），此类不纳入解释
                continue;
            }
            GraphModels.ExplainGroup grp = groups.get(n.object);
            if(grp == null){ grp = new GraphModels.ExplainGroup(); grp.object = n.object; groups.put(n.object, grp); }
            // 还原路径
            List<GraphModels.ExplainStep> steps = new ArrayList<GraphModels.ExplainStep>();
            String cur = n.object + "." + n.field;
            while(prev.containsKey(cur)){
                GraphModels.Edge e = prevEdge.get(cur);
                String src = e.source, dst = e.target;
                String reason;
                if("intra".equals(e.type)){
                    // 取目标字段的表达式，说明其依赖来源（避免与目标字段重复展示）
                    int dot = dst.indexOf('.'); String obj = dst.substring(0,dot); String fld = dst.substring(dot+1);
                    BaseappObjectField info = getFieldInfo(obj, fld);
                    String expr = info!=null? (info.getTriggerExpr()!=null? info.getTriggerExpr(): (info.getExpression()!=null? info.getExpression(): info.getVirtualExpr())) : null;
                    String srcField = src.substring(src.indexOf('.')+1);
                    reason = expr!=null? ("由表达式计算，包含 " + srcField) : ("依赖 " + srcField);
                }else{
                    // writeBack：根据写回表达式（避免与目标字段重复展示）
                    int dot = dst.indexOf('.'); String obj = dst.substring(0,dot); String fld = dst.substring(dot+1);
                    BaseappObjectField info = getFieldInfo(obj, fld);
                    String raw = info!=null? info.getWriteBackExpr(): null;
                    WriteBackExpr wb = raw!=null? parseWriteBack(raw): null;
                    String expr = wb!=null? wb.getExpression(): null;
                    String srcField = src.substring(src.indexOf('.')+1);
                    reason = (expr!=null? ("由回写表达式聚合/计算，包含 " + srcField) : ("回写依赖 " + srcField));
                }
                steps.add(new GraphModels.ExplainStep(e.type, src, dst, reason));
                cur = prev.get(cur);
            }
            // 反转为 root -> target 的顺序
            Collections.reverse(steps);
            GraphModels.FieldExplain fe = new GraphModels.FieldExplain();
            fe.object = n.object; fe.field = n.field; fe.steps = steps;
            fe.summary = n.object + "." + n.field + " 受 " + root + " 影响，路径长度 " + steps.size();
            grp.fields.add(fe);
        }
        GraphModels.ExplainResponse resp = new GraphModels.ExplainResponse();
        resp.rootObject = objectType; resp.rootField = fieldCamel; resp.groups = new ArrayList<GraphModels.ExplainGroup>(groups.values());
        return resp;
    }

    public GraphModels.ObjectGraph analyzeObjects(String objectType, String fieldCamel, int depth, int relType){
        GraphModels.Graph g = analyze(objectType, fieldCamel, depth, relType);
        Map<String, Set<String>> objToFields = new LinkedHashMap<String, Set<String>>();
        for(GraphModels.Node n : g.nodes){
            objToFields.computeIfAbsent(n.object, k-> new LinkedHashSet<String>()).add(n.field);
        }
        Map<String, Integer> objFieldCount = new LinkedHashMap<String, Integer>();
        for(Map.Entry<String, Set<String>> e : objToFields.entrySet()){
            objFieldCount.put(e.getKey(), e.getValue().size());
        }
        Map<String, Integer> agg = new LinkedHashMap<String, Integer>();
        for(GraphModels.Edge e : g.edges){
            String sObj = e.source.substring(0, e.source.indexOf('.'));
            String tObj = e.target.substring(0, e.target.indexOf('.'));
            String key = sObj + "|" + e.type + "|" + tObj;
            agg.put(key, agg.getOrDefault(key, 0) + 1);
        }
        GraphModels.ObjectGraph og = new GraphModels.ObjectGraph();
        for(Map.Entry<String,Integer> e : objFieldCount.entrySet()){
            og.nodes.add(new GraphModels.ObjectNode(e.getKey(), e.getValue()));
        }
        for(Map.Entry<String,Integer> e : agg.entrySet()){
            String[] parts = e.getKey().split("\\|");
            og.edges.add(new GraphModels.ObjectEdge(parts[0], parts[2], parts[1], e.getValue()));
        }
        return og;
    }

    /**
     * 返回对象间连线对应的字段级边明细（sourceObject -> targetObject，指定关系类型）。
     */
    public List<GraphModels.Edge> objectEdgeDetails(String objectType, String fieldCamel, int depth, int relType,
                                                    String sourceObject, String targetObject, String type){
        GraphModels.Graph g = analyze(objectType, fieldCamel, depth, relType);
        List<GraphModels.Edge> out = new ArrayList<GraphModels.Edge>();
        for(GraphModels.Edge e : g.edges){
            if(type!=null && !type.equals(e.type)) continue;
            String sObj = e.source.substring(0, e.source.indexOf('.'));
            String tObj = e.target.substring(0, e.target.indexOf('.'));
            if(sObj.equals(sourceObject) && tObj.equals(targetObject)){
                out.add(e);
            }
        }
        return out;
    }
}
