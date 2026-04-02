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

import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
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
     * 引用查询：返回通过 refer_info.referEntities 引用了指定对象的所有字段，按对象分组。
     * 示例：GET /api/reference/incoming?entityName=ArContract
     */
    @GetMapping("/api/reference/incoming")
    public List<ImpactAnalyzerService.ReferenceGroup> incomingReferences(
            @RequestParam("entityName") String entityName,
            @RequestParam(value = "excludeView", defaultValue = "false") boolean excludeView) {
        return analyzerService.findObjectsReferencingEntity(entityName, excludeView);
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
        List<Map.Entry<String, String>> roots = parseRoots(req);
        int depth = req.depth != null && req.depth > 0 ? req.depth : 3;
        String relTypes = req.relTypes != null && !req.relTypes.isEmpty() ? req.relTypes : "intra,writeBack";
        Map<String, BaseappObjectField> latestFieldDefs = fetchLatestFieldDefs(req, null);
        boolean includeComments = req.includeComments != null && req.includeComments;

        String sql = upgradeScriptService.generateUpgradeScriptBatch(roots, depth, relTypes, latestFieldDefs, includeComments);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"batch_upgrade.sql\"");
        headers.setContentType(MediaType.TEXT_PLAIN);
        return new ResponseEntity<>(sql, headers, HttpStatus.OK);
    }

    /**
     * 批量生成升级脚本（流式版本）：边执行边通过 HTTP 分块传输向前端推送进度。
     *
     * <p>响应流格式：
     * <ul>
     *   <li>进度行：{@code -- [PROGRESS] 消息文本\n}（每个阶段 / 每个字段处理前即时 flush）</li>
     *   <li>最终 SQL：所有进度行之后，正常写入完整 SQL 文本</li>
     * </ul>
     * 前端通过 {@code fetch} + {@code ReadableStream} 逐行读取，识别 {@code -- [PROGRESS]} 前缀
     * 更新进度 UI，其余行拼接为最终 SQL 结果。
     */
    @PostMapping(value = "/api/impact/upgradeScript/batch/stream",
                 produces = "text/plain;charset=UTF-8")
    public ResponseEntity<StreamingResponseBody> generateUpgradeScriptBatchStream(
            @RequestBody UpgradeScriptBatchRequest req) {

        final List<Map.Entry<String, String>> roots = parseRoots(req);
        final int depth = req.depth != null && req.depth > 0 ? req.depth : 3;
        final String relTypes = req.relTypes != null && !req.relTypes.isEmpty() ? req.relTypes : "intra,writeBack";
        final boolean includeComments = req.includeComments != null && req.includeComments;

        StreamingResponseBody body = outputStream -> {
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

            // 所有对 writer 的写操作都加锁，避免心跳线程与主线程并发写导致数据损坏
            // 进度回调
            java.util.function.Consumer<String> progress = msg -> {
                synchronized (writer) {
                    try {
                        writer.write("-- [PROGRESS] " + msg + "\n");
                        writer.flush();
                    } catch (IOException ignored) {}
                }
            };

            // 心跳：每 8 秒发一行 -- [HEARTBEAT]，在图构建/拓扑排序等无进度阶段保活连接
            // application.yml 已设 spring.mvc.async.request-timeout=-1 关闭 Tomcat 内部超时，
            // 但中间代理（Nginx/LB）仍可能基于空闲时间断连，心跳可规避此问题
            java.util.concurrent.atomic.AtomicBoolean finished =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            java.util.concurrent.ScheduledExecutorService heartbeatSvc =
                    java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread t = new Thread(r, "upgrade-heartbeat");
                        t.setDaemon(true);
                        return t;
                    });
            heartbeatSvc.scheduleAtFixedRate(() -> {
                if (!finished.get()) {
                    synchronized (writer) {
                        try {
                            writer.write("-- [HEARTBEAT] keep-alive\n");
                            writer.flush();
                        } catch (IOException ignored) {}
                    }
                }
            }, 8, 8, java.util.concurrent.TimeUnit.SECONDS);

            try {
                // compareDb 字段加载（可能耗时，单独上报进度）
                Map<String, BaseappObjectField> latestFieldDefs = fetchLatestFieldDefs(req, progress);

                String sql = upgradeScriptService.generateUpgradeScriptBatch(
                        roots, depth, relTypes, latestFieldDefs, includeComments, progress);

                synchronized (writer) {
                    writer.write(sql);
                    writer.flush();
                }
            } catch (Throwable e) {
                // 捕获 Throwable 而非 Exception，确保 OOM / StackOverflow 等 Error 也能上报
                log.error("[upgradeScript/batch/stream] 生成失败", e);
                String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                synchronized (writer) {
                    try {
                        writer.write("-- [ERROR] " + errMsg + "\n");
                        writer.flush();
                    } catch (IOException ignored) {}
                }
            } finally {
                finished.set(true);
                heartbeatSvc.shutdown();
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"batch_upgrade.sql\"")
                .header("X-Accel-Buffering", "no")   // 禁用 Nginx 等代理缓冲
                .body(body);
    }

    // ─── 私有辅助方法 ────────────────────────────────────────────────────────

    /** 将请求体中的 roots 列表解析为 Entry 列表。 */
    private List<Map.Entry<String, String>> parseRoots(UpgradeScriptBatchRequest req) {
        List<Map.Entry<String, String>> roots = new ArrayList<>();
        if (req.roots != null) {
            for (UpgradeScriptBatchRequest.RootItem r : req.roots) {
                if (r != null && r.objectType != null && r.field != null) {
                    roots.add(new AbstractMap.SimpleEntry<>(r.objectType.trim(), r.field.trim()));
                }
            }
        }
        return roots;
    }

    /**
     * 从比较库拉取最新字段定义（若请求中包含 compareDbUrl）。
     *
     * @param progress 进度回调，可为 null
     * @return 字段 map（key = "ObjectType.camelField"）；未配置比较库时返回 null
     */
    private Map<String, BaseappObjectField> fetchLatestFieldDefs(
            UpgradeScriptBatchRequest req,
            java.util.function.Consumer<String> progress) {
        if (req.compareDbUrl == null || req.compareDbUrl.trim().isEmpty()) {
            return null;
        }
        if (progress != null) progress.accept("正在从比较库加载最新字段定义...");
        try {
            List<String> compareApps = parseAppNames(req.compareDbAppName);
            List<BaseappObjectField> compareFields = snapshotService.fetchCompareDbFields(
                    req.compareDbUrl.trim(), req.compareDbUser, req.compareDbPassword, compareApps);
            Map<String, BaseappObjectField> defs = new LinkedHashMap<>();
            for (BaseappObjectField f : compareFields) {
                String obj = f.getObjectType();
                if (obj == null) continue;
                if (f.getApiName() != null && !f.getApiName().trim().isEmpty()) {
                    defs.put(obj + "." + f.getApiName().trim(), f);
                }
                if (f.getName() != null && !f.getName().trim().isEmpty()) {
                    defs.put(obj + "." + f.getName().trim(), f);
                    String camel = com.deepmodel.relation.util.ExprUtils.snakeToCamel(f.getName().trim());
                    defs.putIfAbsent(obj + "." + camel, f);
                }
            }
            if (progress != null) progress.accept("比较库字段加载完成（" + defs.size() + " 条记录）");
            return defs;
        } catch (Exception e) {
            log.warn("[upgradeScript/batch] 拉取比较库字段定义失败，降级为本地定义: {}", e.getMessage());
            if (progress != null) progress.accept("比较库加载失败，降级为本地定义");
            return null;
        }
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
        /** 是否在输出 SQL 中保留注释行，默认 false（不保留） */
        public Boolean includeComments;

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

}
