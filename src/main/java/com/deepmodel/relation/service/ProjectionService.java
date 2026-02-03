package com.deepmodel.relation.service;

import com.deepmodel.relation.dao.ProjectionMapper;
import com.deepmodel.relation.model.ProjectionModels.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 单据映射关系业务逻辑服务
 */
@Service
public class ProjectionService {

    @Autowired
    private ProjectionMapper projectionMapper;

    /**
     * 获取映射关系图谱（全局视图）
     */
    public ProjectionGraph getProjectionGraph() {
        ProjectionGraph graph = new ProjectionGraph();
        
        // 查询所有映射关系
        List<Projection> projections = projectionMapper.findAllProjections();
        
        // 构建节点集合（去重）
        Set<String> objectSet = new HashSet<>();
        for (Projection p : projections) {
            objectSet.add(p.objectType);
            objectSet.add(p.toObjectType);
        }
        
        // 创建节点
        for (String obj : objectSet) {
            ProjectionNode node = new ProjectionNode();
            node.objectType = obj;
            node.id = obj;
            graph.nodes.add(node);
        }
        
        // 创建边
        for (Projection p : projections) {
            ProjectionEdge edge = new ProjectionEdge(p.objectType, p.toObjectType, p.id);
            
            // 统计子表数量
            int childCount = projectionMapper.countChildren(p.id);
            edge.hasChildren = childCount > 0;
            edge.label = childCount > 0 ? "包含子表" : "";
            
            graph.edges.add(edge);
        }
        
        return graph;
    }

    /**
     * 获取指定对象的映射链路
     * @param objectType 起始对象
     * @param direction upstream/downstream/both
     * @param maxDepth 最大追踪深度
     */
    public ProjectionGraph getObjectChain(String objectType, String direction, int maxDepth) {
        ProjectionGraph graph = new ProjectionGraph();
        Set<String> visitedProjections = new HashSet<>();
        Map<String, Integer> objectDepth = new HashMap<>();
        
        if ("downstream".equals(direction) || "both".equals(direction)) {
            // 下游链路（BFS）
            buildDownstreamChainBFS(objectType, graph, visitedProjections, objectDepth, maxDepth);
        }
        
        if ("upstream".equals(direction) || "both".equals(direction)) {
            // 上游链路（BFS）
            buildUpstreamChainBFS(objectType, graph, visitedProjections, objectDepth, maxDepth);
        }
        
        return graph;
    }

    /**
     * BFS构建下游链路（允许同一对象在不同深度被访问）
     */
    private void buildDownstreamChainBFS(String startObject, ProjectionGraph graph, 
                                         Set<String> visitedProjections,
                                         Map<String, Integer> objectDepth, int maxDepth) {
        Queue<String> queue = new LinkedList<>();
        queue.offer(startObject);
        objectDepth.put(startObject, 0);
        
        System.out.println("开始构建下游链路: " + startObject + ", 最大深度: " + maxDepth);
        
        // 添加起始节点（如果不存在）
        if (graph.nodes.stream().noneMatch(n -> n.objectType.equals(startObject))) {
            ProjectionNode startNode = new ProjectionNode();
            startNode.objectType = startObject;
            startNode.id = startObject;
            graph.nodes.add(startNode);
        }
        
        while (!queue.isEmpty()) {
            String currentObject = queue.poll();
            int currentDepth = objectDepth.get(currentObject);
            
            System.out.println("处理对象: " + currentObject + ", 当前深度: " + currentDepth);
            
            if (currentDepth >= maxDepth) {
                System.out.println("  达到最大深度，跳过");
                continue;
            }
            
            // 查询下游映射
            List<Projection> downstreams = projectionMapper.findDownstreamProjections(currentObject);
            System.out.println("  找到 " + downstreams.size() + " 个下游映射");
            
            for (Projection p : downstreams) {
                // 跳过已处理的映射关系
                if (visitedProjections.contains(p.id)) {
                    System.out.println("    跳过已访问的映射: " + p.objectType + " -> " + p.toObjectType);
                    continue;
                }
                visitedProjections.add(p.id);
                System.out.println("    处理映射: " + p.objectType + " -> " + p.toObjectType);
                
                // 添加目标对象节点（如果不存在）
                if (graph.nodes.stream().noneMatch(n -> n.objectType.equals(p.toObjectType))) {
                    ProjectionNode node = new ProjectionNode();
                    node.objectType = p.toObjectType;
                    node.id = p.toObjectType;
                    // 标记深度信息（用于前端层级展示）
                    node.criteriaStr = "depth:" + (currentDepth + 1);
                    graph.nodes.add(node);
                }
                
                // 添加边
                ProjectionEdge edge = new ProjectionEdge(p.objectType, p.toObjectType, p.id);
                int childCount = projectionMapper.countChildren(p.id);
                edge.hasChildren = childCount > 0;
                edge.label = childCount > 0 ? "包含子表" : "";
                graph.edges.add(edge);
                
                // 将目标对象加入队列（允许重复访问，但要更新深度）
                int targetDepth = currentDepth + 1;
                Integer existingDepth = objectDepth.get(p.toObjectType);
                if (existingDepth == null || targetDepth < existingDepth) {
                    objectDepth.put(p.toObjectType, targetDepth);
                    queue.offer(p.toObjectType);
                    System.out.println("      将 " + p.toObjectType + " 加入队列，深度: " + targetDepth);
                } else {
                    System.out.println("      " + p.toObjectType + " 已访问过，深度: " + existingDepth);
                }
            }
        }
        
        System.out.println("下游链路构建完成: " + graph.nodes.size() + " 个节点, " + graph.edges.size() + " 条边");
    }

    /**
     * BFS构建上游链路（允许同一对象在不同深度被访问）
     */
    private void buildUpstreamChainBFS(String startObject, ProjectionGraph graph,
                                       Set<String> visitedProjections,
                                       Map<String, Integer> objectDepth, int maxDepth) {
        Queue<String> queue = new LinkedList<>();
        queue.offer(startObject);
        if (!objectDepth.containsKey(startObject)) {
            objectDepth.put(startObject, 0);
        }
        
        System.out.println("开始构建上游链路: " + startObject + ", 最大深度: " + maxDepth);
        
        // 添加起始节点（如果不存在）
        if (graph.nodes.stream().noneMatch(n -> n.objectType.equals(startObject))) {
            ProjectionNode startNode = new ProjectionNode();
            startNode.objectType = startObject;
            startNode.id = startObject;
            startNode.criteriaStr = "depth:0";
            graph.nodes.add(startNode);
        }
        
        while (!queue.isEmpty()) {
            String currentObject = queue.poll();
            int currentDepth = objectDepth.get(currentObject);
            
            System.out.println("处理对象: " + currentObject + ", 当前深度: " + currentDepth);
            
            if (currentDepth >= maxDepth) {
                System.out.println("  达到最大深度，跳过");
                continue;
            }
            
            // 查询上游映射
            List<Projection> upstreams = projectionMapper.findUpstreamProjections(currentObject);
            System.out.println("  找到 " + upstreams.size() + " 个上游映射");
            
            for (Projection p : upstreams) {
                // 跳过已处理的映射关系
                if (visitedProjections.contains(p.id)) {
                    System.out.println("    跳过已访问的映射: " + p.objectType + " -> " + p.toObjectType);
                    continue;
                }
                visitedProjections.add(p.id);
                System.out.println("    处理映射: " + p.objectType + " -> " + p.toObjectType);
                
                // 添加源对象节点（如果不存在）
                if (graph.nodes.stream().noneMatch(n -> n.objectType.equals(p.objectType))) {
                    ProjectionNode node = new ProjectionNode();
                    node.objectType = p.objectType;
                    node.id = p.objectType;
                    // 标记深度信息
                    node.criteriaStr = "depth:" + (currentDepth + 1);
                    graph.nodes.add(node);
                }
                
                // 添加边
                ProjectionEdge edge = new ProjectionEdge(p.objectType, p.toObjectType, p.id);
                int childCount = projectionMapper.countChildren(p.id);
                edge.hasChildren = childCount > 0;
                edge.label = childCount > 0 ? "包含子表" : "";
                graph.edges.add(edge);
                
                // 将源对象加入队列（允许重复访问，但要更新深度）
                int sourceDepth = currentDepth + 1;
                Integer existingDepth = objectDepth.get(p.objectType);
                if (existingDepth == null || sourceDepth < existingDepth) {
                    objectDepth.put(p.objectType, sourceDepth);
                    queue.offer(p.objectType);
                    System.out.println("      将 " + p.objectType + " 加入队列，深度: " + sourceDepth);
                } else {
                    System.out.println("      " + p.objectType + " 已访问过，深度: " + existingDepth);
                }
            }
        }
        
        System.out.println("上游链路构建完成: " + graph.nodes.size() + " 个节点, " + graph.edges.size() + " 条边");
    }

    /**
     * 获取映射详情（包含所有字段配置）
     */
    public ProjectionDetail getProjectionDetail(String projectionId) {
        ProjectionDetail detail = new ProjectionDetail();
        
        // 基本信息
        detail.projection = projectionMapper.findProjectionById(projectionId);
        if (detail.projection == null) {
            return detail;
        }
        
        // 主表字段映射
        List<FieldMapping> mainMappings = projectionMapper.findFieldMappings(projectionId);
        for (FieldMapping fm : mainMappings) {
            FieldMappingDetail fmd = new FieldMappingDetail(
                fm.objectField != null ? fm.objectField : "",
                fm.toObjectField,
                fm.fieldMappingTypeId,
                fm.expr,
                "main"
            );
            detail.mainFieldMappings.add(fmd);
        }
        
        // 子表映射
        List<ProjectionChild> children = projectionMapper.findProjectionChildren(projectionId);
        for (ProjectionChild child : children) {
            ChildMappingDetail cmd = new ChildMappingDetail(child);
            
            // 子表字段映射
            List<ProjectionChildFieldMapping> childMappings = projectionMapper.findChildFieldMappings(child.id);
            for (ProjectionChildFieldMapping cfm : childMappings) {
                FieldMappingDetail fmd = new FieldMappingDetail(
                    cfm.objectField != null ? cfm.objectField : "",
                    cfm.toObjectField,
                    cfm.fieldMappingTypeId,
                    cfm.expr,
                    "child"
                );
                fmd.childObjectType = child.objectType + " → " + child.toObjectType;
                cmd.fieldMappings.add(fmd);
            }
            
            detail.children.add(cmd);
        }
        
        return detail;
    }

    /**
     * 字段追踪
     */
    public List<FieldTrackResult> trackField(String fieldName) {
        List<FieldTrackResult> results = new ArrayList<>();
        
        // 追踪主表字段
        List<FieldTrackResult> mainResults = projectionMapper.trackFieldInMain(fieldName);
        results.addAll(mainResults);
        
        // 追踪子表字段
        List<FieldTrackResult> childResults = projectionMapper.trackFieldInChild(fieldName);
        results.addAll(childResults);
        
        return results;
    }

    /**
     * 获取所有对象类型列表
     */
    public List<String> getAllObjectTypes() {
        return projectionMapper.findAllObjectTypes();
    }

    /**
     * 获取对象统计信息
     */
    public Map<String, Object> getObjectStats(String objectType) {
        Map<String, Object> stats = new HashMap<>();
        
        // 下游数量
        List<Projection> downstreams = projectionMapper.findDownstreamProjections(objectType);
        stats.put("downstreamCount", downstreams.size());
        
        // 上游数量
        List<Projection> upstreams = projectionMapper.findUpstreamProjections(objectType);
        stats.put("upstreamCount", upstreams.size());
        
        // 相关映射ID列表
        List<String> relatedProjections = new ArrayList<>();
        downstreams.forEach(p -> relatedProjections.add(p.id));
        upstreams.forEach(p -> relatedProjections.add(p.id));
        stats.put("relatedProjections", relatedProjections);
        
        return stats;
    }

    /**
     * 检测循环依赖
     */
    public List<List<String>> detectCycles() {
        List<List<String>> cycles = new ArrayList<>();
        List<Projection> allProjections = projectionMapper.findAllProjections();
        
        // 构建邻接表
        Map<String, List<String>> graph = new HashMap<>();
        for (Projection p : allProjections) {
            graph.computeIfAbsent(p.objectType, k -> new ArrayList<>()).add(p.toObjectType);
        }
        
        // DFS 检测环
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();
        
        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                List<String> path = new ArrayList<>();
                detectCycleDFS(node, graph, visited, recStack, path, cycles);
            }
        }
        
        return cycles;
    }

    /**
     * DFS 检测环路
     */
    private boolean detectCycleDFS(String node, Map<String, List<String>> graph,
                                   Set<String> visited, Set<String> recStack,
                                   List<String> path, List<List<String>> cycles) {
        visited.add(node);
        recStack.add(node);
        path.add(node);
        
        List<String> neighbors = graph.get(node);
        if (neighbors != null) {
            for (String neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    if (detectCycleDFS(neighbor, graph, visited, recStack, path, cycles)) {
                        return true;
                    }
                } else if (recStack.contains(neighbor)) {
                    // 找到环
                    int cycleStart = path.indexOf(neighbor);
                    List<String> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
                    cycle.add(neighbor);
                    cycles.add(cycle);
                }
            }
        }
        
        recStack.remove(node);
        path.remove(path.size() - 1);
        return false;
    }
}
