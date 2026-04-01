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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

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

    /** 匹配接口返回 SQL 末尾的 MyBatis targetIds 占位符，兼容索引变化 */
    private static final Pattern MYBATIS_TARGET_IDS_PATTERN =
            Pattern.compile("\\s+WHERE\\s+m\\.id\\s+IN\\s*\\(#\\{targetIds\\[\\d+]\\}\\)\\s*$", Pattern.CASE_INSENSITIVE);

    @Value("${writeback-sql.api-url:http://arap.test-tx-16.e7link.com/arap/gen/debug/writeBackField2sql}")
    private String writeBackSqlApiUrl;
    @Value("${writeback-sql.tenant-id:711FNX50G6V0009}")
    private String writeBackSqlTenantId;

    private final ImpactAnalyzerService impactAnalyzerService;
    private final BaseappObjectFieldMapper mapper;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    /** objectType → appName 缓存，避免 N+1 DB 查询 */
    private final Map<String, String> appNameCache = new ConcurrentHashMap<>();

    public UpgradeScriptService(ImpactAnalyzerService impactAnalyzerService,
                                BaseappObjectFieldMapper mapper,
                                OkHttpClient httpClient) {
        this.impactAnalyzerService = impactAnalyzerService;
        this.mapper = mapper;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

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

    /** UPDATE 语句中一个 SET 子句（col = expr）。 */
    static class SetClause {
        String columnName;   // 列名，如 "amount" 或 "tax_amount"
        String expression;   // 右侧表达式，如 "(price * qty)" 或 "(SELECT ...)"
        String originalText; // 原始文本 "col = expr"
    }

    /**
     * 解析后的 SQL 语句块：若干注释行 + 一条 SQL 语句（以 ';' 结尾）。
     * isUpdate=true 时解析了表名、FROM/WHERE 子句和 SET 子句列表，可参与合并。
     *
     * <p>支持两种 UPDATE 格式：
     * <ol>
     *   <li>相关子查询格式：{@code UPDATE t SET col=(SELECT ... FROM src WHERE ...)}</li>
     *   <li>PostgreSQL UPDATE...FROM 格式：{@code UPDATE t SET col=src.x FROM src WHERE t.id=src.fk}</li>
     * </ol>
     * 两种格式都能正确提取 SET/FROM/WHERE 各段，合并时要求三者均一致。
     */
    static class StatementBlock {
        List<String> commentLines = new ArrayList<>();
        String sqlStatement = null; // 完整 SQL 文本（不含尾部换行），null 表示纯注释块
        boolean isUpdate = false;
        String  tableName      = null;
        String  tableAlias     = null;  // 表别名，null 表示无别名
        boolean tableAliasAs   = false; // 别名是否使用了 AS 关键字（如 UPDATE t AS m）
        String  fromClause     = null;  // "" = 无顶层 FROM；非空 = UPDATE...FROM 格式
        String  whereClause    = null;  // "" = 无 WHERE；null = 未解析
        List<SetClause> setClauses = new ArrayList<>();

        /** 重新序列化为文本（注释 + SQL + 空行分隔符）。 */
        String render() {
            StringBuilder sb = new StringBuilder();
            for (String c : commentLines) sb.append(c).append("\n");
            if (sqlStatement != null) {
                sb.append(sqlStatement).append("\n");
                sb.append("\n");
            } else if (!commentLines.isEmpty()) {
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * 生成升级 SQL 脚本（单根字段）。
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

        // [FIX P1-6] 与批量方法保持一致：规范化节点 ID + 补全比较库边
        normalizeGraphNodeIdsToCamelCase(graph);
        enrichGraphWithLatestEdges(graph);

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

        appendRootFieldPlaceholder(sb, rootObject, rootField);

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
                continue;
            }
            List<FixStep> list = e.getValue();
            if (list.isEmpty()) continue;

            sb.append("\n-- =========================================\n");
            sb.append("-- depth ").append(depth).append("\n");
            sb.append("-- =========================================\n\n");

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
     * 批量生成升级脚本（不带比较库定义，兼容旧调用）。
     *
     * @param roots    勾选的根字段列表，每项为 (objectType, field)
     * @param maxDepth 下游层级深度
     * @param relTypes 关系类型，例如 "intra,writeBack"
     */
    public String generateUpgradeScriptBatch(List<Map.Entry<String, String>> roots,
                                             int maxDepth,
                                             String relTypes) {
        return generateUpgradeScriptBatch(roots, maxDepth, relTypes, null, true);
    }

    /**
     * 批量生成升级脚本。
     *
     * @param latestFieldDefs 比较库的最新字段定义 map（key = "ObjectType.camelField"），
     *                        为 null 时退化为使用本地 in-memory 数据（兼容旧行为）。
     * @param includeComments 是否在输出 SQL 中保留注释行（以 "--" 开头的行）；
     *                        false 时自动去除所有注释，输出更精简的纯 SQL。
     */
    public String generateUpgradeScriptBatch(List<Map.Entry<String, String>> roots,
                                             int maxDepth,
                                             String relTypes,
                                             Map<String, BaseappObjectField> latestFieldDefs,
                                             boolean includeComments) {
        if (roots == null || roots.isEmpty()) {
            return "-- 未选择任何根字段\n";
        }
        latestFieldDefsCtx.set(latestFieldDefs);
        try {
            String result = doGenerateUpgradeScriptBatch(roots, maxDepth, relTypes);
            result = mergeConsecutiveUpdates(result);
            return includeComments ? result : stripComments(result);
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

        List<Map.Entry<String, String>> normalizedRoots = new java.util.ArrayList<>();
        for (Map.Entry<String, String> r : roots) {
            String obj = r.getKey();
            String fld = r.getValue();
            if (obj == null || fld == null) { normalizedRoots.add(r); continue; }
            BaseappObjectField def = getLatestFieldInfo(obj, fld);
            if (def == null && fld.contains("_")) {
                String camelFld = ExprUtils.snakeToCamel(fld);
                def = getLatestFieldInfo(obj, camelFld);
                if (def != null) { fld = camelFld; }
            }
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
        // [FIX P1-5] 使用独立方法计算 relType，正确处理 VIEW 等 edge case
        int relType = computeGraphRelType(included);

        GraphModels.Graph graph = impactAnalyzerService.buildMultiRootClosedGraph(roots, maxDepth, relType);
        if (graph.nodes == null || graph.nodes.isEmpty()) {
            return "-- 未找到任何影响关系（多根合并图为空）\n";
        }

        normalizeGraphNodeIdsToCamelCase(graph);
        enrichGraphWithLatestEdges(graph);

        Set<String> rootIds = new HashSet<>();
        for (Map.Entry<String, String> r : roots) {
            if (r.getKey() != null && r.getValue() != null) {
                rootIds.add(r.getKey() + "." + r.getValue());
            }
        }

        // [FIX P2-8] 收集循环依赖节点，在 SQL 输出中添加 WARNING
        Set<String> cycleNodes = new LinkedHashSet<>();
        List<String> topoOrder = topologicalSort(graph, cycleNodes);
        Map<String, EnumSet<RelType>> incomingTypes = buildIncomingTypes(graph, included);

        Set<String> updatedSet = computeUpdatedNodes(topoOrder, rootIds, incomingTypes, included, graph);

        StringBuilder sb = new StringBuilder();
        sb.append("-- =========================================\n");
        sb.append("-- 批量字段升级脚本（").append(roots.size()).append(" 个根字段）\n");
        sb.append("-- 说明：先按依赖拓扑顺序列出批量更新逻辑与执行顺序，再输出对应 SQL。\n");
        sb.append("-- =========================================\n\n");

        // [FIX P2-8] 在脚本头部输出循环依赖 WARNING
        if (!cycleNodes.isEmpty()) {
            sb.append("-- WARNING: 检测到依赖环，以下节点存在循环依赖，生成的 SQL 顺序可能不正确，请人工审阅：\n");
            for (String cn : cycleNodes) {
                sb.append("--   - ").append(cn).append("\n");
            }
            sb.append("\n");
        }

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

            boolean isCycleNode = cycleNodes.contains(nodeId);

            if (rootIds.contains(nodeId)) {
                BaseappObjectField rootDef = getLatestFieldInfo(objectType, field);
                String canonicalField = resolveCanonicalField(rootDef, field);

                boolean rootIsWriteBack = rootDef != null && rootDef.getWriteBackExpr() != null
                        && !rootDef.getWriteBackExpr().trim().isEmpty();
                String rootFormula = rootDef != null
                        ? firstNonEmpty(rootDef.getTriggerExpr(), rootDef.getExpression(), rootDef.getVirtualExpr())
                        : null;
                boolean rootIsTrigger = rootFormula != null && !rootFormula.trim().isEmpty();

                if (rootIsWriteBack && included.contains(RelType.WRITE_BACK)) {
                    sb.append("-- ROOT 回写字段（先回写填充）：")
                      .append(objectType).append(".").append(canonicalField).append("\n");
                    appendWriteBackSql(sb, new FixStep(objectType, canonicalField, -1, EnumSet.of(RelType.WRITE_BACK)));
                } else if (rootIsTrigger && included.contains(RelType.INTRA)) {
                    sb.append("-- ROOT Trigger 字段（含定义变更）：")
                      .append(objectType).append(".").append(canonicalField).append("\n");
                    appendIntraSql(sb, new FixStep(objectType, canonicalField, -1, EnumSet.of(RelType.INTRA)));
                } else {
                    appendRootFieldPlaceholderShort(sb, objectType, canonicalField);
                }
                continue;
            }
            if (types.contains(RelType.INTRA) && included.contains(RelType.INTRA)) {
                if (updatedSet.contains(nodeId)) {
                    if (isCycleNode) {
                        sb.append("-- WARNING: 以下节点处于依赖环中，SQL 执行顺序可能不正确\n");
                    }
                    appendIntraSql(sb, new FixStep(objectType, field, -1, types));
                } else {
                    sb.append("-- [SKIP] Trigger 字段 ").append(objectType).append(".").append(field)
                      .append(" 的上游字段本批无数据变化，无需执行\n\n");
                }
            }
            if (types.contains(RelType.WRITE_BACK) && included.contains(RelType.WRITE_BACK)) {
                if (updatedSet.contains(nodeId)) {
                    if (isCycleNode) {
                        sb.append("-- WARNING: 以下节点处于依赖环中，SQL 执行顺序可能不正确\n");
                    }
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
     * 根据 included 关系类型集合，计算传给 buildMultiRootClosedGraph / analyze 的 relType 整数值。
     * relType: 0=全部(含view), 1=writeBack, 2=intra, 4=intra+writeBack(不含view)
     */
    private int computeGraphRelType(Set<RelType> included) {
        boolean wantIntra = included.contains(RelType.INTRA);
        boolean wantWb = included.contains(RelType.WRITE_BACK);
        boolean wantView = included.contains(RelType.VIEW);

        if (wantView) {
            return 0;
        }
        if (wantIntra && wantWb) return 4;
        if (wantIntra) return 2;
        if (wantWb) return 1;
        return 4;
    }

    private Set<String> computeUpdatedNodes(List<String> topoOrder,
                                            Set<String> rootIds,
                                            Map<String, EnumSet<RelType>> incomingTypes,
                                            Set<RelType> included,
                                            GraphModels.Graph graph) {
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

            if (types.contains(RelType.INTRA) && included.contains(RelType.INTRA)) {
                Set<String> intraSources = intraSourcesMap.getOrDefault(nodeId, Collections.emptySet());
                if (intraSources.stream().anyMatch(updated::contains)) {
                    updated.add(nodeId);
                }
            }
            if (types.contains(RelType.WRITE_BACK) && included.contains(RelType.WRITE_BACK)) {
                Set<String> wbSources = getWriteBackSourceNodeIds(objectType, field);
                if (wbSources != null && wbSources.stream().anyMatch(updated::contains)) {
                    updated.add(nodeId);
                }
            }
        }
        return updated;
    }

    /**
     * 将图中所有节点的字段名统一规范化为 camelCase，并对重复节点/边去重。
     */
    private void normalizeGraphNodeIdsToCamelCase(GraphModels.Graph graph) {
        Map<String, String> idRemap = new HashMap<>();
        for (GraphModels.Node n : graph.nodes) {
            String oldId = n.id != null ? n.id : (n.object + "." + n.field);
            String fld = n.field != null ? n.field : "";
            String camelFld;
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

        for (GraphModels.Edge e : graph.edges) {
            String remapped;
            if ((remapped = idRemap.get(e.source)) != null) e.source = remapped;
            if ((remapped = idRemap.get(e.target)) != null) e.target = remapped;
        }

        Set<String> seenIds = new HashSet<>();
        List<GraphModels.Node> dedupNodes = new java.util.ArrayList<>();
        for (GraphModels.Node n : graph.nodes) {
            String id = n.id != null ? n.id : (n.object + "." + n.field);
            if (seenIds.add(id)) dedupNodes.add(n);
        }
        graph.nodes.clear();
        graph.nodes.addAll(dedupNodes);

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
     *       {@code getDirectUpstreamEdges} 找到其 writeBack 来源。</li>
     *   <li><b>intra 上游边</b>：trigger/formula 表达式在比较库中新增了引用，但基础库未含该引用。
     *       注意：仅当引用字段确认属于同对象时才建 intra 边，避免跨对象字段被误识别。</li>
     * </ol>
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

                // 2. intra 上游边：从比较库 trigger/formula 表达式提取引用字段
                // [FIX P0-2] 仅当引用字段确认存在于同对象中时才建 intra 边，
                //            避免跨对象引用（如 srcObj.amount）被误识别为同对象 intra 依赖
                String intrExpr = firstNonEmpty(def.getTriggerExpr(), def.getExpression(), def.getVirtualExpr());
                if (intrExpr != null && !intrExpr.trim().isEmpty()) {
                    Set<String> refs = ExprUtils.extractCamelFieldsFromSql(intrExpr);
                    for (String ref : refs) {
                        String camelRef = ref.contains("_") ? ExprUtils.snakeToCamel(ref) : ref;
                        String refId = obj + "." + camelRef;
                        if (refId.equals(nodeId)) continue;
                        if (getLatestFieldInfo(obj, camelRef) == null) continue;
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

    /**
     * 对依赖图做拓扑排序（Kahn 算法），依赖在前、被依赖在后。
     *
     * @param outCycleNodes 如果非 null，检测到的循环依赖节点将被收集到此集合中
     */
    private List<String> topologicalSort(GraphModels.Graph graph, Set<String> outCycleNodes) {
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
        // 检测环：Kahn 算法结束后入度仍 > 0 的节点处于环中
        Set<String> detected = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> ent : inDegree.entrySet()) {
            if (ent.getValue().intValue() > 0) {
                detected.add(ent.getKey());
            }
        }
        if (!detected.isEmpty()) {
            log.warn("[UpgradeScript] 检测到依赖环，以下节点入度无法降为 0: {}", detected);
            order.addAll(detected);
            if (outCycleNodes != null) {
                outCycleNodes.addAll(detected);
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
            set.add(RelType.INTRA);
            set.add(RelType.WRITE_BACK);
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

    private BaseappObjectField getLatestFieldInfo(String objectType, String field) {
        Map<String, BaseappObjectField> latest = latestFieldDefsCtx.get();
        if (latest != null) {
            BaseappObjectField def = latest.get(objectType + "." + field);
            if (def != null) return def;
            if (field != null && field.contains("_")) {
                def = latest.get(objectType + "." + ExprUtils.snakeToCamel(field));
                if (def != null) return def;
            }
        }
        BaseappObjectField def = impactAnalyzerService.getFieldInfo(objectType, field);
        if (def == null && field != null && field.contains("_")) {
            def = impactAnalyzerService.getFieldInfo(objectType, ExprUtils.snakeToCamel(field));
        }
        return def;
    }

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

    private void appendRootFieldPlaceholderShort(StringBuilder sb, String rootObject, String rootField) {
        BaseappObjectField def = getLatestFieldInfo(rootObject, rootField);
        String triggerExpr = def != null ? def.getTriggerExpr() : null;
        String expr = def != null ? def.getExpression() : null;
        String virtualExpr = def != null ? def.getVirtualExpr() : null;

        boolean hasTriggerOrExpr = def != null
                && ((triggerExpr != null && !triggerExpr.trim().isEmpty())
                    || (expr != null && !expr.trim().isEmpty()));
        boolean hasVirtualOnly = def != null
                && !hasTriggerOrExpr
                && virtualExpr != null
                && !virtualExpr.trim().isEmpty();

        if (hasVirtualOnly) {
            sb.append("-- ROOT 虚拟字段(virtualExpr)，数据库无物理列：")
              .append(rootObject).append(".").append(rootField)
              .append("  本次升级无需对该字段生成 SQL\n\n");
            return;
        }

        String formula = def != null ? firstNonEmpty(triggerExpr, expr, virtualExpr) : null;
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
            String camelF = f.contains("_") ? ExprUtils.snakeToCamel(f) : f;
            ids.add(srcObj + "." + camelF);
        }
        return ids;
    }

    /**
     * 计算每个节点的 depth（从 root 出发的最长路径步数）。
     * [FIX P0-1] 使用拓扑排序 + DP 求最长路径，替代 BFS 最短路径，
     * 确保 DAG 中有多条路径到达同一节点时取最大 depth，保证依赖顺序正确。
     */
    private Map<String, Integer> computeDepthMap(GraphModels.Graph graph,
                                                 String rootObject,
                                                 String rootField) {
        Map<String, List<String>> adj = new HashMap<String, List<String>>();
        for (GraphModels.Edge e : graph.edges) {
            adj.computeIfAbsent(e.source, k -> new ArrayList<String>()).add(e.target);
        }

        List<String> topoOrder = topologicalSort(graph, null);

        String rootId = null;
        for (GraphModels.Node n : graph.nodes) {
            String id = n.id != null ? n.id : (n.object + "." + n.field);
            if (rootObject.equals(n.object) && rootField.equals(n.field)) {
                rootId = id;
                break;
            }
        }
        if (rootId == null && !graph.nodes.isEmpty()) {
            GraphModels.Node n0 = graph.nodes.get(0);
            rootId = n0.id != null ? n0.id : (n0.object + "." + n0.field);
            log.warn("[UpgradeScript] root node not found for {}.{}, fallback to {}", rootObject, rootField, rootId);
        }

        Map<String, Integer> depth = new HashMap<String, Integer>();
        if (rootId != null) {
            depth.put(rootId, Integer.valueOf(0));
        }

        for (String u : topoOrder) {
            if (!depth.containsKey(u)) continue;
            int d = depth.get(u).intValue();
            for (String v : adj.getOrDefault(u, Collections.<String>emptyList())) {
                int newD = d + 1;
                Integer prev = depth.get(v);
                if (prev == null || prev.intValue() < newD) {
                    depth.put(v, Integer.valueOf(newD));
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

        String triggerExpr = def.getTriggerExpr();
        String expr = def.getExpression();
        String virtualExpr = def.getVirtualExpr();

        boolean hasTriggerOrExpr = (triggerExpr != null && !triggerExpr.trim().isEmpty())
                                   || (expr != null && !expr.trim().isEmpty());
        boolean hasVirtualOnly = !hasTriggerOrExpr
                                 && virtualExpr != null
                                 && !virtualExpr.trim().isEmpty();
        if (hasVirtualOnly) {
            sb.append("-- [SKIP] 虚拟字段(virtualExpr)，数据库无物理列，无需生成 SQL: ")
              .append(step.objectType).append(".").append(step.field).append("\n\n");
            return;
        }

        String formula = firstNonEmpty(triggerExpr, expr, virtualExpr);
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

        String tableName = objectTypeToTableName(step.objectType);
        String columnName = fieldCamelToColumnName(step.field, step.objectType);
        String formulaSnake = convertFormulaToSnakeCase(formula, step.objectType);

        // [FIX P1-3] 升级脚本全量刷新，不加 WHERE 过滤条件，
        // 避免 formula 结果为 NULL 时因 SQL 三值逻辑（column <> NULL → NULL）导致漏更新
        sb.append("UPDATE ").append(tableName).append("\n")
          .append("SET ").append(columnName).append(" = (").append(formulaSnake).append(");\n\n");
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

        String fieldPath = step.objectType + "." + step.field;
        String sql = callWriteBackSqlApi(fieldPath);
        
        if (sql != null && !sql.trim().isEmpty()) {
            sb.append(ensureSqlEndsWithSemicolon(sql.trim())).append("\n\n");
        } else {
            sb.append("-- [WARN] 接口调用失败，无法生成 SQL: ").append(fieldPath).append("\n\n");
        }
    }

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
        // [FIX P2-9] 合并重复的 IOException / Exception catch 块
        } catch (Exception ex) {
            log.error("[UpgradeScript] 调用回写 SQL 接口失败: fieldPath={}, error={}", fieldPath, ex.getMessage(), ex);
            return null;
        }
    }

    private String ensureSqlEndsWithSemicolon(String sql) {
        if (sql == null) return null;
        String s = sql.trim();
        if (s.isEmpty()) return s;
        return s.endsWith(";") ? s : s + ";";
    }

    /**
     * 对回写接口返回的 SQL 做后处理：去掉首尾引号；去掉结尾的 MyBatis targetIds 占位符。
     * [FIX P2-11] 使用正则匹配替代硬编码后缀，兼容索引号变化
     */
    private String normalizeWriteBackSql(String sql) {
        if (sql == null) return null;
        String s = sql.trim();
        if (s.isEmpty()) return s;
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        s = MYBATIS_TARGET_IDS_PATTERN.matcher(s).replaceFirst("").trim();
        return s;
    }

    // ─── 连续同条件 UPDATE 合并（后处理优化）────────────────────────────────────

    /**
     * 后处理优化：扫描生成的 SQL 脚本，将连续出现的同表同 WHERE 条件的 UPDATE 语句
     * 合并为一条（多个 SET 子句）。
     *
     * <p>安全性保证：
     * <ul>
     *   <li>仅合并<b>连续</b>出现的满足条件的语句，保持原有执行顺序不变。</li>
     *   <li>若后一条语句的 SET 表达式引用了前一条语句正在更新的列（或反之），
     *       则不合并（避免 SQL 单条 UPDATE 中多个 SET 表达式均基于行旧值求值的语义问题）。</li>
     * </ul>
     */
    private String mergeConsecutiveUpdates(String sqlScript) {
        if (sqlScript == null || sqlScript.isEmpty()) return sqlScript;
        List<StatementBlock> blocks = parseStatementBlocks(sqlScript);
        if (blocks.size() <= 1) return sqlScript;

        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < blocks.size()) {
            StatementBlock cur = blocks.get(i);
            if (!cur.isUpdate || cur.setClauses.isEmpty()) {
                result.append(cur.render());
                i++;
                continue;
            }

            // 尝试向后合并连续满足条件的 UPDATE 块
            List<StatementBlock> group = new ArrayList<>();
            group.add(cur);
            int j = i + 1;
            while (j < blocks.size()) {
                StatementBlock next = blocks.get(j);
                if (!next.isUpdate || next.setClauses.isEmpty()) break;
                if (!cur.tableName.equalsIgnoreCase(next.tableName)) break;
                // 别名兼容性：两者必须相同，或其中一方无别名
                // 一方有别名、另一方无别名时可合并（合并后使用有别名的那一方）
                // 两者均有别名但不同时不可合并（子查询中的 alias.col 引用无法调和）
                String curAlias  = cur.tableAlias  != null ? cur.tableAlias  : "";
                String nextAlias = next.tableAlias != null ? next.tableAlias : "";
                if (!curAlias.isEmpty() && !nextAlias.isEmpty()
                        && !curAlias.equalsIgnoreCase(nextAlias)) break;
                // FROM 子句必须完全相同（空 = 相关子查询格式；非空 = UPDATE...FROM 格式）
                String curFrom  = cur.fromClause  != null ? cur.fromClause  : "";
                String nextFrom = next.fromClause != null ? next.fromClause : "";
                if (!curFrom.equals(nextFrom)) break;
                // WHERE 子句必须完全相同
                String curWhere  = cur.whereClause  != null ? cur.whereClause  : "";
                String nextWhere = next.whereClause != null ? next.whereClause : "";
                if (!curWhere.equals(nextWhere)) break;
                if (hasSetColumnConflict(group, next)) break;
                group.add(next);
                j++;
            }

            if (group.size() == 1) {
                result.append(cur.render());
            } else {
                result.append(buildMergedUpdate(group));
            }
            i = j;
        }
        return result.toString();
    }

    /**
     * 将 SQL 脚本文本解析为 {@link StatementBlock} 列表。
     * 每个块由若干注释行 + 一条以 ';' 结尾的 SQL 语句组成。
     */
    private List<StatementBlock> parseStatementBlocks(String sqlScript) {
        List<StatementBlock> blocks = new ArrayList<>();
        String[] lines = sqlScript.split("\n", -1);

        List<String> commentBuffer = new ArrayList<>();
        StringBuilder sqlBuffer    = new StringBuilder();
        boolean inSql = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (inSql) {
                sqlBuffer.append("\n").append(line);
                if (trimmed.endsWith(";")) {
                    StatementBlock b = new StatementBlock();
                    b.commentLines.addAll(commentBuffer);
                    b.sqlStatement = sqlBuffer.toString().trim();
                    parseUpdateStatement(b);
                    blocks.add(b);
                    commentBuffer.clear();
                    sqlBuffer.setLength(0);
                    inSql = false;
                }
            } else if (trimmed.isEmpty()) {
                if (!commentBuffer.isEmpty()) {
                    StatementBlock b = new StatementBlock();
                    b.commentLines.addAll(commentBuffer);
                    blocks.add(b);
                    commentBuffer.clear();
                }
            } else if (trimmed.startsWith("--")) {
                commentBuffer.add(line);
            } else {
                // SQL 起始行
                sqlBuffer.setLength(0);
                sqlBuffer.append(line);
                inSql = true;
                if (trimmed.endsWith(";")) {
                    StatementBlock b = new StatementBlock();
                    b.commentLines.addAll(commentBuffer);
                    b.sqlStatement = sqlBuffer.toString().trim();
                    parseUpdateStatement(b);
                    blocks.add(b);
                    commentBuffer.clear();
                    sqlBuffer.setLength(0);
                    inSql = false;
                }
            }
        }
        // 收尾
        if (!commentBuffer.isEmpty() || sqlBuffer.length() > 0) {
            StatementBlock b = new StatementBlock();
            b.commentLines.addAll(commentBuffer);
            if (sqlBuffer.length() > 0) {
                b.sqlStatement = sqlBuffer.toString().trim();
                parseUpdateStatement(b);
            }
            if (!b.commentLines.isEmpty() || b.sqlStatement != null) {
                blocks.add(b);
            }
        }
        return blocks;
    }

    /**
     * 解析 UPDATE 语句，提取表名、别名、FROM 子句、WHERE 子句和 SET 子句列表。
     * 仅当 {@code sqlStatement} 以 UPDATE 开头时才解析，否则保持 isUpdate=false。
     *
     * <p>兼容两种格式：
     * <ul>
     *   <li>相关子查询：{@code UPDATE t SET col=(SELECT ... FROM src WHERE ...)} —— FROM/WHERE 在括号内，顶层无 FROM</li>
     *   <li>PostgreSQL UPDATE...FROM：{@code UPDATE t SET col=src.x FROM src WHERE t.id=src.fk} —— 顶层有 FROM</li>
     * </ul>
     */
    private void parseUpdateStatement(StatementBlock block) {
        if (block.sqlStatement == null) return;
        String sql = block.sqlStatement.trim();
        if (!sql.toUpperCase().startsWith("UPDATE")) return;
        block.isUpdate = true;

        // "UPDATE " 之后的部分：tableName [alias]\nSET ...
        String rest = sql.substring(6).trim();

        // 在 rest 中找顶层 SET 关键字
        int setIdx = findTopLevelKeyword(rest, 0, "SET");
        if (setIdx < 0) return;

        // 解析表名和别名（SET 前的部分，按空白分割）
        // 支持三种格式：
        //   "table"          → 无别名
        //   "table alias"    → 无 AS 关键字的别名
        //   "table AS alias" → 含 AS 关键字的别名
        String tableAndAlias = rest.substring(0, setIdx).trim();
        String[] tParts = tableAndAlias.split("\\s+");
        block.tableName = tParts[0];
        if (tParts.length == 3 && "AS".equalsIgnoreCase(tParts[1])) {
            block.tableAlias   = tParts[2];
            block.tableAliasAs = true;
        } else if (tParts.length == 2) {
            block.tableAlias   = tParts[1];
            block.tableAliasAs = false;
        } else {
            block.tableAlias   = null;
            block.tableAliasAs = false;
        }

        // SET 之后的内容
        String afterSet = rest.substring(setIdx + 3).trim(); // skip "SET"

        // 找顶层 FROM 和 WHERE（括号内的不算）
        int fromIdx  = findTopLevelKeyword(afterSet, 0, "FROM");
        int whereIdx = findTopLevelKeyword(afterSet, 0, "WHERE");

        // 判断是否为 UPDATE...FROM...WHERE 格式：顶层 FROM 出现在 WHERE 之前
        boolean hasTopLevelFrom = fromIdx >= 0 && (whereIdx < 0 || fromIdx < whereIdx);

        String setSection;
        if (hasTopLevelFrom) {
            // UPDATE t SET col=src.x FROM src WHERE ...
            // setSection = col=src.x（不含 FROM 及后续内容）
            setSection = afterSet.substring(0, fromIdx).trim();
            String afterFrom = afterSet.substring(fromIdx + 4).trim();
            int whereInFrom = findTopLevelKeyword(afterFrom, 0, "WHERE");
            if (whereInFrom >= 0) {
                block.fromClause = afterFrom.substring(0, whereInFrom).trim();
                String afterWhere = afterFrom.substring(whereInFrom + 5).trim();
                if (afterWhere.endsWith(";")) afterWhere = afterWhere.substring(0, afterWhere.length() - 1).trim();
                block.whereClause = afterWhere;
            } else {
                String fromRest = afterFrom;
                if (fromRest.endsWith(";")) fromRest = fromRest.substring(0, fromRest.length() - 1).trim();
                block.fromClause  = fromRest;
                block.whereClause = "";
            }
        } else {
            // 相关子查询格式：FROM/WHERE 都在括号内，顶层只可能有 WHERE
            block.fromClause = "";
            if (whereIdx >= 0) {
                setSection = afterSet.substring(0, whereIdx).trim();
                String afterWhere = afterSet.substring(whereIdx + 5).trim();
                if (afterWhere.endsWith(";")) afterWhere = afterWhere.substring(0, afterWhere.length() - 1).trim();
                block.whereClause = afterWhere;
            } else {
                setSection = afterSet;
                if (setSection.endsWith(";")) setSection = setSection.substring(0, setSection.length() - 1).trim();
                block.whereClause = "";
            }
        }

        block.setClauses = parseSetClauses(setSection);
    }

    /**
     * 在 {@code text} 中从 {@code startFrom} 开始查找顶层（括号深度为 0）的 SQL 关键字。
     * 关键字前后必须是非字母/数字字符，避免误匹配子字符串。
     *
     * @return 关键字在 text 中的起始索引；未找到则返回 -1
     */
    private int findTopLevelKeyword(String text, int startFrom, String keyword) {
        String upper  = text.toUpperCase();
        String kwUp   = keyword.toUpperCase();
        int kwLen     = kwUp.length();
        int depth     = 0;

        for (int i = startFrom; i < text.length(); i++) {
            char c = text.charAt(i);
            if      (c == '(') { depth++; continue; }
            else if (c == ')') { depth--; continue; }
            if (depth != 0) continue;

            if (upper.startsWith(kwUp, i)) {
                boolean prevOk = (i == 0) || !Character.isLetterOrDigit(text.charAt(i - 1));
                int endIdx = i + kwLen;
                boolean nextOk = (endIdx >= text.length()) || !Character.isLetterOrDigit(text.charAt(endIdx));
                if (prevOk && nextOk) return i;
            }
        }
        return -1;
    }

    /** 按顶层逗号拆分 SET 子句段落，返回解析后的 {@link SetClause} 列表。 */
    private List<SetClause> parseSetClauses(String setSection) {
        List<SetClause> result = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < setSection.length(); i++) {
            char c = setSection.charAt(i);
            if      (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                String part = setSection.substring(start, i).trim();
                if (!part.isEmpty()) result.add(parseOneSetClause(part));
                start = i + 1;
            }
        }
        String last = setSection.substring(start).trim();
        if (!last.isEmpty()) result.add(parseOneSetClause(last));
        return result;
    }

    /** 解析单个 SET 子句（"col = expr"），找到顶层第一个 '=' 分割列名与表达式。 */
    private SetClause parseOneSetClause(String text) {
        SetClause sc  = new SetClause();
        sc.originalText = text;
        int eqIdx = -1;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if      (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == '=' && depth == 0) { eqIdx = i; break; }
        }
        if (eqIdx > 0) {
            sc.columnName = text.substring(0, eqIdx).trim();
            sc.expression = text.substring(eqIdx + 1).trim();
        } else {
            sc.columnName = text.trim();
            sc.expression = "";
        }
        return sc;
    }

    /**
     * 检查 {@code next} 与 {@code group} 之间是否存在列冲突，即：
     * group 中某 SET 表达式引用了 next 正在更新的列，或反之。
     * 存在冲突时不能合并，以避免 SQL 中同一 UPDATE 多 SET 子句均使用行旧值求值的语义问题。
     */
    private boolean hasSetColumnConflict(List<StatementBlock> group, StatementBlock next) {
        Set<String> groupCols = new HashSet<>();
        for (StatementBlock b : group) {
            for (SetClause sc : b.setClauses) groupCols.add(sc.columnName.toLowerCase());
        }
        Set<String> nextCols = new HashSet<>();
        for (SetClause sc : next.setClauses) nextCols.add(sc.columnName.toLowerCase());

        // group 的 SET 表达式是否引用了 next 正在更新的列
        for (StatementBlock b : group) {
            for (SetClause sc : b.setClauses) {
                String expr = sc.expression.toLowerCase();
                for (String col : nextCols) {
                    if (containsWordInSql(expr, col)) return true;
                }
            }
        }
        // next 的 SET 表达式是否引用了 group 正在更新的列
        for (SetClause sc : next.setClauses) {
            String expr = sc.expression.toLowerCase();
            for (String col : groupCols) {
                if (containsWordInSql(expr, col)) return true;
            }
        }
        return false;
    }

    /** 以单词边界判断 {@code text} 中是否包含 {@code word}（小写匹配）。 */
    private boolean containsWordInSql(String text, String word) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "\\b" + java.util.regex.Pattern.quote(word) + "\\b");
        return p.matcher(text).find();
    }

    /**
     * 将多个可合并的 UPDATE 块合并为一条 UPDATE 语句文本。
     * 所有块的注释行依次输出，SET 子句以逗号分隔合并，FROM/WHERE 子句取第一个块的值（所有块相同）。
     *
     * <p>合并后格式：
     * <pre>
     * -- 注释1
     * -- 注释2
     * UPDATE table [alias]
     * SET col1 = expr1,
     *     col2 = expr2
     * [FROM ...]       -- 仅 UPDATE...FROM 格式时出现
     * [WHERE ...]
     * ;
     * </pre>
     */
    private String buildMergedUpdate(List<StatementBlock> group) {
        StringBuilder sb = new StringBuilder();
        // 合并所有注释
        for (StatementBlock block : group) {
            for (String comment : block.commentLines) sb.append(comment).append("\n");
        }
        // 构建 UPDATE ... SET ...
        // 别名：优先使用 group 中任一有别名的块（所有块要么别名相同，要么其中一方无别名）
        StatementBlock first = group.get(0);
        String mergedAlias   = null;
        boolean mergedAliasAs = false;
        for (StatementBlock b : group) {
            if (b.tableAlias != null && !b.tableAlias.isEmpty()) {
                mergedAlias   = b.tableAlias;
                mergedAliasAs = b.tableAliasAs;
                break;
            }
        }
        sb.append("UPDATE ").append(first.tableName);
        if (mergedAlias != null) {
            if (mergedAliasAs) sb.append(" AS");
            sb.append(" ").append(mergedAlias);
        }
        sb.append("\n").append("SET ");
        boolean firstSet = true;
        for (StatementBlock block : group) {
            for (SetClause sc : block.setClauses) {
                if (!firstSet) sb.append(",\n    ");
                sb.append(sc.originalText);
                firstSet = false;
            }
        }
        // UPDATE...FROM 格式：SET 之后跟 FROM（所有块的 FROM 子句相同）
        String from = first.fromClause;
        if (from != null && !from.isEmpty()) {
            sb.append("\nFROM ").append(from);
        }
        String where = first.whereClause;
        if (where != null && !where.isEmpty()) {
            sb.append("\nWHERE ").append(where);
        }
        sb.append(";\n\n");
        return sb.toString();
    }

    // ─── 注释剥离 ────────────────────────────────────────────────────────────

    /**
     * 去除 SQL 文本中所有注释行（以 "--" 开头的行），并折叠多余的连续空行为单个空行。
     * 用于在 includeComments=false 时输出更精简的纯 SQL 脚本。
     */
    private String stripComments(String sql) {
        if (sql == null || sql.isEmpty()) return sql;
        String[] lines = sql.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int consecutiveBlanks = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--")) {
                continue;
            }
            if (trimmed.isEmpty()) {
                consecutiveBlanks++;
                if (consecutiveBlanks <= 1) {
                    sb.append("\n");
                }
            } else {
                consecutiveBlanks = 0;
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim() + "\n";
    }

    private String firstNonEmpty(String... arr) {
        if (arr == null) return null;
        for (String s : arr) {
            if (s != null && !s.trim().isEmpty()) return s.trim();
        }
        return null;
    }

    /**
     * 将对象类型转换为表名（带前缀）。
     * [FIX P2-7] 使用 appNameCache 缓存查询结果，避免 N+1 DB 查询。
     */
    private String objectTypeToTableName(String objectType) {
        if (objectType == null || objectType.trim().isEmpty()) {
            return "";
        }
        String snake = ExprUtils.camelToSnake(objectType);
        String appName = appNameCache.computeIfAbsent(objectType, k -> {
            String name = mapper.selectAppNameByObjectType(k);
            return name != null ? name.trim() : "";
        });
        if (!appName.isEmpty()) {
            return appName + "_" + snake;
        }
        return snake;
    }

    private String fieldCamelToColumnName(String fieldCamel, String objectType) {
        BaseappObjectField def = getLatestFieldInfo(objectType, fieldCamel);
        if (def != null && def.getName() != null && !def.getName().trim().isEmpty()) {
            String name = def.getName().trim();
            if (name.contains("_")) {
                return name;
            }
            return ExprUtils.camelToSnake(name);
        }
        return ExprUtils.camelToSnake(fieldCamel);
    }

    /**
     * 将公式中属于当前对象的驼峰字段名转换为下划线格式。
     * [FIX P1-4] 仅替换确认属于当前对象的字段名，跳过跨对象引用；
     * 使用负向后行断言 (?<!\.) 避免替换带对象前缀的 alias.field 引用。
     */
    private String convertFormulaToSnakeCase(String formula, String objectType) {
        if (formula == null || formula.trim().isEmpty()) {
            return formula;
        }
        
        Set<String> camelFields = ExprUtils.extractCamelFieldsFromSql(formula);
        
        Map<String, String> fieldMap = new HashMap<String, String>();
        for (String camelField : camelFields) {
            BaseappObjectField def = getLatestFieldInfo(objectType, camelField);
            if (def == null) continue;
            if (def.getName() != null && !def.getName().trim().isEmpty()) {
                fieldMap.put(camelField, def.getName().trim());
            } else {
                fieldMap.put(camelField, ExprUtils.camelToSnake(camelField));
            }
        }
        
        String result = formula;
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
            String pattern = "(?<!\\.)\\b" + java.util.regex.Pattern.quote(camel) + "\\b";
            result = result.replaceAll(pattern, snake);
        }
        
        return result;
    }

}
