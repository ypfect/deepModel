package com.deepmodel.relation.env;

import com.deepmodel.relation.dao.MetadataRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class EnvController {

    private final EnvResolver envResolver;
    private final MetadataRepository metadataRepository;
    private final EnvSnapshotManager snapshotManager;

    public EnvController(EnvResolver envResolver,
                         MetadataRepository metadataRepository,
                         EnvSnapshotManager snapshotManager) {
        this.envResolver = envResolver;
        this.metadataRepository = metadataRepository;
        this.snapshotManager = snapshotManager;
    }

    /** 返回所有环境（简化字段，供前端下拉展示）。 */
    @GetMapping("/api/env/list")
    public List<Map<String, Object>> listEnvs() {
        List<Map<String, Object>> raw = envResolver.listEnvs();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> e : raw) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("envName", e.get("envName"));
            item.put("zhcnName", e.get("zhcnName"));
            item.put("type", e.get("type"));
            item.put("globalEnv", e.get("globalEnv"));
            item.put("envStatus", e.get("envStatus"));
            result.add(item);
        }
        return result;
    }

    /** 解析某 env 的服务详情（GraphQL/writeBack 地址 + 服务列表）。 */
    @GetMapping("/api/env/services")
    public EnvInfo getServices(@RequestParam("env") String env) {
        return envResolver.resolve(env);
    }

    /** 返回当前请求上下文中的 env（前端用作调试）。 */
    @GetMapping("/api/env/current")
    public Map<String, Object> current() {
        Map<String, Object> result = new LinkedHashMap<>();
        String env = EnvContext.currentOrNull();
        result.put("env", env);
        if (env != null) {
            EnvInfo info = envResolver.resolve(env);
            result.put("graphqlUrl", info.graphqlUrl);
            result.put("writeBackSqlApiUrl", info.writeBackSqlApiUrl);
        }
        return result;
    }

    /** 强制刷新某 env 的服务地址缓存。 */
    @PostMapping("/api/env/invalidate")
    public Map<String, Object> invalidate(@RequestParam("env") String env) {
        envResolver.invalidate(env);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("env", env);
        result.put("invalidated", true);
        return result;
    }

    /**
     * 查询指定环境下的 appName 列表（GraphQL AggregateQueryOne）。
     * env 为空时使用当前请求的 X-Env。
     */
    @GetMapping("/api/env/apps")
    public List<String> listApps(@RequestParam(value = "env", required = false) String env) {
        if (env != null && !env.isBlank()) {
            return metadataRepository.selectDistinctAppNamesForEnv(env.trim());
        }
        return metadataRepository.selectDistinctAppNames();
    }

    /**
     * 查询某环境元数据快照是否已加载（不触发加载）。
     */
    @GetMapping("/api/env/snapshot/status")
    public Map<String, Object> snapshotStatus(@RequestParam(value = "env", required = false) String env) {
        String target = (env != null && !env.isBlank()) ? env.trim() : EnvContext.currentOrNull();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("env", target);
        if (target == null || target.isBlank()) {
            result.put("loaded", false);
            result.put("message", "未选择环境");
            return result;
        }
        EnvSnapshot snap = snapshotManager.peek(target);
        result.put("loaded", snap != null && snap.builtAt > 0);
        if (snap != null) {
            result.put("fieldCount", snap.allRows != null ? snap.allRows.size() : 0);
            result.put("objectCount", snap.rowsByObject != null ? snap.rowsByObject.size() : 0);
            result.put("builtAt", snap.builtAt);
        }
        return result;
    }

    /**
     * 预热当前环境的元数据快照（阻塞直到加载完成）。前端切换环境后调用。
     */
    @PostMapping("/api/env/snapshot/warmup")
    public Map<String, Object> snapshotWarmup() {
        String env = EnvContext.requireCurrent();
        envResolver.assertEnvRunnable(env);
        long t0 = System.currentTimeMillis();
        EnvSnapshot snap = snapshotManager.getOrLoad(env);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("env", env);
        result.put("loaded", true);
        result.put("fieldCount", snap.allRows != null ? snap.allRows.size() : 0);
        result.put("objectCount", snap.rowsByObject != null ? snap.rowsByObject.size() : 0);
        result.put("builtAt", snap.builtAt);
        result.put("elapsedMs", System.currentTimeMillis() - t0);
        return result;
    }
}
