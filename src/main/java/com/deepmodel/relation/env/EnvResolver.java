package com.deepmodel.relation.env;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 环境/服务信息解析器：调用 ops 后台接口拉取环境列表与服务列表，
 * 并根据服务地址推导出 GraphQL endpoint 与回写 SQL API endpoint。
 *
 * 缓存策略：环境列表与某 env 的服务列表均缓存 5 分钟。
 */
@Service
public class EnvResolver {

    private static final Logger log = LoggerFactory.getLogger(EnvResolver.class);
    private static final String OPS_BASE = "http://ops.q7link.com:8080/api";

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Cache<String, List<Map<String, Object>>> envListCache = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    private final Cache<String, EnvInfo> envInfoCache = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    /** 拉取业务环境列表（不含 global 环境与服务详情）。 */
    public List<Map<String, Object>> listEnvs() {
        try {
            List<Map<String, Object>> all = envListCache.get("all", () -> fetchJsonArray(
                    OPS_BASE + "/qqsystem/busenv/?page=1&limit=999"));
            List<Map<String, Object>> business = new ArrayList<>();
            for (Map<String, Object> e : all) {
                if (!isGlobalEnv(e)) {
                    business.add(e);
                }
            }
            return business;
        } catch (ExecutionException e) {
            throw new RuntimeException("获取环境列表失败: " + e.getCause().getMessage(), e.getCause());
        }
    }

    /** 运维平台标记的 global 环境（identity 等），DeepModel 不可选。 */
    static boolean isGlobalEnv(Map<String, Object> env) {
        if (env == null) {
            return false;
        }
        Object flag = env.get("isGlobal");
        if (flag instanceof Boolean) {
            return (Boolean) flag;
        }
        return "true".equalsIgnoreCase(str(flag));
    }

    /**
     * 校验环境是否可用于 DeepModel（已启动且非 global）。
     * 不可用时抛出 {@link EnvServiceException}。
     */
    public void assertEnvRunnable(String envName) {
        if (envName == null || envName.isBlank()) {
            throw new IllegalArgumentException("envName 不能为空");
        }
        Map<String, Object> meta = findEnvMeta(envName);
        if (meta == null) {
            throw new EnvServiceException(envName, "ENV_NOT_FOUND",
                    "未找到环境「" + envName + "」，请从列表重新选择");
        }
        if (isGlobalEnv(meta)) {
            throw new EnvServiceException(envName, "ENV_IS_GLOBAL",
                    "「" + envName + "」是 global 环境，请选择业务环境");
        }
        String status = str(meta.get("envStatus"));
        if (isBlockedStatus(status)) {
            throw new EnvServiceException(envName, "ENV_NOT_STARTED",
                    "环境「" + envName + "」当前不可用（" + statusLabel(status)
                            + "），请换其他环境，或在运维平台启动后再试");
        }
    }

    /** 解析指定 env 的服务列表 + GraphQL/writeBack 地址。 */
    public EnvInfo resolve(String envName) {
        if (envName == null || envName.isBlank()) {
            throw new IllegalArgumentException("envName 不能为空");
        }
        assertEnvRunnable(envName);
        try {
            return envInfoCache.get(envName, () -> doResolve(envName));
        } catch (ExecutionException e) {
            throw new RuntimeException("解析环境 " + envName + " 失败: " + e.getCause().getMessage(), e.getCause());
        }
    }

    public String getGraphqlUrl(String envName) {
        return resolve(envName).graphqlUrl;
    }

    public String getWriteBackSqlApiUrl(String envName) {
        return resolve(envName).writeBackSqlApiUrl;
    }

    /** 强制刷新某 env 的缓存。 */
    public void invalidate(String envName) {
        if (envName != null) {
            envInfoCache.invalidate(envName);
        }
    }

    private Map<String, Object> findEnvMeta(String envName) {
        try {
            List<Map<String, Object>> all = envListCache.get("all", () -> fetchJsonArray(
                    OPS_BASE + "/qqsystem/busenv/?page=1&limit=999"));
            for (Map<String, Object> e : all) {
                if (envName.equals(e.get("envName"))) {
                    return e;
                }
            }
        } catch (ExecutionException e) {
            log.warn("[EnvResolver] 读取环境元信息失败: {}", e.getCause().getMessage());
        }
        return null;
    }

    /**
     * 仅拦截运维平台明确标记为不可用的状态。
     * <p>
     * 注意：部分生产环境 envStatus 为 {@code 0} 或空，但 GraphQL 实际可用（如 cn-apnorthbj-2），
     * 不能白名单只认 {@code started}。
     */
    static boolean isBlockedStatus(String envStatus) {
        if (envStatus == null || envStatus.isBlank()) {
            return false;
        }
        String s = envStatus.trim().toLowerCase();
        return "stopped".equals(s) || "start_err".equals(s);
    }

    static boolean isRunnableStatus(String envStatus) {
        return !isBlockedStatus(envStatus);
    }

    static String statusLabel(String envStatus) {
        if (envStatus == null || envStatus.isBlank()) {
            return "状态未知";
        }
        return switch (envStatus.trim().toLowerCase()) {
            case "started" -> "运行中";
            case "stopped" -> "已关闭";
            case "starting" -> "启动中";
            case "start_err" -> "启动失败";
            case "0" -> "状态未同步";
            default -> envStatus;
        };
    }

    private EnvInfo doResolve(String envName) throws IOException {
        Map<String, Object> envMeta = findEnvMeta(envName);
        if (envMeta == null) {
            throw new IOException("未在环境列表中找到 env=" + envName);
        }

        // 2. 拉服务列表
        List<Map<String, Object>> services = fetchJsonArray(
                OPS_BASE + "/qqtools/serverinfo/?page=1&limit=200&env=" + envName);

        String graphqlUrl = deriveGraphqlUrl(services, envName);
        String writeBackUrl = deriveWriteBackUrl(services, envName);

        return new EnvInfo(
                envName,
                str(envMeta.get("zhcnName")),
                str(envMeta.get("type")),
                str(envMeta.get("globalEnv")),
                graphqlUrl,
                writeBackUrl,
                services);
    }

    /**
     * 从服务列表中找 "Gql工具地址"，提取 host 拼成 GraphQL endpoint。
     * 例：https://graphql-test-tx-21.e7link.com/graphiql/index.html
     *  → https://graphql-test-tx-21.e7link.com/graphql/withoutAuth
     */
    private String deriveGraphqlUrl(List<Map<String, Object>> services, String envName) {
        for (Map<String, Object> s : services) {
            String name = str(s.get("service"));
            if (name != null && name.contains("Gql")) {
                String addr = str(s.get("serviceAddr"));
                if (addr != null && !addr.isBlank()) {
                    String trimmed = addr.trim();
                    // 取 origin（schema + host）
                    int schemaEnd = trimmed.indexOf("://");
                    if (schemaEnd > 0) {
                        int pathStart = trimmed.indexOf('/', schemaEnd + 3);
                        String origin = pathStart > 0 ? trimmed.substring(0, pathStart) : trimmed;
                        return origin + "/graphql/withoutAuth";
                    }
                }
            }
        }
        // 兜底：按命名约定推断
        log.warn("[EnvResolver] 未找到 Gql工具地址 服务，按命名约定推断 env={}", envName);
        return "https://graphql-" + envName + ".e7link.com/graphql/withoutAuth";
    }

    /**
     * 从服务列表找 "arap"，拼出回写 SQL API 地址。
     * 例：arap.test-tx-21.e7link.com → http://arap.test-tx-21.e7link.com/arap/gen/debug/writeBackField2sql
     */
    private String deriveWriteBackUrl(List<Map<String, Object>> services, String envName) {
        for (Map<String, Object> s : services) {
            if ("arap".equals(str(s.get("service")))) {
                String addr = str(s.get("serviceAddr"));
                if (addr != null && !addr.isBlank()) {
                    String trimmed = addr.trim();
                    if (!trimmed.startsWith("http")) {
                        trimmed = "http://" + trimmed;
                    }
                    return trimmed.replaceAll("/+$", "") + "/arap/gen/debug/writeBackField2sql";
                }
            }
        }
        return "http://arap." + envName + ".e7link.com/arap/gen/debug/writeBackField2sql";
    }

    private List<Map<String, Object>> fetchJsonArray(String url) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("ops 请求失败 " + response.code() + ": " + url);
            }
            String body = response.body() != null ? response.body().string() : "";
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            List<Map<String, Object>> list = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode item : data) {
                    list.add(objectMapper.convertValue(item,
                            objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class)));
                }
            }
            return list;
        }
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }
}
