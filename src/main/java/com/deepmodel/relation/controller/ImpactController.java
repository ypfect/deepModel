package com.deepmodel.relation.controller;

import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.GraphModels;
import com.deepmodel.relation.service.ImpactAnalyzerService;
import com.deepmodel.relation.service.UpgradeScriptService;
import com.deepmodel.relation.service.HealthCheckService;
import com.deepmodel.relation.service.SnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.AbstractMap;

@RestController
public class ImpactController {

    private static final Logger log = LoggerFactory.getLogger(ImpactController.class);

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    private final ImpactAnalyzerService analyzerService;
    private final UpgradeScriptService upgradeScriptService;
    private final HealthCheckService healthCheckService;
    private final SnapshotService snapshotService;

    public ImpactController(ImpactAnalyzerService analyzerService,
            UpgradeScriptService upgradeScriptService,
            HealthCheckService healthCheckService,
            SnapshotService snapshotService) {
        this.analyzerService = analyzerService;
        this.upgradeScriptService = upgradeScriptService;
        this.healthCheckService = healthCheckService;
        this.snapshotService = snapshotService;
    }

    @GetMapping("/api/impact/meta/datasource")
    public Map<String, String> getDatasourceInfo() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("url", datasourceUrl);
        info.put("username", datasourceUsername);
        info.put("password", datasourcePassword);
        return info;
    }

    @GetMapping("/api/reload")
    public String reload() {
        analyzerService.reload();
        return "ok";
    }

    @GetMapping("/api/impact/meta/objects")
    public Set<String> getObjects() {
        return analyzerService.getAllObjectTypes();
    }

    @GetMapping("/api/impact/meta/objectDetails")
    public List<Map<String, String>> getObjectDetails() {
        return analyzerService.getObjectDetails();
    }

    @GetMapping("/api/impact/meta/fields")
    public List<String> getFields(@RequestParam("objectType") String objectType) {
        return analyzerService.getFieldsForObject(objectType);
    }

    @GetMapping("/api/impact/meta/fieldDetails")
    public List<BaseappObjectField> getFieldsDetails(@RequestParam("objectType") String objectType) {
        return analyzerService.getFieldDetailsForObject(objectType);
    }

    @GetMapping("/api/impact/meta/health")
    public ImpactAnalyzerService.ObjectHealth getObjectHealth(@RequestParam("objectType") String objectType) {
        return analyzerService.getObjectHealth(objectType);
    }

    @GetMapping("/api/impact/health/cycles")
    public HealthCheckService.CycleResult checkCycles(@RequestParam(required = false) String appName) {
        List<String> apps = parseAppNames(appName);
        return healthCheckService.detectCycles(apps);
    }

    @GetMapping("/api/impact/health/chains")
    public HealthCheckService.ChainResult checkChains(@RequestParam(defaultValue = "10") int threshold,
            @RequestParam(required = false) String appName) {
        List<String> apps = parseAppNames(appName);
        return healthCheckService.detectDeepChains(threshold, apps);
    }

    /**
     * 调试用：查看当前全局依赖图的节点/边统计，方便排查为什么 chains 结果为空。
     */
    @GetMapping("/api/impact/health/graph/debug")
    public Map<String, Object> debugHealthGraph(@RequestParam(required = false) String appName) {
        List<String> apps = parseAppNames(appName);
        return healthCheckService.debugGlobalGraph(apps);
    }

    /**
     * 调试用：按 appName 查看当前内存中的字段数量和示例，用来确认 appName 填充是否正确。
     */
    @GetMapping("/api/debug/fieldsByApp")
    public Map<String, Object> debugFieldsByApp(@RequestParam("appName") String appName) {
        Map<String, Object> result = new HashMap<>();
        if (appName == null || appName.trim().isEmpty()) {
            result.put("error", "appName is empty");
            return result;
        }
        String target = appName.trim().toLowerCase(Locale.ROOT);
        List<BaseappObjectField> all = analyzerService.getAllFields();
        List<Map<String, String>> samples = new ArrayList<>();
        int count = 0;
        for (BaseappObjectField f : all) {
            String app = f.getAppName();
            if (app == null)
                continue;
            String a = app.toLowerCase(Locale.ROOT);
            if (a.equals(target) || a.startsWith(target) || a.contains(target)) {
                count++;
                if (samples.size() < 20) {
                    Map<String, String> m = new HashMap<>();
                    m.put("id", String.valueOf(f.getId()));
                    m.put("objectType", String.valueOf(f.getObjectType()));
                    m.put("field", String.valueOf(f.getApiName() != null ? f.getApiName() : f.getName()));
                    m.put("appName", app);
                    samples.add(m);
                }
            }
        }
        result.put("appName", appName);
        result.put("totalMatchedFields", count);
        result.put("samples", samples);
        return result;
    }

    @PostMapping("/api/impact/snapshot/create")
    public String createSnapshot() throws java.io.IOException {
        return snapshotService.createSnapshot();
    }

    @GetMapping("/api/impact/snapshot/list")
    public java.util.List<String> listSnapshots() {
        return snapshotService.listSnapshots();
    }

    @GetMapping("/api/impact/snapshot/diff")
    public SnapshotService.VersionDiff diffSnapshots(@RequestParam("id1") String id1, @RequestParam("id2") String id2)
            throws java.io.IOException {
        return snapshotService.compare(id1, id2);
    }

    /**
     * 远程数据库对比：前端传入 JDBC URL、用户名、密码和可选 appName 列表，
     * 后端从远程库加载 baseapp_object_field + object_type，再与本地当前定义做差异分析。
     *
     * 支持指定「基准库」：
     * - localAsBase = true => 基准库 = 本地 Spring Boot 数据源，比较库 = 远程
     * - localAsBase = false => 基准库 = 远程，比较库 = 本地（兼容原有语义）
     */
    @PostMapping("/api/impact/snapshot/diff/remote")
    public SnapshotService.VersionDiff diffRemote(
            @org.springframework.web.bind.annotation.RequestBody RemoteRequest req)
            throws Exception {
        List<String> apps = parseAppNames(req.appName);
        boolean localAsBase = req.localAsBase == null ? false : req.localAsBase;
        return snapshotService.compareWithRemote(req.url, req.username, req.password, apps, localAsBase);
    }

    public static class RemoteRequest {
        public String url;
        public String username;
        public String password;
        public String appName;
        /**
         * 是否以本地库为基准库（可为空，默认 false 以保持向后兼容；前端会主动传 true 以本地为基准）。
         */
        public Boolean localAsBase;
    }

    /**
     * 双端 JDBC 对比接口。前端传入「基准库」和「比较库」两个 JDBC 端点（都可为空）。
     * - 当某一端 URL 为空时，表示使用当前 Spring Boot 数据源。
     */
    @PostMapping("/api/impact/snapshot/diff/jdbcPair")
    public SnapshotService.VersionDiff diffJdbcPair(
            @org.springframework.web.bind.annotation.RequestBody JdbcPairRequest req) throws Exception {
        JdbcEndpoint base = req.base != null ? req.base : new JdbcEndpoint();
        JdbcEndpoint compare = req.compare != null ? req.compare : new JdbcEndpoint();

        List<String> baseApps = parseAppNames(base.appName);
        List<String> compareApps = parseAppNames(compare.appName);

        return snapshotService.compareJdbcPair(
                base.url, base.username, base.password, baseApps,
                compare.url, compare.username, compare.password, compareApps);
    }

    public static class JdbcEndpoint {
        public String url;
        public String username;
        public String password;
        public String appName;
    }

    public static class JdbcPairRequest {
        public JdbcEndpoint base;
        public JdbcEndpoint compare;
    }

    /**
     * 跨对象：给定来源对象，查询所有被其 writeBack/聚合影响到的目标对象列表。
     */
    @GetMapping("/api/impact/cross/targetsBySource")
    public List<ImpactAnalyzerService.CrossTargetSummary> listTargetsBySource(
            @RequestParam("sourceObject") String sourceObject) {
        return analyzerService.listTargetsBySource(sourceObject);
    }

    /**
     * 跨对象：给定目标对象，查询有哪些来源对象通过 writeBack/聚合影响到它。
     * 本需求中，左侧选择的是“当前对象 A”，因此这里用 targetObject 参数。
     */
    @GetMapping("/api/impact/cross/sourcesForTarget")
    public List<ImpactAnalyzerService.CrossSourceSummary> listSourcesForTarget(
            @RequestParam("targetObject") String targetObject) {
        return analyzerService.listSourcesForTarget(targetObject);
    }

    // 新增：字段详情
    @GetMapping("/api/impact/fieldInfo")
    public BaseappObjectField fieldInfo(@RequestParam("objectType") String objectType,
            @RequestParam("field") String field) {
        return analyzerService.getFieldInfo(objectType, field);
    }

    @GetMapping("/api/impact")
    public GraphModels.Graph impact(@RequestParam("objectType") String objectType,
            @RequestParam("field") String field,
            @RequestParam(value = "depth", defaultValue = "3") int depth,
            @RequestParam(value = "relType", defaultValue = "0") int relType,
            @RequestParam(value = "direction", defaultValue = "downstream") String direction) {
        boolean includeUpstream = !"downstream".equalsIgnoreCase(direction);
        return analyzerService.analyze(objectType, field, depth, relType, includeUpstream);
    }

    /**
     * 批量影响分析接口
     * 
     * @param objectType 对象类型
     * @param fields     字段列表，多个字段用逗号分隔
     * @param depth      深度
     * @param relType    关系类型
     * @param direction  方向
     * @return 合并后的分析结果
     */
    @GetMapping("/api/impact/batch")
    public GraphModels.Graph impactBatch(@RequestParam("objectType") String objectType,
            @RequestParam("fields") String fields,
            @RequestParam(value = "depth", defaultValue = "3") int depth,
            @RequestParam(value = "relType", defaultValue = "0") int relType,
            @RequestParam(value = "direction", defaultValue = "downstream") String direction) {
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

    private static String safeId(String id) {
        if (id == null)
            return "";
        return id.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String toMermaid(GraphModels.Graph g) {
        StringBuilder sb = new StringBuilder();
        sb.append("graph TD\n");
        Map<String, List<GraphModels.Node>> byObj = new LinkedHashMap<String, List<GraphModels.Node>>();
        for (GraphModels.Node n : g.nodes) {
            if (!byObj.containsKey(n.object))
                byObj.put(n.object, new ArrayList<GraphModels.Node>());
            byObj.get(n.object).add(n);
        }
        Map<String, String> idMap = new HashMap<String, String>();
        for (GraphModels.Node n : g.nodes) {
            String raw = n.id;
            String sid = safeId(raw);
            idMap.put(raw, sid);
        }
        for (Map.Entry<String, List<GraphModels.Node>> e : byObj.entrySet()) {
            sb.append("  subgraph ").append(e.getKey()).append("\n");
            for (GraphModels.Node n : e.getValue()) {
                String raw = n.id;
                String sid = idMap.get(raw);
                String label = raw;
                sb.append("    ").append(sid).append("[").append(label.replace("\\", "\\\\").replace("]", "\\]"))
                        .append("]\n");
            }
            sb.append("  end\n");
        }
        for (GraphModels.Edge e : g.edges) {
            String s = idMap.get(e.source);
            String t = idMap.get(e.target);
            if (s == null || t == null)
                continue;
            sb.append("  ").append(s).append(" -- ").append(e.type).append(" --> ").append(t).append("\n");
        }
        return sb.toString();
    }

    @GetMapping(value = "/api/impact/mermaid", produces = MediaType.TEXT_PLAIN_VALUE)
    public String mermaid(@RequestParam("objectType") String objectType,
            @RequestParam("field") String field,
            @RequestParam(value = "depth", defaultValue = "3") int depth,
            @RequestParam(value = "relType", defaultValue = "0") int relType,
            @RequestParam(value = "direction", defaultValue = "downstream") String direction) {
        boolean includeUpstream = !"downstream".equalsIgnoreCase(direction);
        GraphModels.Graph g = analyzerService.analyze(objectType, field, depth, relType, includeUpstream);
        return toMermaid(g);
    }

    @GetMapping(value = "/api/impact/mermaidPage", produces = MediaType.TEXT_HTML_VALUE)
    public String mermaidPage(@RequestParam("objectType") String objectType,
            @RequestParam("field") String field,
            @RequestParam(value = "depth", defaultValue = "3") int depth,
            @RequestParam(value = "relType", defaultValue = "0") int relType,
            @RequestParam(value = "direction", defaultValue = "downstream") String direction) {
        boolean includeUpstream = !"downstream".equalsIgnoreCase(direction);
        GraphModels.Graph g = analyzerService.analyze(objectType, field, depth, relType, includeUpstream);
        String mer = toMermaid(g);
        return "<!doctype html><html><head><meta charset=\"utf-8\"/>" +
                "<title>Mermaid</title>" +
                "<script src=\"https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js\"></script>" +
                "<script>mermaid.initialize({startOnLoad:true});</script>" +
                "</head><body><div class=\"mermaid\">" + mer.replace("<", "&lt;") + "</div></body></html>";
    }

    @GetMapping(value = "/api/impact/dot", produces = MediaType.TEXT_PLAIN_VALUE)
    public String dot(@RequestParam("objectType") String objectType,
            @RequestParam("field") String field,
            @RequestParam(value = "depth", defaultValue = "3") int depth,
            @RequestParam(value = "relType", defaultValue = "0") int relType,
            @RequestParam(value = "direction", defaultValue = "downstream") String direction) {
        boolean includeUpstream = !"downstream".equalsIgnoreCase(direction);
        GraphModels.Graph g = analyzerService.analyze(objectType, field, depth, relType, includeUpstream);
        StringBuilder sb = new StringBuilder();
        sb.append("digraph G {\n  rankdir=LR;\n  node [shape=box, style=rounded];\n");
        for (GraphModels.Node n : g.nodes) {
            sb.append("  \"").append(n.id.replace("\"", "\\\""))
                    .append("\";\n");
        }
        for (GraphModels.Edge e : g.edges) {
            sb.append("  \"").append(e.source.replace("\"", "\\\""))
                    .append("\" -> \"")
                    .append(e.target.replace("\"", "\\\""))
                    .append("\" [label=\"").append(e.type).append("\"];\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * 解析 appName 参数，支持逗号分隔的多应用名，自动去空格和空串。
     * 示例：\"arap, baseapp\" -> [\"arap\", \"baseapp\"]
     */
    private List<String> parseAppNames(String appName) {
        if (appName == null || appName.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<String>();
        for (String part : appName.split(",")) {
            if (part != null) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    @GetMapping("/api/impact/objects")
    public GraphModels.ObjectGraph impactObjects(@RequestParam("objectType") String objectType,
            @RequestParam("field") String field,
            @RequestParam(value = "depth", defaultValue = "3") int depth,
            @RequestParam(value = "relType", defaultValue = "0") int relType) {
        return analyzerService.analyzeObjects(objectType, field, depth, relType);
    }

    @GetMapping("/api/impact/objectEdgeDetails")
    public List<GraphModels.Edge> objectEdgeDetails(@RequestParam("objectType") String objectType,
            @RequestParam("field") String field,
            @RequestParam(value = "depth", defaultValue = "3") int depth,
            @RequestParam(value = "relType", defaultValue = "0") int relType,
            @RequestParam("sourceObject") String sourceObject,
            @RequestParam("targetObject") String targetObject,
            @RequestParam("type") String type) {
        return analyzerService.objectEdgeDetails(objectType, field, depth, relType, sourceObject, targetObject, type);
    }

    // ===== 调试接口：查看视图依赖关系 =====
    @GetMapping("/api/debug/viewDeps")
    public Map<String, Object> debugViewDeps() {
        return analyzerService.getViewDependenciesDebugInfo();
    }

    /**
     * 查询：在目标对象中，哪些字段是由指定来源对象写回/聚合而来。
     * targetObject 示例：ArContractSubjectMatterItem
     * sourceObject 示例：RevenueConfirmationItem
     */
    @GetMapping("/api/impact/bySourceObject")
    public List<BaseappObjectField> fieldsBySourceObject(@RequestParam("targetObject") String targetObject,
            @RequestParam("sourceObject") String sourceObject) {
        return analyzerService.getFieldsImpactedBySourceObject(targetObject, sourceObject);
    }

    /**
     * 查询：某个目标字段依赖的 trigger 字段（同对象内的上游字段）。
     */
    @GetMapping("/api/impact/triggerFields")
    public List<BaseappObjectField> triggerFields(@RequestParam("objectType") String objectType,
            @RequestParam("field") String field) {
        return analyzerService.getTriggerFieldsForTarget(objectType, field);
    }

    /**
     * 生成字段升级 SQL 脚本（仅返回文本，不执行）。
     *
     * @param objectType 根对象类型
     * @param field      根字段名
     * @param depth      向下层级深度
     * @param relTypes   关系类型列表，例如: intra,writeBack 或 intra,writeBack,view
     */
    @GetMapping(value = "/api/impact/upgradeScript", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> generateUpgradeScript(
            @RequestParam("objectType") String objectType,
            @RequestParam("field") String field,
            @RequestParam(value = "depth", defaultValue = "3") int depth,
            @RequestParam(value = "relTypes", defaultValue = "intra,writeBack") String relTypes) {

        String sql = upgradeScriptService.generateUpgradeScript(objectType, field, depth, relTypes);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + objectType + "_" + field + "_upgrade.sql\"");
        headers.setContentType(MediaType.TEXT_PLAIN);

        return new ResponseEntity<String>(sql, headers, HttpStatus.OK);
    }

    /**
     * 批量生成升级脚本：多根合并图 + 拓扑排序，一次请求返回整份脚本。
     * POST body: { "roots": [ { "objectType", "field" }, ... ], "depth": 3,
     * "relTypes": "intra,writeBack" }
     */
    @PostMapping(value = "/api/impact/upgradeScript/batch", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> generateUpgradeScriptBatch(@RequestBody UpgradeScriptBatchRequest req) {
        List<Map.Entry<String, String>> roots = new ArrayList<>();
        if (req.roots != null) {
            for (UpgradeScriptBatchRequest.RootItem r : req.roots) {
                if (r != null && r.objectType != null && r.field != null) {
                    roots.add(new AbstractMap.SimpleEntry<>(r.objectType.trim(), r.field.trim()));
                }
            }
        }
        int depth = req.depth != null && req.depth > 0 ? req.depth : 3;
        String relTypes = req.relTypes != null && !req.relTypes.isEmpty() ? req.relTypes : "intra,writeBack";

        // 如果前端传入了比较库连接，拉取最新字段定义用于生成 SQL（以比较库定义为准）
        Map<String, BaseappObjectField> latestFieldDefs = null;
        if (req.compareDbUrl != null && !req.compareDbUrl.trim().isEmpty()) {
            try {
                List<String> compareApps = parseAppNames(req.compareDbAppName);
                List<BaseappObjectField> compareFields = snapshotService.fetchCompareDbFields(
                        req.compareDbUrl.trim(), req.compareDbUser, req.compareDbPassword, compareApps);
                latestFieldDefs = new LinkedHashMap<>();
                for (BaseappObjectField f : compareFields) {
                    String obj = f.getObjectType();
                    if (obj == null)
                        continue;
                    // 以 apiName（camelCase）为主键，name（snake_case）为辅键，都存入，方便双格式查找
                    if (f.getApiName() != null && !f.getApiName().trim().isEmpty()) {
                        latestFieldDefs.put(obj + "." + f.getApiName().trim(), f);
                    }
                    if (f.getName() != null && !f.getName().trim().isEmpty()) {
                        latestFieldDefs.put(obj + "." + f.getName().trim(), f);
                        // name 是 snake_case 时也存 camelCase key
                        String camel = com.deepmodel.relation.util.ExprUtils.snakeToCamel(f.getName().trim());
                        latestFieldDefs.putIfAbsent(obj + "." + camel, f);
                    }
                }
            } catch (Exception e) {
                log.warn("[upgradeScript/batch] 拉取比较库字段定义失败，降级为本地定义: {}", e.getMessage());
            }
        }

        String sql = upgradeScriptService.generateUpgradeScriptBatch(roots, depth, relTypes, latestFieldDefs);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"batch_upgrade.sql\"");
        headers.setContentType(MediaType.TEXT_PLAIN);
        return new ResponseEntity<>(sql, headers, HttpStatus.OK);
    }

    public static class UpgradeScriptBatchRequest {
        public List<RootItem> roots;
        public Integer depth;
        public String relTypes;
        /** 比较库 JDBC 连接（可选），用于以最新字段定义生成升级 SQL */
        public String compareDbUrl;
        public String compareDbUser;
        public String compareDbPassword;
        public String compareDbAppName;

        public static class RootItem {
            public String objectType;
            public String field;
        }
    }

    /**
     * 多根合并影响图（字段明细视图），供前端 Cytoscape.js 可视化展示。
     * 请求体与 upgradeScript/batch 完全一致，只返回 Graph JSON 而不生成 SQL。
     */
    @PostMapping("/api/impact/graph/multiRoot")
    public GraphModels.Graph multiRootGraph(@RequestBody UpgradeScriptBatchRequest req) {
        List<Map.Entry<String, String>> roots = new ArrayList<>();
        if (req.roots != null) {
            for (UpgradeScriptBatchRequest.RootItem r : req.roots) {
                if (r != null && r.objectType != null && r.field != null) {
                    roots.add(new AbstractMap.SimpleEntry<>(r.objectType.trim(), r.field.trim()));
                }
            }
        }
        if (roots.isEmpty())
            return new GraphModels.Graph();
        int depth = req.depth != null && req.depth > 0 ? req.depth : 3;
        int relType = 4; // intra + writeBack only，排除 view
        return analyzerService.buildMultiRootClosedGraph(roots, depth, relType);
    }

    // ===== 解释接口 =====
    @GetMapping("/api/impact/explain")
    public GraphModels.ExplainResponse explain(@RequestParam("objectType") String objectType,
            @RequestParam("field") String field,
            @RequestParam(value = "depth", defaultValue = "3") int depth,
            @RequestParam(value = "relType", defaultValue = "0") int relType,
            @RequestParam(value = "direction", defaultValue = "downstream") String direction) {
        boolean includeUpstream = !"downstream".equalsIgnoreCase(direction);
        return analyzerService.explain(objectType, field, depth, relType, includeUpstream);
    }

    @GetMapping(value = "/api/impact/explainPage", produces = MediaType.TEXT_HTML_VALUE)
    public String explainPage() {
        return "<!doctype html><html lang=\"zh\"><head><meta charset=\"utf-8\"/>" +
                "<title>按对象分组的解释</title>" +
                "<style>body{font-family:Arial;margin:0}#toolbar{padding:10px 12px;background:#f5f7fa;border-bottom:1px solid #e5e7eb;display:flex;gap:8px;align-items:center;flex-wrap:wrap}#content{padding:12px}details{border:1px solid #e5e7eb;border-radius:8px;margin:10px 0;background:#fff}summary{cursor:pointer;padding:8px 12px;font-weight:bold;background:#f9fafb}ul{margin:8px 16px;padding-left:18px}code{background:#f3f4f6;padding:2px 6px;border-radius:4px}</style>"
                +
                "</head><body>" +
                "<div id=\"toolbar\">" +
                "对象 <input id=\"obj\" value=\"ArReceiptItem\"/>" +
                " 字段 <input id=\"fld\" value=\"originAmount\"/>" +
                " 深度 <input id=\"dep\" type=\"number\" value=\"3\" min=\"1\" max=\"6\"/>" +
                " 关系类型 <select id=\"rel\"><option value=\"0\" selected>全部</option><option value=\"1\">回写</option><option value=\"2\">触发</option></select>"
                +
                " <button id=\"btn\">查询</button>" +
                "</div>" +
                "<div id=\"content\"></div>" +
                "<script>(function(){\n" +
                "async function load(){ const o=document.getElementById('obj').value.trim(); const f=document.getElementById('fld').value.trim(); const d=parseInt(document.getElementById('dep').value||'3',10); const r=parseInt(document.getElementById('rel').value||'0',10); const url=`/api/impact/explain?objectType=${encodeURIComponent(o)}&field=${encodeURIComponent(f)}&depth=${encodeURIComponent(d)}&relType=${encodeURIComponent(r)}`; const resp = await fetch(url); if(!resp.ok){ alert('请求失败'); return; } const data = await resp.json(); render(data);}\n"
                +
                "function esc(s){ return (s||'').replace(/[&<>\"]/g, c=>({ '&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;' }[c])); }\n"
                +
                "function stepLine(s){ return `<li><code>${esc(s.source)}</code> -- <b>${esc(s.type)}</b> --> <code>${esc(s.target)}</code> · ${esc(s.reason||'')}</li>`; }\n"
                +
                "function render(data){ const ct = document.getElementById('content'); ct.innerHTML=''; if(!data||!data.groups||data.groups.length===0){ ct.textContent='无结果'; return; } const header=document.createElement('div'); header.style.margin='6px 2px'; header.innerHTML=`根：<code>${esc(data.rootObject)}.${esc(data.rootField)}</code>`; ct.appendChild(header); data.groups.forEach(g=>{ const det=document.createElement('details'); det.open=true; const sum=document.createElement('summary'); sum.textContent=g.object; det.appendChild(sum); (g.fields||[]).forEach(fe=>{ const h=document.createElement('div'); h.style.padding='8px 12px'; const title=document.createElement('div'); title.style.fontWeight='bold'; title.textContent=fe.object + '.' + fe.field; const ul=document.createElement('ul'); (fe.steps||[]).forEach(st=>{ const li=document.createElement('li'); li.innerHTML = stepLine(st); ul.appendChild(li); }); const sm=document.createElement('div'); sm.style.color='#666'; sm.style.marginTop='6px'; sm.textContent = fe.summary||''; h.appendChild(title); h.appendChild(ul); h.appendChild(sm); det.appendChild(h); }); ct.appendChild(det); }); }\n"
                +
                "document.getElementById('btn').onclick=load; load();\n" +
                "})();</script>" +
                "</body></html>";
    }

    @GetMapping(value = "/api/impact/objectsPage", produces = MediaType.TEXT_HTML_VALUE)
    public String impactObjectsPage() {
        return "<!doctype html><html lang=\"zh\"><head><meta charset=\"utf-8\"/>" +
                "<title>对象级影响图</title>" +
                "<style>body{font-family:Arial;margin:0}#toolbar{padding:10px 12px;background:#f5f7fa;border-bottom:1px solid #e5e7eb;display:flex;gap:8px;align-items:center;flex-wrap:wrap}#objFilters{max-height:84px;overflow:auto;padding:6px 10px;border-top:1px solid #e5e7eb;background:#fafafa;display:flex;flex-wrap:wrap;gap:12px;align-items:center}#cy{width:100%;height:calc(100vh - 112px)}label{margin-right:6px}</style>"
                +
                "<script src=\"https://cdn.jsdelivr.net/npm/cytoscape@3.28.0/dist/cytoscape.min.js\"></script>" +
                "</head><body>" +
                "<div id=\"toolbar\">" +
                "对象 <input id=\"obj\" value=\"ArReceipt\"/>" +
                " 字段 <input id=\"fld\" value=\"originAmount\"/>" +
                " 深度 <input id=\"dep\" type=\"number\" value=\"3\" min=\"1\" max=\"6\"/>" +
                " 关系类型 <select id=\"rel\"><option value=\"0\" selected>全部</option><option value=\"1\">回写</option><option value=\"2\">触发</option></select>"
                +
                " <button id=\"btn\">查询</button>" +
                " <span style=\"margin-left:auto;font-size:12px;color:#666\">对象过滤：下方勾选</span>" +
                "</div>" +
                "<div id=\"objFilters\"></div>" +
                "<div id=\"cy\"></div>" +
                "<script>(function(){\n" +
                "const cy = cytoscape({ container: document.getElementById('cy'), elements: [], style: [\n" +
                "{ selector: 'node', style: { 'shape':'round-rectangle','background-color':'#f0f9ff','border-color':'#0ea5e9','border-width':1,'label':'data(label)','padding':10,'text-wrap':'wrap','text-max-width':120 } },\n"
                +
                "{ selector: 'edge', style: { 'width':2,'curve-style':'bezier','target-arrow-shape':'triangle','label':ele=>ele.data('type')+'('+ele.data('count')+')','font-size':10,'text-rotation':'autorotate','line-color':ele=>ele.data('type')==='writeBack'?'#16a34a':'#2563eb','target-arrow-color':ele=>ele.data('type')==='writeBack'?'#16a34a':'#2563eb' } },\n"
                +
                "{ selector: '.hiddenObj', style: { 'display':'none' } }\n" +
                "], layout: { name: 'breadthfirst', directed:true, padding: 20 } });\n" +
                "function color(obj){ let h=0; for(let i=0;i<obj.length;i++){ h=(h*31+obj.charCodeAt(i))>>>0;} h%=360; return `hsl(${h},70%,90%)`; }\n"
                +
                "function toElements(data){ const es=[]; const seenN=new Set(); const seenE=new Set(); (data.nodes||[]).forEach(n=>{ if(seenN.has(n.object)) return; seenN.add(n.object); es.push({ data:{ id:n.object, label: n.object + ' ('+n.fieldCount+')' }, style:{ 'background-color': color(n.object) } }); }); (data.edges||[]).forEach(e=>{ const id=e.sourceObject+'->'+e.type+'->'+e.targetObject; if(seenE.has(id)) return; seenE.add(id); es.push({ data:{ id, source:e.sourceObject, target:e.targetObject, type:e.type, count:e.count } }); }); return es; }\n"
                +
                "function rebuildObjectFilters(){ const panel = document.getElementById('objFilters'); panel.innerHTML=''; const objs = cy.nodes().map(n=>n.id()).sort(); if(objs.length===0){ panel.textContent='无对象可过滤'; return; } const btnAll=document.createElement('button'); btnAll.textContent='全选'; btnAll.onclick=()=>{ setAll(true); }; const btnNone=document.createElement('button'); btnNone.textContent='全不选'; btnNone.onclick=()=>{ setAll(false); }; panel.appendChild(btnAll); panel.appendChild(btnNone); objs.forEach(obj=>{ const label=document.createElement('label'); const ck=document.createElement('input'); ck.type='checkbox'; ck.value=obj; ck.checked=true; ck.onchange=applyObjectFilters; label.appendChild(ck); label.appendChild(document.createTextNode(' '+obj)); panel.appendChild(label); }); }\n"
                +
                "function setAll(val){ document.querySelectorAll('#objFilters input[type=checkbox]').forEach(ck=>{ ck.checked=val; }); applyObjectFilters(); }\n"
                +
                "function applyObjectFilters(){ const allowed=new Set(); document.querySelectorAll('#objFilters input[type=checkbox]').forEach(ck=>{ if(ck.checked) allowed.add(ck.value); }); cy.batch(()=>{ cy.nodes().forEach(n=>{ if(allowed.has(n.id())) n.removeClass('hiddenObj'); else n.addClass('hiddenObj'); }); cy.edges().forEach(e=>{ const sHidden=e.source().hasClass('hiddenObj'); const tHidden=e.target().hasClass('hiddenObj'); if(sHidden||tHidden) e.addClass('hiddenObj'); else e.removeClass('hiddenObj'); }); }); }\n"
                +
                "async function load(){ const o=document.getElementById('obj').value.trim(); const f=document.getElementById('fld').value.trim(); const d=parseInt(document.getElementById('dep').value||'3',10); const r=parseInt(document.getElementById('rel').value||'0',10); const resp = await fetch(`/api/impact/objects?objectType=${encodeURIComponent(o)}&field=${encodeURIComponent(f)}&depth=${encodeURIComponent(d)}&relType=${encodeURIComponent(r)}`); const data = await resp.json(); cy.elements().remove(); cy.add(toElements(data)); cy.layout({ name:'breadthfirst', directed:true, padding:20 }).run(); rebuildObjectFilters(); applyObjectFilters(); }\n"
                +
                "document.getElementById('btn').onclick=load; load();\n" +
                "})();</script>" +
                "</body></html>";
    }

    /**
     * Neo4j 风格对象-字段图谱数据接口。
     *
     * @param appName    按 appName 过滤（支持逗号分隔多个），为空则不过滤
     * @param objectName 只展示指定对象（逗号分隔），为空则全部
     * @param maxFields  最多返回多少字段节点（默认 500）
     */
    @GetMapping("/api/neo4j/graph")
    public ImpactAnalyzerService.Neo4jGraph neo4jGraph(
            @RequestParam(required = false) String appName,
            @RequestParam(required = false) String objectName,
            @RequestParam(value = "maxFields", defaultValue = "500") int maxFields) {
        return analyzerService.buildObjectFieldGraph(appName, objectName, maxFields);
    }

    /**
     * NL2MVEL: 自然语言到级联表达式的图推演。
     * 给定起点对象和自然语言关键词，自动分词并在图谱中寻找最短路径，
     * 最终拼接成例如 project.category.name 这样的合法 MVEL 表达式。
     * 
     * [Global升级]: 如果 baseObject 为空，则触发全图自动选址算法。
     */
    @GetMapping("/api/neo4j/deducePath")
    public ImpactAnalyzerService.DeduceResult deducePath(
            @RequestParam(required = false) String baseObject,
            @RequestParam String keyword,
            @RequestParam(value = "maxDepth", defaultValue = "3") int maxDepth) {

        if (baseObject == null || baseObject.trim().isEmpty()) {
            return analyzerService.globalDeduceExpressionPath(keyword, maxDepth);
        } else {
            return analyzerService.deduceExpressionPath(baseObject, keyword, maxDepth);
        }
    }
}
