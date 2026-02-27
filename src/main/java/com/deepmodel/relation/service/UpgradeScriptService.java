package com.deepmodel.relation.service;

import com.deepmodel.relation.dao.BaseappObjectFieldMapper;
import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.GraphModels;
import com.deepmodel.relation.model.WriteBackExpr;
import com.deepmodel.relation.util.ExprUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * 根据字段影响关系生成数据升级 SQL 脚本（只生成文本，不执行）。
 *
 * 用法：
 *  - 通过 ImpactAnalyzerService 获取某个根字段的下游依赖图；
 *  - 按 depth 计算字段层级；
 *  - 按层级 + 关系类型（intra / writeBack）生成对应的 UPDATE/聚合 SQL；
 *  - 返回一整段可在生产环境执行的脚本，由使用方自行审阅和执行。
 *
 * 说明：
 *  - 使用 EQL 格式（对象名 + 驼峰字段名），通过 eqlExecutor.parseEql 转换为真实 SQL（含前缀 + 下划线列名）。
 *  - 视图关系（type = "view"）目前默认不生成 SQL，由调用方通过 relTypes 控制是否包含。
 */
@Service
public class UpgradeScriptService {

    private static final Logger log = LoggerFactory.getLogger(UpgradeScriptService.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /**
     * 当前批量生成上下文中的「最新字段定义」缓存（来自比较库）。
     * key = "ObjectType.camelCaseField"，优先级高于本地 in-memory 的 analyzerService。
     * 使用 ThreadLocal 保证并发安全。
     */
    private static final ThreadLocal<Map<String, BaseappObjectField>> latestFieldDefsCtx = new ThreadLocal<>();

    @Value("${writeback-sql.api-url:http://arap.test-tx-16.e7link.com/arap/gen/debug/writeBackField2sql}")
    private String writeBackSqlApiUrl;
    @Value("${writeback-sql.tenant-id:711FNX50G6V0009}")
    private String writeBackSqlTenantId;

    private final ImpactAnalyzerService impactAnalyzerService;
    private final BaseappObjectFieldMapper mapper;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public UpgradeScriptService(ImpactAnalyzerService impactAnalyzerService,
                                BaseappObjectFieldMapper mapper,
                                OkHttpClient httpClient) {
        this.impactAnalyzerService = impactAnalyzerService;
        this.mapper = mapper;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 关系类型枚举。
     */
    enum RelType {
        INTRA("intra"),
        WRITE_BACK("writeBack"),
        VIEW("view"),
        UNKNOWN("unknown");

        final String code;

        RelType(String code) {
            this.code = code;
        }

        static RelType fromCode(String code) {
            if (code == null) return UNKNOWN;
            for (RelType t : values()) {
                if (t.code.equals(code)) return t;
            }
            return UNKNOWN;
        }
    }

    static class FixStep {
        final String objectType;
        final String field;
        final int depth;
        final EnumSet<RelType> incomingTypes;

        FixStep(String objectType, String field, int depth, EnumSet<RelType> incomingTypes) {
            this.objectType = objectType;
            this.field = field;
            this.depth = depth;
            this.incomingTypes = incomingTypes;
        }
    }

    /**
     * 生成升级 SQL 脚本。
     *
     * @param rootObject 根对象类型
     * @param rootField  根字段名
     * @param maxDepth   下游层级深度
     * @param relTypes   包含的关系类型字符串，例如 "intra,writeBack" 或 "intra,writeBack,view"
     * @return 可直接保存为 .sql 的脚本文本
     */
    public String generateUpgradeScript(String rootObject,
                                        String rootField,
                                        int maxDepth,
                                        String relTypes) {

        Set<RelType> included = parseRelTypes(relTypes);

        GraphModels.Graph graph = impactAnalyzerService.analyze(rootObject, rootField, maxDepth, 0, false);
        if (graph.nodes == null || graph.nodes.isEmpty()) {
            return "-- 未找到任何影响关系: " + rootObject + "." + rootField + "\n";
        }

        Map<String, Integer> depthMap = computeDepthMap(graph, rootObject, rootField);
        List<FixStep> steps = buildFixSteps(graph, depthMap, included);

        StringBuilder sb = new StringBuilder();
        sb.append("-- =========================================\n");
        sb.append("-- 升级脚本 - 根字段: ").append(rootObject).append(".").append(rootField).append("\n");
        sb.append("-- maxDepth = ").append(maxDepth).append(", relTypes = ").append(included).append("\n");
        sb.append("-- 说明：\n");
        sb.append("--  1. 第 0 层为根字段本身，请根据新口径规则手工补充 UPDATE 语句。\n");
        sb.append("--  2. 后续各层按 depth 从小到大，先触发(intra)，再回写(writeBack)。\n");
        sb.append("-- =========================================\n\n");

        // 根字段：占位说明
        appendRootFieldPlaceholder(sb, rootObject, rootField);

        // 按 depth 分组
        Map<Integer, List<FixStep>> byDepth = new TreeMap<Integer, List<FixStep>>();
        for (FixStep s : steps) {
            if (s.depth < 0) continue;
            if (s.depth > maxDepth) continue;
            List<FixStep> list = byDepth.get(s.depth);
            if (list == null) {
                list = new ArrayList<FixStep>();
                byDepth.put(s.depth, list);
            }
            list.add(s);
        }

        for (Map.Entry<Integer, List<FixStep>> e : byDepth.entrySet()) {
            int depth = e.getKey();
            if (depth == 0) {
                // 根字段已单独处理
                continue;
            }
            List<FixStep> list = e.getValue();
            if (list.isEmpty()) continue;

            sb.append("\n-- =========================================\n");
            sb.append("-- depth ").append(depth).append("\n");
            sb.append("-- =========================================\n\n");

            // 先 intra，再 writeBack
            // 为了输出稳定性，先按对象/字段排序
            Collections.sort(list, new Comparator<FixStep>() {
                @Override
                public int compare(FixStep a, FixStep b) {
                    int c1 = a.objectType.compareTo(b.objectType);
                    if (c1 != 0) return c1;
                    return a.field.compareTo(b.field);
                }
            });

            for (FixStep step : list) {
                if (step.incomingTypes.contains(RelType.INTRA) && included.contains(RelType.INTRA)) {
                    appendIntraSql(sb, step);
                }
            }
            for (FixStep step : list) {
                if (step.incomingTypes.contains(RelType.WRITE_BACK) && included.contains(RelType.WRITE_BACK)) {
                    appendWriteBackSql(sb, step);
                }
            }
        }

        return sb.toString();
    }

    /**
     * 多根批量生成升级脚本：合并多根下游图并闭合上游，拓扑排序后按顺序输出（占位 / intra / writeBack），
     * 更新逻辑与单字段一致，仅图与排序不同。
     *
     * @param roots   勾选的根字段列表，每项为 (objectType, field)
     * @param maxDepth 下游层级深度
     * @param relTypes 关系类型，例如 "intra,writeBack"
     */
    /**
     * 批量生成升级脚本（不带比较库定义，兼容旧调用）。
     */
    public String generateUpgradeScriptBatch(List<Map.Entry<String, String>> roots,
                                             int maxDepth,
                                             String relTypes) {
        return generateUpgradeScriptBatch(roots, maxDepth, relTypes, null);
    }

    /**
     * 批量生成升级脚本。
     *
     * @param latestFieldDefs 比较库的最新字段定义 map（key = "ObjectType.camelField"），
     *                        为 null 时退化为使用本地 in-memory 数据（兼容旧行为）。
     *                        以最新定义为准，确保 trigger/writeBack 表达式使用新版本。
     */
    public String generateUpgradeScriptBatch(List<Map.Entry<String, String>> roots,
                                             int maxDepth,
                                             String relTypes,
                                             Map<String, BaseappObjectField> latestFieldDefs) {
        if (roots == null || roots.isEmpty()) {
            return "-- 未选择任何根字段\n";
        }
        // 设置线程上下文，所有 getLatestFieldInfo 调用都会优先命中比较库定义
        latestFieldDefsCtx.set(latestFieldDefs);
        try {
            return doGenerateUpgradeScriptBatch(roots, maxDepth, relTypes);
        } finally {
            latestFieldDefsCtx.remove();
        }
    }

    private String doGenerateUpgradeScriptBatch(List<Map.Entry<String, String>> roots,
                                                int maxDepth,
                                                String relTypes) {
        if (roots == null || roots.isEmpty()) {
            return "-- 未选择任何根字段\n";
        }

        // 规范化 root 字段名：前端可能传 snake_case (name)，统一转为 camelCase 再建图
        List<Map.Entry<String, String>> normalizedRoots = new java.util.ArrayList<>();
        for (Map.Entry<String, String> r : roots) {
            String obj = r.getKey();
            String fld = r.getValue();
            if (obj == null || fld == null) { normalizedRoots.add(r); continue; }
            // 1. 先尝试直接查找
            BaseappObjectField def = getLatestFieldInfo(obj, fld);
            // 2. 若找不到且字段名含下划线，尝试 camelCase 版本
            if (def == null && fld.contains("_")) {
                String camelFld = ExprUtils.snakeToCamel(fld);
                def = getLatestFieldInfo(obj, camelFld);
                if (def != null) { fld = camelFld; }
            }
            // 3. 用 apiName（优先）或将 name 转 camelCase 作为最终字段名
            if (def != null) {
                String apiName = def.getApiName();
                if (apiName != null && !apiName.trim().isEmpty()) {
                    fld = apiName.trim();
                } else if (def.getName() != null && !def.getName().trim().isEmpty()) {
                    String n = def.getName().trim();
                    fld = n.contains("_") ? ExprUtils.snakeToCamel(n) : n;
                }
            }
            normalizedRoots.add(new java.util.AbstractMap.SimpleEntry<>(obj, fld));
        }
        roots = normalizedRoots;

        Set<RelType> included = parseRelTypes(relTypes);
        int relType = 0; // 0 = intra + writeBack
        if (included.contains(RelType.INTRA) && !included.contains(RelType.WRITE_BACK)) relType = 2;
        else if (included.contains(RelType.WRITE_BACK) && !included.contains(RelType.INTRA)) relType = 1;

        GraphModels.Graph graph = impactAnalyzerService.buildMultiRootClosedGraph(roots, maxDepth, relType);
        if (graph.nodes == null || graph.nodes.isEmpty()) {
            return "-- 未找到任何影响关系（多根合并图为空）\n";
        }

        // canonicalFieldName() 当字段无 apiName 时直接返回 name（snake_case），
        // 导致图中存在同一字段的 snake_case 和 camelCase 两个节点。先统一规范化为 camelCase。
        normalizeGraphNodeIdsToCamelCase(graph);

        // 用 latestFieldDefsCtx（比较库定义）补充 ImpactAnalyzerService 可能漏掉的上游边：
        //  1. writeBack 上游边：新增字段在基础库不存在时，getDirectUpstreamEdges 返回空
        //  2. intra 上游边：trigger/formula 在比较库新增了对 writeBack 字段的引用，
        //     但基础库 triggerExpr 未含该引用，导致 trigger SQL 先于 writeBack SQL 执行，数据错误
        enrichGraphWithLatestEdges(graph);

        Set<String> rootIds = new HashSet<>();
        for (Map.Entry<String, String> r : roots) {
            if (r.getKey() != null && r.getValue() != null) {
                rootIds.add(r.getKey() + "." + r.getValue());
            }
        }

        List<String> topoOrder = topologicalSort(graph);
        Map<String, EnumSet<RelType>> incomingTypes = buildIncomingTypes(graph, included);

        // 「本批有数据变化」= 会对其执行 SQL 的节点：根 + 会执行的 intra + 会执行的 writeBack（递推）
        // 仅当回写来源在此集合内时才生成回写 SQL，避免未改动的对象被回写
        Set<String> updatedSet = computeUpdatedNodes(topoOrder, rootIds, incomingTypes, included, graph);

        StringBuilder sb = new StringBuilder();
        sb.append("-- =========================================\n");
        sb.append("-- 批量字段升级脚本（").append(roots.size()).append(" 个根字段）\n");
        sb.append("-- 说明：先按依赖拓扑顺序列出批量更新逻辑与执行顺序，再输出对应 SQL。\n");
        sb.append("-- =========================================\n\n");

        // 先在注释中列出批量更新的逻辑和顺序，方便审阅（回写若来源无变化会标 SKIP）
        sb.append("-- 批量更新执行顺序（拓扑排序结果）：\n");
        int idx = 1;
        for (String nodeId : topoOrder) {
            int dot = nodeId.indexOf('.');
            if (dot <= 0) continue;
            String objectType = nodeId.substring(0, dot);
            String field = nodeId.substring(dot + 1);
            EnumSet<RelType> types = incomingTypes.get(nodeId);
            if (types == null) types = EnumSet.noneOf(RelType.class);

            if (!rootIds.contains(nodeId) && types.isEmpty()) {
                continue;
            }

            String role;
            if (rootIds.contains(nodeId)) {
                BaseappObjectField nodeDef = getLatestFieldInfo(objectType, field);
                boolean isWbRoot = nodeDef != null && nodeDef.getWriteBackExpr() != null
                        && !nodeDef.getWriteBackExpr().trim().isEmpty();
                String rootFm = nodeDef != null
                        ? firstNonEmpty(nodeDef.getTriggerExpr(), nodeDef.getExpression(), nodeDef.getVirtualExpr())
                        : null;
                boolean isTriggerRoot = rootFm != null && !rootFm.trim().isEmpty();
                if (isWbRoot) {
                    role = "ROOT(回写,先填充)";
                } else if (isTriggerRoot) {
                    role = "ROOT(Trigger,直接UPDATE)";
                } else {
                    role = "ROOT(基础字段,需人工补充)";
                }
            } else {
                List<String> labels = new ArrayList<String>();
                if (types.contains(RelType.INTRA)) {
                    boolean intraActive = updatedSet.contains(nodeId);
                    labels.add(intraActive ? "INTRA" : "INTRA(SKIP:上游无变化)");
                }
                if (types.contains(RelType.WRITE_BACK)) {
                    boolean wbActive = updatedSet.contains(nodeId);
                    labels.add(wbActive ? "WRITE_BACK" : "WRITE_BACK(SKIP:来源无变化)");
                }
                role = String.join("+", labels);
            }
            sb.append("--  ").append(idx++).append(") ")
              .append(objectType).append(".").append(field)
              .append("  [").append(role).append("]\n");
        }
        sb.append("\n-- ===== 以上为逻辑与顺序，以下开始输出实际 SQL =====\n\n");

        for (String nodeId : topoOrder) {
            int dot = nodeId.indexOf('.');
            if (dot <= 0) continue;
            String objectType = nodeId.substring(0, dot);
            String field = nodeId.substring(dot + 1);
            EnumSet<RelType> types = incomingTypes.get(nodeId);
            if (types == null) types = EnumSet.noneOf(RelType.class);

            if (rootIds.contains(nodeId)) {
                // 解析规范字段名：优先比较库最新定义，fallback 本地 in-memory
                BaseappObjectField rootDef = getLatestFieldInfo(objectType, field);
                String canonicalField = resolveCanonicalField(rootDef, field);

                boolean rootIsWriteBack = rootDef != null && rootDef.getWriteBackExpr() != null
                        && !rootDef.getWriteBackExpr().trim().isEmpty();
                String rootFormula = rootDef != null
                        ? firstNonEmpty(rootDef.getTriggerExpr(), rootDef.getExpression(), rootDef.getVirtualExpr())
                        : null;
                boolean rootIsTrigger = rootFormula != null && !rootFormula.trim().isEmpty();

                if (rootIsWriteBack && included.contains(RelType.WRITE_BACK)) {
                    // 回写字段（新增或保持为 writeBack）：先执行回写填充
                    sb.append("-- ROOT 回写字段（先回写填充）：")
                      .append(objectType).append(".").append(canonicalField).append("\n");
                    appendWriteBackSql(sb, new FixStep(objectType, canonicalField, -1, EnumSet.of(RelType.WRITE_BACK)));
                } else if (rootIsTrigger && included.contains(RelType.INTRA)) {
                    // Trigger/公式字段（新增或由 writeBack 改为 trigger）：直接生成 UPDATE SQL
                    sb.append("-- ROOT Trigger 字段（含定义变更）：")
                      .append(objectType).append(".").append(canonicalField).append("\n");
                    appendIntraSql(sb, new FixStep(objectType, canonicalField, -1, EnumSet.of(RelType.INTRA)));
                } else {
                    // 基础字段（无公式、无回写）：输出占位，需人工补充修复逻辑
                    appendRootFieldPlaceholderShort(sb, objectType, canonicalField);
                }
                continue;
            }
            if (types.contains(RelType.INTRA) && included.contains(RelType.INTRA)) {
                if (updatedSet.contains(nodeId)) {
                    appendIntraSql(sb, new FixStep(objectType, field, -1, types));
                } else {
                    sb.append("-- [SKIP] Trigger 字段 ").append(objectType).append(".").append(field)
                      .append(" 的上游字段本批无数据变化，无需执行\n\n");
                }
            }
            if (types.contains(RelType.WRITE_BACK) && included.contains(RelType.WRITE_BACK)) {
                if (updatedSet.contains(nodeId)) {
                    appendWriteBackSql(sb, new FixStep(objectType, field, -1, types));
                } else {
                    sb.append("-- [SKIP] 回写 ").append(objectType).append(".").append(field)
                      .append(" 的来源字段本批无数据变化，无需执行\n\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * 计算本批「有数据变化」的节点集合：根 + 上游已变化的 intra 节点 + 来源已变化的 writeBack 节点（按拓扑顺序递推）。
     *
     * 关键逻辑：
     *  - INTRA 节点只有当其 intra 上游（某个依赖字段）在 updated 中时，自身才被认为有变化。
     *  - WRITE_BACK 节点只有当其 writeBack 来源字段在 updated 中时，自身才被认为有变化。
     *  - 两种类型保持对称，避免"上游没变但下游被误标为有变化"。
     */
    private Set<String> computeUpdatedNodes(List<String> topoOrder,
                                            Set<String> rootIds,
                                            Map<String, EnumSet<RelType>> incomingTypes,
                                            Set<RelType> included,
                                            GraphModels.Graph graph) {
        // 构建 intra 来源映射：nodeId -> 该节点的所有 intra 上游 nodeId
        Map<String, Set<String>> intraSourcesMap = new HashMap<>();
        for (GraphModels.Edge e : graph.edges) {
            if ("intra".equals(e.type)) {
                intraSourcesMap.computeIfAbsent(e.target, k -> new HashSet<>()).add(e.source);
            }
        }

        Set<String> updated = new HashSet<>(rootIds);
        for (String nodeId : topoOrder) {
            if (rootIds.contains(nodeId)) continue;
            EnumSet<RelType> types = incomingTypes.get(nodeId);
            if (types == null) types = EnumSet.noneOf(RelType.class);
            int dot = nodeId.indexOf('.');
            if (dot <= 0) continue;
            String objectType = nodeId.substring(0, dot);
            String field = nodeId.substring(dot + 1);

            // INTRA：只有当至少一个 intra 上游已在 updated 中，才标记自身为有变化
            if (types.contains(RelType.INTRA) && included.contains(RelType.INTRA)) {
                Set<String> intraSources = intraSourcesMap.getOrDefault(nodeId, Collections.emptySet());
                if (intraSources.stream().anyMatch(updated::contains)) {
                    updated.add(nodeId);
                }
            }
            // WRITE_BACK：只有当至少一个 writeBack 来源字段已在 updated 中，才标记自身为有变化
            if (types.contains(RelType.WRITE_BACK) && included.contains(RelType.WRITE_BACK)) {
                Set<String> wbSources = getWriteBackSourceNodeIds(objectType, field);
                if (wbSources != null && wbSources.stream().anyMatch(updated::contains)) {
                    updated.add(nodeId);
                }
            }
        }
        return updated;
    }

    /** 对依赖图做拓扑排序（依赖在前、被依赖在后），保证脚本顺序正确。 */
    /**
     * 将图中所有节点的字段名统一规范化为 camelCase：
     * ImpactAnalyzerService.canonicalFieldName() 当字段无 apiName 时直接返回 name（snake_case），
     * 导致 buildIntraDependencies / buildCrossObjectDependencies 建出的节点 ID 是 snake_case，
     * 而 enrichGraphWithLatestEdges 从比较库拿到的节点 ID 是 camelCase，产生重复节点。
     * 此方法在图增强之前把所有 snake_case 节点 ID 转成 camelCase，并对重复节点/边去重。
     */
    private void normalizeGraphNodeIdsToCamelCase(GraphModels.Graph graph) {
        // 构建 old_id → new_camelCase_id 映射
        Map<String, String> idRemap = new HashMap<>();
        for (GraphModels.Node n : graph.nodes) {
            String oldId = n.id != null ? n.id : (n.object + "." + n.field);
            String fld = n.field != null ? n.field : "";
            String camelFld;
            // 优先用 apiName（已经是 camelCase），其次 snakeToCamel(name)
            if (n.apiName != null && !n.apiName.trim().isEmpty()) {
                camelFld = n.apiName.trim();
            } else if (fld.contains("_")) {
                camelFld = ExprUtils.snakeToCamel(fld);
            } else {
                camelFld = fld;
            }
            if (!camelFld.equals(fld)) {
                String newId = n.object + "." + camelFld;
                idRemap.put(oldId, newId);
                n.field = camelFld;
                n.id = newId;
            }
        }

        if (idRemap.isEmpty()) return;

        // 把边的 source/target 同步更新
        for (GraphModels.Edge e : graph.edges) {
            String remapped;
            if ((remapped = idRemap.get(e.source)) != null) e.source = remapped;
            if ((remapped = idRemap.get(e.target)) != null) e.target = remapped;
        }

        // 去重节点（重命名后可能出现相同 id 的多个节点）
        Set<String> seenIds = new HashSet<>();
        List<GraphModels.Node> dedupNodes = new java.util.ArrayList<>();
        for (GraphModels.Node n : graph.nodes) {
            String id = n.id != null ? n.id : (n.object + "." + n.field);
            if (seenIds.add(id)) dedupNodes.add(n);
        }
        graph.nodes.clear();
        graph.nodes.addAll(dedupNodes);

        // 去重边（重命名后可能出现相同 source|type|target 的多条边）
        Set<String> seenEdges = new HashSet<>();
        List<GraphModels.Edge> dedupEdges = new java.util.ArrayList<>();
        for (GraphModels.Edge e : graph.edges) {
            if (seenEdges.add(e.source + "|" + e.type + "|" + e.target)) dedupEdges.add(e);
        }
        graph.edges.clear();
        graph.edges.addAll(dedupEdges);
    }

    /**
     * 使用比较库（latestFieldDefsCtx）的字段定义，为图中所有节点补充缺失的上游边。
     *
     * <p>补充两类边：
     * <ol>
     *   <li><b>writeBack 上游边</b>：基础库中不存在的新增字段无法通过
     *       {@code getDirectUpstreamEdges} 找到其 writeBack 来源，导致该字段入度为 0，
     *       拓扑排序排到最前，早于其来源字段执行 SQL。</li>
     *   <li><b>intra 上游边</b>：trigger/formula 表达式在比较库中新增了对 writeBack 字段的
     *       引用，但基础库 triggerExpr 未含该引用，{@code buildIntraDependencies} 找不到此
     *       intra 依赖，导致 trigger SQL 早于 writeBack SQL 执行，数据升级结果不正确。</li>
     * </ol>
     *
     * <p>两类边统一在同一迭代循环中处理，直到无新边为止，正确覆盖多层依赖链。
     */
    private void enrichGraphWithLatestEdges(GraphModels.Graph graph) {
        Set<String> existingNodeIds = new HashSet<>();
        for (GraphModels.Node n : graph.nodes) {
            existingNodeIds.add(n.id != null ? n.id : (n.object + "." + n.field));
        }
        Set<String> existingEdgeKeys = new HashSet<>();
        for (GraphModels.Edge e : graph.edges) {
            existingEdgeKeys.add(e.source + "|" + e.type + "|" + e.target);
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            List<String> snapshot = new java.util.ArrayList<>(existingNodeIds);
            for (String nodeId : snapshot) {
                int dot = nodeId.indexOf('.');
                if (dot <= 0) continue;
                String obj = nodeId.substring(0, dot);
                String fld = nodeId.substring(dot + 1);

                BaseappObjectField def = getLatestFieldInfo(obj, fld);
                if (def == null) continue;

                // 1. writeBack 上游边：parseWriteBack 找来源字段，建 srcId →[writeBack]→ nodeId
                String wbExprStr = def.getWriteBackExpr();
                if (wbExprStr != null && !wbExprStr.trim().isEmpty()) {
                    WriteBackExpr wb = impactAnalyzerService.parseWriteBack(wbExprStr);
                    if (wb != null && wb.getSrcObjectType() != null && wb.getExpression() != null) {
                        String srcObj = wb.getSrcObjectType();
                        Set<String> srcFields = ExprUtils.extractCamelFieldsFromSql(wb.getExpression());
                        for (String srcFld : srcFields) {
                            String camelSrc = srcFld.contains("_") ? ExprUtils.snakeToCamel(srcFld) : srcFld;
                            String srcId = srcObj + "." + camelSrc;
                            if (existingEdgeKeys.add(srcId + "|writeBack|" + nodeId)) {
                                graph.edges.add(new GraphModels.Edge(srcId, nodeId, "writeBack"));
                                changed = true;
                            }
                            if (existingNodeIds.add(srcId)) {
                                graph.nodes.add(new GraphModels.Node(srcObj, camelSrc));
                                changed = true;
                            }
                        }
                    }
                }

                // 2. intra 上游边：从比较库 trigger/formula 表达式提取同对象引用字段，
                //    建 refId →[intra]→ nodeId，确保 writeBack 先于 trigger 执行
                String intrExpr = firstNonEmpty(def.getTriggerExpr(), def.getExpression(), def.getVirtualExpr());
                if (intrExpr != null && !intrExpr.trim().isEmpty()) {
                    Set<String> refs = ExprUtils.extractCamelFieldsFromSql(intrExpr);
                    for (String ref : refs) {
                        String camelRef = ref.contains("_") ? ExprUtils.snakeToCamel(ref) : ref;
                        // 只处理同对象的 intra 依赖
                        String refId = obj + "." + camelRef;
                        if (refId.equals(nodeId)) continue; // 排除自引用
                        if (existingEdgeKeys.add(refId + "|intra|" + nodeId)) {
                            graph.edges.add(new GraphModels.Edge(refId, nodeId, "intra"));
                            changed = true;
                        }
                        if (existingNodeIds.add(refId)) {
                            graph.nodes.add(new GraphModels.Node(obj, camelRef));
                            changed = true;
                        }
                    }
                }
            }
        }
    }

    private List<String> topologicalSort(GraphModels.Graph graph) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> outEdges = new HashMap<>();

        for (GraphModels.Node n : graph.nodes) {
            String id = n.id != null ? n.id : (n.object + "." + n.field);
            inDegree.put(id, inDegree.getOrDefault(id, 0));
            outEdges.putIfAbsent(id, new ArrayList<>());
        }
        for (GraphModels.Edge e : graph.edges) {
            inDegree.put(e.target, inDegree.getOrDefault(e.target, 0) + 1);
            outEdges.computeIfAbsent(e.source, k -> new ArrayList<>()).add(e.target);
        }

        Queue<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> ent : inDegree.entrySet()) {
            if (ent.getValue().intValue() == 0) queue.offer(ent.getKey());
        }
        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String u = queue.poll();
            order.add(u);
            for (String v : outEdges.getOrDefault(u, Collections.<String>emptyList())) {
                int d = inDegree.get(v).intValue() - 1;
                inDegree.put(v, Integer.valueOf(d));
                if (d == 0) queue.offer(v);
            }
        }
        return order;
    }

    private Map<String, EnumSet<RelType>> buildIncomingTypes(GraphModels.Graph graph, Set<RelType> included) {
        Map<String, EnumSet<RelType>> incoming = new HashMap<>();
        for (GraphModels.Edge e : graph.edges) {
            RelType t = RelType.fromCode(e.type);
            if (!included.contains(t)) continue;
            EnumSet<RelType> set = incoming.get(e.target);
            if (set == null) {
                set = EnumSet.noneOf(RelType.class);
                incoming.put(e.target, set);
            }
            set.add(t);
        }
        return incoming;
    }

    private Set<RelType> parseRelTypes(String relTypes) {
        Set<RelType> set = EnumSet.noneOf(RelType.class);
        if (relTypes == null || relTypes.trim().isEmpty()) {
            // 默认都包含
            set.add(RelType.INTRA);
            set.add(RelType.WRITE_BACK);
            // VIEW 通常不参与数据修复，先不加入
            return set;
        }
        String[] parts = relTypes.split(",");
        for (String raw : parts) {
            String t = raw.trim();
            if ("intra".equalsIgnoreCase(t)) set.add(RelType.INTRA);
            if ("writeBack".equalsIgnoreCase(t) || "writeback".equalsIgnoreCase(t)) set.add(RelType.WRITE_BACK);
            if ("view".equalsIgnoreCase(t)) set.add(RelType.VIEW);
        }
        if (set.isEmpty()) {
            set.add(RelType.INTRA);
            set.add(RelType.WRITE_BACK);
        }
        return set;
    }

    /**
     * 优先从「比较库最新定义」中查找字段，fallback 到本地 analyzerService。
     * 支持 camelCase / snake_case 两种 key 格式。
     */
    private BaseappObjectField getLatestFieldInfo(String objectType, String field) {
        Map<String, BaseappObjectField> latest = latestFieldDefsCtx.get();
        if (latest != null) {
            BaseappObjectField def = latest.get(objectType + "." + field);
            if (def != null) return def;
            // 尝试 camelCase 转换
            if (field != null && field.contains("_")) {
                def = latest.get(objectType + "." + ExprUtils.snakeToCamel(field));
                if (def != null) return def;
            }
        }
        // fallback：本地 in-memory
        BaseappObjectField def = impactAnalyzerService.getFieldInfo(objectType, field);
        if (def == null && field != null && field.contains("_")) {
            def = impactAnalyzerService.getFieldInfo(objectType, ExprUtils.snakeToCamel(field));
        }
        return def;
    }

    /**
     * 根据字段定义解析规范字段名：优先 apiName，否则 name，fallback 为调用方传入的字段名。
     */
    private String resolveCanonicalField(BaseappObjectField def, String requestedField) {
        if (def == null) return requestedField;
        if (def.getApiName() != null && !def.getApiName().trim().isEmpty()) {
            return def.getApiName().trim();
        }
        if (def.getName() != null && !def.getName().trim().isEmpty()) {
            return def.getName().trim();
        }
        return requestedField;
    }

    private void appendRootFieldPlaceholder(StringBuilder sb, String rootObject, String rootField) {
        sb.append("-- 第0步：根字段重算（请按新口径补充 SET 表达式）\n");
        sb.append("-- 下面示例使用 EQL 风格（对象名 + 驼峰字段名），请根据实际口径和范围改写：\n");
        sb.append("-- UPDATE ").append(rootObject).append("\n")
          .append("-- SET ").append(rootField).append(" = /* TODO: 新口径表达式 */\n")
          .append("-- WHERE /* TODO: 限定需要修复的范围，例如某个账期或批次 */;\n\n");
    }

    /**
     * 批量模式下的根字段占位说明：区分公式字段与基础字段，只提醒需要人工补充口径。
     */
    private void appendRootFieldPlaceholderShort(StringBuilder sb, String rootObject, String rootField) {
        BaseappObjectField def = getLatestFieldInfo(rootObject, rootField);
        String formula = def != null ? firstNonEmpty(def.getTriggerExpr(), def.getExpression(), def.getVirtualExpr()) : null;
        boolean isBaseField = formula == null || formula.trim().isEmpty();

        if (isBaseField) {
            sb.append("-- ROOT 基础字段（无公式）：").append(rootObject).append(".").append(rootField)
              .append("  请按新口径补充 UPDATE 或数据修复逻辑\n");
        } else {
            sb.append("-- ROOT 字段：").append(rootObject).append(".").append(rootField)
              .append("  请按新口径补充 UPDATE 语句（示例：）\n");
        }
        sb.append("-- UPDATE ").append(rootObject)
          .append(" SET ").append(rootField).append(" = /* TODO: 新口径表达式 */")
          .append(" WHERE /* TODO: 限定本次修复范围 */;\n\n");
    }

    /**
     * 获取某字段 writeBack 所依赖的来源节点 id 集合（srcObjectType.field）。
     * 仅当本批变更中包含这些来源之一时，才需要执行该回写 SQL。
     */
    private Set<String> getWriteBackSourceNodeIds(String objectType, String field) {
        BaseappObjectField def = getLatestFieldInfo(objectType, field);
        if (def == null || def.getWriteBackExpr() == null || def.getWriteBackExpr().trim().isEmpty()) {
            return Collections.emptySet();
        }
        WriteBackExpr wb = impactAnalyzerService.parseWriteBack(def.getWriteBackExpr());
        if (wb == null || wb.getSrcObjectType() == null || wb.getExpression() == null) {
            return Collections.emptySet();
        }
        Set<String> srcFields = ExprUtils.extractCamelFieldsFromSql(wb.getExpression());
        if (srcFields == null || srcFields.isEmpty()) {
            return Collections.emptySet();
        }
        String srcObj = wb.getSrcObjectType();
        Set<String> ids = new HashSet<>();
        for (String f : srcFields) {
            // 统一转为 camelCase，与 updatedSet 中的 nodeId 格式保持一致
            String camelF = f.contains("_") ? ExprUtils.snakeToCamel(f) : f;
            ids.add(srcObj + "." + camelF);
        }
        return ids;
    }

    /**
     * 计算每个节点的 depth（从 root 出发的最短步数，只走下游边）。
     */
    private Map<String, Integer> computeDepthMap(GraphModels.Graph graph,
                                                 String rootObject,
                                                 String rootField) {
        Map<String, Integer> depth = new HashMap<String, Integer>();
        Map<String, List<String>> adj = new HashMap<String, List<String>>();

        for (GraphModels.Edge e : graph.edges) {
            List<String> list = adj.get(e.source);
            if (list == null) {
                list = new ArrayList<String>();
                adj.put(e.source, list);
            }
            list.add(e.target);
        }

        Queue<String> queue = new ArrayDeque<String>();

        for (GraphModels.Node n : graph.nodes) {
            String id = n.id != null ? n.id : (n.object + "." + n.field);
            if (rootObject.equals(n.object) && rootField.equals(n.field)) {
                depth.put(id, Integer.valueOf(0));
                queue.offer(id);
            }
        }

        if (queue.isEmpty() && !graph.nodes.isEmpty()) {
            GraphModels.Node n0 = graph.nodes.get(0);
            String id0 = n0.id != null ? n0.id : (n0.object + "." + n0.field);
            depth.put(id0, Integer.valueOf(0));
            queue.offer(id0);
            log.warn("[UpgradeScript] root node not found for {}.{}, fallback to {}", rootObject, rootField, id0);
        }

        while (!queue.isEmpty()) {
            String cur = queue.poll();
            int d = depth.get(cur).intValue();
            List<String> outs = adj.get(cur);
            if (outs == null) continue;
            for (String next : outs) {
                if (!depth.containsKey(next)) {
                    depth.put(next, Integer.valueOf(d + 1));
                    queue.offer(next);
                }
            }
        }

        return depth;
    }

    private List<FixStep> buildFixSteps(GraphModels.Graph graph,
                                        Map<String, Integer> depthMap,
                                        Set<RelType> includedTypes) {
        Map<String, EnumSet<RelType>> incoming = new HashMap<String, EnumSet<RelType>>();

        for (GraphModels.Edge e : graph.edges) {
            RelType t = RelType.fromCode(e.type);
            if (!includedTypes.contains(t)) {
                // 不在关注范围的关系类型，直接忽略
                continue;
            }
            EnumSet<RelType> set = incoming.get(e.target);
            if (set == null) {
                set = EnumSet.noneOf(RelType.class);
                incoming.put(e.target, set);
            }
            set.add(t);
        }

        List<FixStep> result = new ArrayList<FixStep>();

        for (GraphModels.Node n : graph.nodes) {
            String id = n.id != null ? n.id : (n.object + "." + n.field);
            Integer d = depthMap.get(id);
            if (d == null) continue;
            EnumSet<RelType> types = incoming.get(id);
            if (types == null) {
                types = EnumSet.noneOf(RelType.class);
            }
            result.add(new FixStep(n.object, n.field, d.intValue(), types));
        }

        return result;
    }

    private void appendIntraSql(StringBuilder sb, FixStep step) {
        BaseappObjectField def = getLatestFieldInfo(step.objectType, step.field);
        if (def == null) {
            sb.append("-- [SKIP] 未找到字段定义: ").append(step.objectType).append(".").append(step.field).append("\n\n");
            return;
        }
        String formula = firstNonEmpty(def.getTriggerExpr(), def.getExpression(), def.getVirtualExpr());
        if (formula == null || formula.trim().isEmpty()) {
            sb.append("-- [SKIP] 字段无公式: ").append(step.objectType).append(".").append(step.field).append("\n\n");
            return;
        }

        sb.append("-- Trigger 字段: ").append(step.objectType).append(".").append(step.field);
        if (def.getTitle() != null) {
            sb.append(" (").append(def.getTitle()).append(")");
        }
        sb.append("\n");
        sb.append("-- 公式: ").append(formula).append("\n");

        // 获取表名（带前缀）和字段名（下划线格式）
        String tableName = objectTypeToTableName(step.objectType);
        String columnName = fieldCamelToColumnName(step.field, step.objectType);
        
        // 公式中的字段名也需要转换为下划线格式
        String formulaSnake = convertFormulaToSnakeCase(formula, step.objectType);

        // 直接生成 SQL（带前缀表名 + 下划线字段名）
        sb.append("UPDATE ").append(tableName).append("\n")
          .append("SET ").append(columnName).append(" = (").append(formulaSnake).append(")\n")
          .append("WHERE ").append(columnName).append(" IS NULL")
          .append(" OR ").append(columnName).append(" <> (").append(formulaSnake).append(");\n\n");
    }

    private void appendWriteBackSql(StringBuilder sb, FixStep step) {
        BaseappObjectField def = getLatestFieldInfo(step.objectType, step.field);
        if (def == null || def.getWriteBackExpr() == null || def.getWriteBackExpr().trim().isEmpty()) {
            sb.append("-- [SKIP] 字段无 writeBackExpr: ").append(step.objectType).append(".").append(step.field).append("\n\n");
            return;
        }

        WriteBackExpr wb = impactAnalyzerService.parseWriteBack(def.getWriteBackExpr());
        if (wb == null) {
            sb.append("-- [WARN] writeBackExpr 解析失败: ").append(step.objectType).append(".").append(step.field).append("\n\n");
            return;
        }

        sb.append("-- 回写: ").append(wb.getSrcObjectType()).append(" -> ")
          .append(step.objectType).append(".").append(step.field);
        if (def.getTitle() != null) {
            sb.append(" (").append(def.getTitle()).append(")");
        }
        sb.append("\n");

        // 调用外部接口生成 SQL
        String fieldPath = step.objectType + "." + step.field;
        String sql = callWriteBackSqlApi(fieldPath);
        
        if (sql != null && !sql.trim().isEmpty()) {
            sb.append(sql.trim()).append("\n\n");
        } else {
            sb.append("-- [WARN] 接口调用失败，无法生成 SQL: ").append(fieldPath).append("\n\n");
        }
    }

    /**
     * 调用外部接口生成回写字段的 SQL（使用 OkHttp，仅设置 Content-Type，不设置 Accept，避免 406）
     *
     * @param fieldPath 字段路径，格式：ObjectType.fieldName，例如：ArContractSubjectMatterItem.invoiceMakeAppAmountDir
     * @return SQL 字符串，如果调用失败返回 null
     */
    private String callWriteBackSqlApi(String fieldPath) {
        try {
            Map<String, String> body = new HashMap<String, String>();
            body.put("fieldPath", fieldPath);
            String json = objectMapper.writeValueAsString(body);

            Request.Builder builder = new Request.Builder()
                    .url(writeBackSqlApiUrl)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Tenant-Id", writeBackSqlTenantId)
                    .post(RequestBody.create(json, JSON));
            Request request = builder.build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("[UpgradeScript] 接口返回非 2xx: fieldPath={}, code={}", fieldPath, response.code());
                    return null;
                }
                ResponseBody responseBody = response.body();
                String sql = responseBody != null ? responseBody.string() : null;
                return normalizeWriteBackSql(sql);
            }
        } catch (IOException ex) {
            log.error("[UpgradeScript] 调用回写 SQL 接口失败: fieldPath={}, error={}", fieldPath, ex.getMessage(), ex);
            return null;
        } catch (Exception ex) {
            log.error("[UpgradeScript] 调用回写 SQL 接口失败: fieldPath={}, error={}", fieldPath, ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * 对回写接口返回的 SQL 做后处理：去掉首尾引号；去掉结尾的 WHERE m.id IN (#{targetIds[0]})
     */
    private String normalizeWriteBackSql(String sql) {
        if (sql == null) return null;
        String s = sql.trim();
        if (s.isEmpty()) return s;
        // 去掉首尾双引号（接口可能返回带引号的字符串）
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        // 去掉结尾的 WHERE m.id IN (#{targetIds[0]})
        String suffix = " WHERE m.id IN (#{targetIds[0]})";
        if (s.endsWith(suffix)) {
            s = s.substring(0, s.length() - suffix.length()).trim();
        }
        return s;
    }

    private String firstNonEmpty(String... arr) {
        if (arr == null) return null;
        for (String s : arr) {
            if (s != null && !s.trim().isEmpty()) return s.trim();
        }
        return null;
    }

    /**
     * 将对象类型转换为表名（带前缀）
     * 例如：InvoiceApplicationItem -> arap_invoice_application_item
     */
    private String objectTypeToTableName(String objectType) {
        if (objectType == null || objectType.trim().isEmpty()) {
            return "";
        }
        String appName = mapper.selectAppNameByObjectType(objectType);
        String snake = ExprUtils.camelToSnake(objectType);
        if (appName != null && !appName.trim().isEmpty()) {
            return appName.trim() + "_" + snake;
        }
        return snake;
    }

    /**
     * 将驼峰字段名转换为数据库列名（下划线格式）
     * 优先使用 BaseappObjectField.name（如果存在且是下划线格式），否则转换为下划线格式
     */
    private String fieldCamelToColumnName(String fieldCamel, String objectType) {
        BaseappObjectField def = getLatestFieldInfo(objectType, fieldCamel);
        if (def != null && def.getName() != null && !def.getName().trim().isEmpty()) {
            String name = def.getName().trim();
            // 如果 name 已经是下划线格式（包含下划线），直接使用
            if (name.contains("_")) {
                return name;
            }
            // 如果 name 是驼峰格式，转换为下划线格式
            return ExprUtils.camelToSnake(name);
        }
        // 如果没有 name，将输入的驼峰字段名转换为下划线格式
        return ExprUtils.camelToSnake(fieldCamel);
    }

    /**
     * 将公式中的字段名转换为下划线格式
     * 例如：closedStatus -> closed_status, makeInvoiceAmount -> make_invoice_amount
     */
    private String convertFormulaToSnakeCase(String formula, String objectType) {
        if (formula == null || formula.trim().isEmpty()) {
            return formula;
        }
        
        // 从公式中提取所有字段名（使用 ExprUtils 的方法）
        Set<String> camelFields = ExprUtils.extractCamelFieldsFromSql(formula);
        
        // 构建字段名映射（驼峰 -> 下划线）
        Map<String, String> fieldMap = new HashMap<String, String>();
        for (String camelField : camelFields) {
            // 尝试通过 getFieldInfo 获取字段定义
            BaseappObjectField def = getLatestFieldInfo(objectType, camelField);
            if (def != null && def.getName() != null && !def.getName().trim().isEmpty()) {
                // 如果 name 已经是下划线格式，直接使用
                fieldMap.put(camelField, def.getName().trim());
            } else {
                // 否则转换为下划线格式
                fieldMap.put(camelField, ExprUtils.camelToSnake(camelField));
            }
        }
        
        // 替换公式中的字段名
        String result = formula;
        // 按长度从长到短排序，避免短字段名被长字段名的一部分替换
        List<Map.Entry<String, String>> sorted = new ArrayList<Map.Entry<String, String>>(fieldMap.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<String, String>>() {
            @Override
            public int compare(Map.Entry<String, String> a, Map.Entry<String, String> b) {
                return Integer.compare(b.getKey().length(), a.getKey().length());
            }
        });
        
        for (Map.Entry<String, String> e : sorted) {
            String camel = e.getKey();
            String snake = e.getValue();
            // 使用单词边界匹配，避免部分替换
            // 匹配：字段名前后是空格、括号、逗号、运算符等
            String pattern = "\\b" + java.util.regex.Pattern.quote(camel) + "\\b";
            result = result.replaceAll(pattern, snake);
        }
        
        return result;
    }

}
