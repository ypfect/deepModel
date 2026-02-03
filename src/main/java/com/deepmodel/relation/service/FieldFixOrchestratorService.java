package com.deepmodel.relation.service;

import com.deepmodel.relation.model.GraphModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 字段数据修复编排服务。
 *
 * 作用：
 *  - 基于 ImpactAnalyzerService 给出的字段依赖图（nodes/edges），
 *  - 计算每个字段的层级 depth，
 *  - 按严格的拓扑顺序（depth 从小到大）给出需要修复的字段列表以及关系类型，
 *  - 为后续具体的 SQL/Job 执行提供统一的“执行顺序”入口。
 *
 * 当前实现只做：分析 + 日志输出执行顺序，不直接更新数据库。
 * 后续可以在 executeFixStep / executeBatch 中接入真正的 Root/Intra/WriteBack 修复逻辑。
 */
@Service
public class FieldFixOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(FieldFixOrchestratorService.class);

    private final ImpactAnalyzerService impactAnalyzerService;

    public FieldFixOrchestratorService(ImpactAnalyzerService impactAnalyzerService) {
        this.impactAnalyzerService = impactAnalyzerService;
        ;
    }

    /**
     * 关系类型枚举，对应 GraphModels.Edge.type。
     */
    public enum RelType {
        INTRA("intra"),
        WRITE_BACK("writeBack"),
        VIEW("view"),
        UNKNOWN("unknown");

        private final String code;

        RelType(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        public static RelType fromCode(String code) {
            if (code == null) return UNKNOWN;
            for (RelType t : values()) {
                if (t.code.equals(code)) return t;
            }
            return UNKNOWN;
        }
    }

    /**
     * 单个需要修复的字段。
     */
    public static class FixStep {
        public final String objectType;
        public final String field;
        public final int depth;
        public final EnumSet<RelType> incomingTypes;

        public FixStep(String objectType, String field, int depth, EnumSet<RelType> incomingTypes) {
            this.objectType = objectType;
            this.field = field;
            this.depth = depth;
            this.incomingTypes = incomingTypes;
        }

        @Override
        public String toString() {
            return "FixStep{" +
                    "objectType='" + objectType + '\'' +
                    ", field='" + field + '\'' +
                    ", depth=" + depth +
                    ", incomingTypes=" + incomingTypes +
                    '}';
        }
    }

    /**
     * 启动一次字段修复编排（仅分析 + 打印执行顺序）。
     *
     * @param rootObject 根对象类型，例如 "InvoiceApplicationItem"
     * @param rootField  根字段名，例如 "makeInvoiceAmount"
     * @param maxDepth   向下游扩散深度
     * @param relType    传给 ImpactAnalyzerService 的 relType（0=全部，1=writeBack,2=intra,3=view）
     */
    public void planAndLogFixOrder(String rootObject,
                                   String rootField,
                                   int maxDepth,
                                   int relType) {

        log.info("[FieldFix] start planning, root={}.{}, maxDepth={}, relType={}",
                rootObject, rootField, maxDepth, relType);

        GraphModels.Graph graph = impactAnalyzerService.analyze(rootObject, rootField, maxDepth, relType, false);

        if (graph.nodes == null || graph.nodes.isEmpty()) {
            log.warn("[FieldFix] graph has no nodes for {}.{}", rootObject, rootField);
            return;
        }

        Map<String, Integer> depthMap = computeDepthMap(graph, rootObject, rootField);
        List<FixStep> steps = buildFixSteps(graph, depthMap);

        if (steps.isEmpty()) {
            log.warn("[FieldFix] no fix steps computed for {}.{}", rootObject, rootField);
            return;
        }

        // 按 depth 严格从小到大打印执行计划
        Map<Integer, List<FixStep>> byDepth = steps.stream()
                .collect(Collectors.groupingBy(s -> s.depth, TreeMap::new, Collectors.toList()));

        for (Map.Entry<Integer, List<FixStep>> e : byDepth.entrySet()) {
            int depth = e.getKey();
            List<FixStep> list = e.getValue();
            log.info("========== [FieldFix] depth {} ==========", depth);
            // 为了稳定输出，按 objectType + field 排序
            list.stream()
                    .sorted(Comparator
                            .comparing((FixStep s) -> s.objectType)
                            .thenComparing(s -> s.field))
                    .forEach(step -> {
                        log.info("[FieldFix] step depth={} {}.{} incomingTypes={}",
                                step.depth, step.objectType, step.field, step.incomingTypes);
                    });
        }

        log.info("[FieldFix] planning finished for {}.{}", rootObject, rootField);
    }

    /**
     * 计算每个节点的 depth（从 root 出发的最短步数，只走下游边）。
     */
    private Map<String, Integer> computeDepthMap(GraphModels.Graph graph,
                                                 String rootObject,
                                                 String rootField) {
        Map<String, Integer> depth = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();

        for (GraphModels.Edge e : graph.edges) {
            adj.computeIfAbsent(e.source, k -> new ArrayList<>()).add(e.target);
        }

        // 找到所有 root 起点（同对象 + 同字段名）
        String rootId = rootObject + "." + rootField;
        Queue<String> queue = new ArrayDeque<>();

        for (GraphModels.Node n : graph.nodes) {
            String id = n.id;
            if (id == null) {
                id = n.object + "." + n.field;
            }
            if (rootObject.equals(n.object) && rootField.equals(n.field)) {
                depth.put(id, 0);
                queue.offer(id);
            }
        }

        if (queue.isEmpty()) {
            // 兜底：如果没有直接匹配到，就从第一个节点作为 root
            if (!graph.nodes.isEmpty()) {
                GraphModels.Node n0 = graph.nodes.get(0);
                String id0 = n0.id != null ? n0.id : (n0.object + "." + n0.field);
                depth.put(id0, 0);
                queue.offer(id0);
                log.warn("[FieldFix] root node not found for {}.{}, fallback to {}",
                        rootObject, rootField, id0);
            }
        }

        while (!queue.isEmpty()) {
            String cur = queue.poll();
            int d = depth.getOrDefault(cur, 0);
            for (String next : adj.getOrDefault(cur, Collections.emptyList())) {
                if (!depth.containsKey(next)) {
                    depth.put(next, d + 1);
                    queue.offer(next);
                }
            }
        }

        return depth;
    }

    /**
     * 基于图和 depthMap 构建每个字段的 FixStep。
     *
     * 规则：
     *  - 每个节点（字段）都会有一个 depth（没有则视为 0 或忽略），
     *  - incomingTypes = 所有指向它的 edge.type 的集合。
     */
    private List<FixStep> buildFixSteps(GraphModels.Graph graph,
                                        Map<String, Integer> depthMap) {
        Map<String, EnumSet<RelType>> incoming = new HashMap<>();

        for (GraphModels.Edge e : graph.edges) {
            RelType t = RelType.fromCode(e.type);
            incoming.computeIfAbsent(e.target, k -> EnumSet.noneOf(RelType.class)).add(t);
        }

        List<FixStep> result = new ArrayList<>();

        for (GraphModels.Node n : graph.nodes) {
            String id = n.id != null ? n.id : (n.object + "." + n.field);
            Integer d = depthMap.get(id);
            if (d == null) {
                // 没在 BFS 图里出现的节点，可以选择忽略或视为 depth=0，这里先忽略
                continue;
            }
            EnumSet<RelType> types = incoming.getOrDefault(id, EnumSet.noneOf(RelType.class));
            result.add(new FixStep(n.object, n.field, d, types.isEmpty() ? EnumSet.noneOf(RelType.class) : types));
        }

        return result;
    }
}

