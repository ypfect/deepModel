package com.deepmodel.relation.service;

import com.deepmodel.relation.dao.BaseappObjectFieldMapper;
import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.GraphModels;
import com.deepmodel.relation.model.WriteBackExpr;
import com.deepmodel.relation.util.ExprUtils;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private WriteBackExpr parseWriteBack(String text){
        if(text==null || text.trim().isEmpty()) return null;
        try{
            return objectMapper.readValue(text, WriteBackExpr.class);
        }catch(Exception e){
            try{
                String fixed = text.replace('\'', '"');
                return objectMapper.readValue(fixed, WriteBackExpr.class);
            }catch(Exception ignored){
                log.debug("writeBackExpr 解析失败，原始: {}", text);
                return null;
            }
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
                // 规则：回写只视为跨对象影响，若目标对象与源对象相同则跳过
                if(objectType.equals(dstObj)) continue;
                String dstFld = canonicalFieldName(r);
                if(dstFld!=null) out.add(new AbstractMap.SimpleEntry<String,String>(dstObj, dstFld));
            }
        }
        log.info("writeBack: candidates(scanned)={}, hitEdges={}", scanned, out.size());
        return out;
    }

    public GraphModels.Graph analyze(String objectType, String fieldCamel, int depth){
        log.info("analyze start object={}, field={}, depth={}", objectType, fieldCamel, depth);
        GraphModels.Graph g = new GraphModels.Graph();
        Set<String> nodeSet = new HashSet<String>();
        List<GraphModels.Edge> edges = g.edges;

        Deque<String> q = new ArrayDeque<String>();
        Map<String,Integer> level = new HashMap<String,Integer>();

        String startId = objectType+"."+fieldCamel;
        q.offer(startId); level.put(startId, 0);
        nodeSet.add(startId); g.nodes.add(new GraphModels.Node(objectType, fieldCamel));

        while(!q.isEmpty()){
            String cur = q.poll();
            int d = level.get(cur);
            if(d>=depth) continue;
            int dot = cur.indexOf('.');
            String obj = cur.substring(0,dot);
            String fld = cur.substring(dot+1);

            for(Map.Entry<String,String> e : buildIntraDependencies(obj, fld)){
                String nid = e.getKey()+"."+e.getValue();
                edges.add(new GraphModels.Edge(cur, nid, "intra"));
                if(!nodeSet.contains(nid)){
                    nodeSet.add(nid);
                    g.nodes.add(new GraphModels.Node(e.getKey(), e.getValue()));
                    q.offer(nid); level.put(nid, d+1);
                }
            }
            for(Map.Entry<String,String> e : buildCrossObjectDependencies(obj, fld)){
                String nid = e.getKey()+"."+e.getValue();
                edges.add(new GraphModels.Edge(cur, nid, "writeBack"));
                if(!nodeSet.contains(nid)){
                    nodeSet.add(nid);
                    g.nodes.add(new GraphModels.Node(e.getKey(), e.getValue()));
                    q.offer(nid); level.put(nid, d+1);
                }
            }
        }
        log.info("analyze finish nodes={}, edges={}", g.nodes.size(), g.edges.size());
        return g;
    }
}
