package com.deepmodel.relation.service;

import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.GraphModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);
    private final ImpactAnalyzerService analyzerService;

    public HealthCheckService(ImpactAnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
    }

    public static class CycleResult {
        public List<List<String>> cycles = new ArrayList<>();
        public int totalCycles;
    }

    public static class ChainResult {
        public List<ChainPath> chains = new ArrayList<>();
        public int totalChains;
    }

    public static class ChainPath {
        public String startNode;
        public String endNode;
        public int length;
        public List<String> path;

        public ChainPath(String start, String end, int len, List<String> path) {
            this.startNode = start;
            this.endNode = end;
            this.length = len;
            this.path = path;
        }
    }

    /**
     * 进度回调：可用于 SSE 推送
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(String phase, int current, int total, String detail);
    }

    /**
     * 检测所有循环依赖 (使用 Tarjan 算法寻找强连通分量)
     */
    public CycleResult detectCycles(List<String> appNames) {
        return detectCycles(appNames, null);
    }

    public CycleResult detectCycles(List<String> appNames, ProgressCallback progress) {
        Map<String, List<String>> adj = buildGlobalGraph(appNames, progress);

        if (progress != null)
            progress.onProgress("analyze", 0, 0, "Running Tarjan SCC...");
        List<List<String>> sccs = findSCCs(adj);

        CycleResult result = new CycleResult();
        for (List<String> scc : sccs) {
            if (scc.size() > 1 || (scc.size() == 1 && hasSelfLoop(adj, scc.get(0)))) {
                result.cycles.add(scc);
            }
        }
        result.totalCycles = result.cycles.size();
        return result;
    }

    /**
     * 检测深度过长的依赖链。
     * 深度定义与图谱分析一致：depth = 边数（层级数）。
     * 例如 A→B→C 的深度为 2（2 条边）。
     * 只从入度为 0 的根节点（无上游依赖）开始报告，避免冗余子链。
     * 
     * @param threshold 深度阈值（边数），超过此值的链会被报告
     */
    public ChainResult detectDeepChains(int threshold, List<String> appNames) {
        return detectDeepChains(threshold, appNames, null);
    }

    public ChainResult detectDeepChains(int threshold, List<String> appNames, ProgressCallback progress) {
        long t0 = System.currentTimeMillis();
        log.info("detectDeepChains start, threshold={}, appNames={}", threshold, appNames);
        Map<String, List<String>> adj = buildGlobalGraph(appNames, progress);
        log.info("buildGlobalGraph done in {}ms, adj size={}", System.currentTimeMillis() - t0, adj.size());
        ChainResult result = new ChainResult();

        // 计算入度，找出根节点（入度为 0 的节点）
        Set<String> allNodes = new HashSet<>(adj.keySet());
        Set<String> hasIncoming = new HashSet<>();
        for (List<String> targets : adj.values()) {
            for (String t : targets) {
                hasIncoming.add(t);
                allNodes.add(t);
            }
        }
        Set<String> roots = new HashSet<>();
        for (String node : allNodes) {
            if (!hasIncoming.contains(node)) {
                roots.add(node);
            }
        }
        if (roots.isEmpty()) {
            roots = allNodes;
        }

        if (progress != null)
            progress.onProgress("analyze", 0, roots.size(), "Analyzing chain depths...");

        Map<String, Integer> memoDepth = new HashMap<>();
        Map<String, String> memoNext = new HashMap<>();
        Set<String> visiting = new HashSet<>();
        int idx = 0;

        for (String node : roots) {
            try {
                int d = getMaxDepth(node, adj, memoDepth, memoNext, visiting);
                if (d >= threshold) {
                    List<String> path = reconstructPath(node, memoNext);
                    result.chains.add(new ChainPath(node, path.get(path.size() - 1), d, path));
                }
            } catch (IllegalStateException e) {
                // cycle detected during depth check, ignore
            }
            idx++;
            if (progress != null && idx % 50 == 0) {
                progress.onProgress("analyze", idx, roots.size(),
                        "Checked " + idx + "/" + roots.size() + " roots, found " + result.chains.size() + " chains");
            }
        }

        result.chains.sort((a, b) -> b.length - a.length);
        result.totalChains = result.chains.size();
        log.info("detectDeepChains done in {}ms, found {} chains", System.currentTimeMillis() - t0, result.totalChains);
        if (progress != null)
            progress.onProgress("done", result.totalChains, result.totalChains, "Complete");
        return result;
    }

    /**
     * Debug method: expose global graph stats for troubleshooting
     */
    public Map<String, Object> debugGlobalGraph(List<String> appNames) {
        Map<String, List<String>> adj = buildGlobalGraph(appNames, null);
        Map<String, Object> info = new LinkedHashMap<>();
        int edgeCount = adj.values().stream().mapToInt(List::size).sum();
        info.put("nodeCount", adj.size());
        info.put("edgeCount", edgeCount);

        // Sample edges (first 20)
        List<String> sampleEdges = new ArrayList<>();
        outer: for (Map.Entry<String, List<String>> entry : adj.entrySet()) {
            for (String target : entry.getValue()) {
                sampleEdges.add(entry.getKey() + " -> " + target);
                if (sampleEdges.size() >= 20)
                    break outer;
            }
        }
        info.put("sampleEdges", sampleEdges);

        // Root nodes
        Set<String> allNodes = new HashSet<>(adj.keySet());
        Set<String> hasIncoming = new HashSet<>();
        for (List<String> targets : adj.values()) {
            for (String t : targets) {
                hasIncoming.add(t);
                allNodes.add(t);
            }
        }
        int rootCount = 0;
        List<String> sampleRoots = new ArrayList<>();
        for (String node : allNodes) {
            if (!hasIncoming.contains(node)) {
                rootCount++;
                if (sampleRoots.size() < 10)
                    sampleRoots.add(node);
            }
        }
        info.put("totalNodes", allNodes.size());
        info.put("rootCount", rootCount);
        info.put("sampleRoots", sampleRoots);

        return info;
    }

    // --- Helpers ---

    private Map<String, List<String>> buildGlobalGraph(List<String> appNames, ProgressCallback progress) {
        Map<String, List<String>> adj = new HashMap<>();
        Set<String> edgeSet = new HashSet<>();

        // 从内存中获取所有字段，再根据 appName 过滤（不过滤 bill / view，由调用方通过 appName 控制范围）
        List<BaseappObjectField> allFields = analyzerService.getAllFields();
        List<BaseappObjectField> filtered = new ArrayList<>();
        for (BaseappObjectField field : allFields) {
            if (appNames != null && !appNames.isEmpty()) {
                boolean match = false;
                String fApp = field.getAppName();
                if (fApp != null) {
                    String fLower = fApp.toLowerCase(Locale.ROOT);
                    for (String targetApp : appNames) {
                        if (targetApp == null)
                            continue;
                        String tLower = targetApp.toLowerCase(Locale.ROOT).trim();
                        if (tLower.isEmpty())
                            continue;
                        if (fLower.equals(tLower) || fLower.startsWith(tLower) || fLower.contains(tLower)) {
                            match = true;
                            break;
                        }
                    }
                }
                if (!match) {
                    continue;
                }
            }
            filtered.add(field);
        }

        int totalFields = filtered.size();
        log.info("buildGlobalGraph: scanning {} filtered fields (from {} total)", totalFields, allFields.size());
        if (progress != null)
            progress.onProgress("graph", 0, totalFields, "Scanning " + totalFields + " fields...");

        int fieldCount = 0;
        for (BaseappObjectField field : filtered) {
            String obj = field.getObjectType();
            String fName = field.getApiName() != null ? field.getApiName() : field.getName();
            if (obj == null || obj.trim().isEmpty() || fName == null || fName.trim().isEmpty()) {
                fieldCount++;
                continue;
            }

            try {
                GraphModels.Graph g = analyzerService.analyze(obj, fName, 1, 0, false);
                if (g.edges != null) {
                    for (GraphModels.Edge e : g.edges) {
                        String edgeKey = e.source + "->" + e.target;
                        if (edgeSet.add(edgeKey)) {
                            adj.computeIfAbsent(e.source, k -> new ArrayList<>()).add(e.target);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to analyze field: {}.{}", obj, fName, e);
            }
            fieldCount++;
            if (fieldCount % 50 == 0) {
                log.info("buildGlobalGraph progress: {}/{} fields, {} edges", fieldCount, totalFields,
                        edgeSet.size());
                if (progress != null) {
                    progress.onProgress("graph", fieldCount, totalFields,
                            fieldCount + "/" + totalFields + " fields, " + edgeSet.size() + " edges");
                }
            }
        }
        log.info("buildGlobalGraph complete: {} fields, {} edges", fieldCount, edgeSet.size());
        return adj;
    }

    private boolean hasSelfLoop(Map<String, List<String>> adj, String u) {
        List<String> neighbors = adj.get(u);
        return neighbors != null && neighbors.contains(u);
    }

    // Tarjan's implementation
    private int index = 0;
    private Stack<String> stack = new Stack<>();
    private Map<String, Integer> dfn = new HashMap<>();
    private Map<String, Integer> low = new HashMap<>();
    private Set<String> onStack = new HashSet<>();

    private List<List<String>> findSCCs(Map<String, List<String>> adj) {
        index = 0;
        stack.clear();
        dfn.clear();
        low.clear();
        onStack.clear();
        List<List<String>> result = new ArrayList<>();

        for (String node : adj.keySet()) {
            if (!dfn.containsKey(node)) {
                tarjan(node, adj, result);
            }
        }
        return result;
    }

    private void tarjan(String u, Map<String, List<String>> adj, List<List<String>> result) {
        dfn.put(u, index);
        low.put(u, index);
        index++;
        stack.push(u);
        onStack.add(u);

        List<String> neighbors = adj.getOrDefault(u, Collections.emptyList());
        for (String v : neighbors) {
            if (!dfn.containsKey(v)) {
                tarjan(v, adj, result);
                low.put(u, Math.min(low.get(u), low.get(v)));
            } else if (onStack.contains(v)) {
                low.put(u, Math.min(low.get(u), dfn.get(v)));
            }
        }

        if (low.get(u).equals(dfn.get(u))) {
            List<String> scc = new ArrayList<>();
            String v;
            do {
                v = stack.pop();
                onStack.remove(v);
                scc.add(v);
            } while (!u.equals(v));
            result.add(scc);
        }
    }

    // DFS with Memoization for Deep Chain
    // 深度定义 = 边数（与图谱分析中的 level 概念一致）
    // 叶子节点 depth=0, A→B depth=1, A→B→C depth=2
    private int getMaxDepth(String u, Map<String, List<String>> adj, Map<String, Integer> memoDepth,
            Map<String, String> memoNext, Set<String> visiting) {
        if (visiting.contains(u)) {
            throw new IllegalStateException("Cycle detected");
        }
        if (memoDepth.containsKey(u)) {
            return memoDepth.get(u);
        }

        visiting.add(u);
        int max = 0;
        String nextNode = null;

        List<String> neighbors = adj.get(u);
        if (neighbors != null) {
            for (String v : neighbors) {
                try {
                    int d = getMaxDepth(v, adj, memoDepth, memoNext, visiting) + 1; // +1 为 u→v 这条边
                    if (d > max) {
                        max = d;
                        nextNode = v;
                    }
                } catch (IllegalStateException e) {
                    // ignore cycle path in depth check
                }
            }
        }

        visiting.remove(u);
        memoDepth.put(u, max);
        if (nextNode != null) {
            memoNext.put(u, nextNode);
        }
        return max;
    }

    private List<String> reconstructPath(String start, Map<String, String> memoNext) {
        List<String> path = new ArrayList<>();
        String cur = start;
        while (cur != null) {
            path.add(cur);
            cur = memoNext.get(cur);
        }
        return path;
    }
}
