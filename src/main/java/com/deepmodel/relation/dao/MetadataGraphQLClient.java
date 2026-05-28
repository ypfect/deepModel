package com.deepmodel.relation.dao;

import com.deepmodel.relation.env.EnvContext;
import com.deepmodel.relation.env.EnvResolver;
import com.deepmodel.relation.env.EnvServiceException;
import com.deepmodel.relation.util.OkHttpSsl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * GraphQL HTTP 客户端：URL 与 tenant 都按当前请求的环境动态解析（{@link EnvContext}）。
 * 启动期不再依赖任何 metadata-source.graphql-url 配置。
 */
@Component
public class MetadataGraphQLClient {

    private static final Logger log = LoggerFactory.getLogger(MetadataGraphQLClient.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient;
    private final EnvResolver envResolver;

    @Value("${metadata-source.tenant-id:0}")
    private String tenantId;

    @Value("${metadata-source.page-size:100000}")
    private int pageSize;

    public MetadataGraphQLClient(
            EnvResolver envResolver,
            @Value("${metadata-source.trust-all-ssl:true}") boolean trustAllSsl) {
        this.envResolver = envResolver;
        this.httpClient = OkHttpSsl.newBuilder(trustAllSsl).build();
        if (trustAllSsl) {
            log.warn("[MetadataGraphQL] trust-all-ssl=true，已跳过 HTTPS 证书校验（仅建议内网开发使用）");
        }
    }

    public int getPageSize() {
        return pageSize;
    }

    /**
     * 解析当前请求上下文（{@link EnvContext}）对应的 GraphQL endpoint。
     * 若 EnvContext 未设置，则抛 {@link IllegalStateException}。
     */
    private String currentGraphqlUrl() {
        String env = EnvContext.requireCurrent();
        return envResolver.getGraphqlUrl(env);
    }

    public JsonNode execute(String query) {
        return executeAgainst(currentGraphqlUrl(), query);
    }

    /** 显式指定 env 执行（用于跨 env 比较场景，比如 Version Comparison）。 */
    public JsonNode executeWithEnv(String env, String query) {
        if (env == null || env.isBlank()) {
            return execute(query);
        }
        return executeAgainst(envResolver.getGraphqlUrl(env), query);
    }

    private JsonNode executeAgainst(String url, String query) {
        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("variables", null);
        body.put("operationName", null);
        try {
            String json = objectMapper.writeValueAsString(body);
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(json, JSON))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Tenant-Id", tenantId)
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    String env = EnvContext.currentOrNull();
                    String friendly = humanizeHttpError(response.code(), respBody, env);
                    if (friendly != null) {
                        throw new EnvServiceException(
                                env != null ? env : "",
                                "ENV_SERVICE_UNAVAILABLE",
                                friendly);
                    }
                    throw new IOException("GraphQL HTTP " + response.code() + ": " + truncate(respBody));
                }
                JsonNode root = objectMapper.readTree(respBody);
                JsonNode errors = root.get("errors");
                if (errors != null && errors.isArray() && !errors.isEmpty()) {
                    throw new IOException("GraphQL errors: " + errors);
                }
                return root.path("data");
            }
        } catch (EnvServiceException e) {
            throw e;
        } catch (IOException e) {
            String env = EnvContext.currentOrNull();
            String friendly = humanizeConnectionError(e.getMessage(), env);
            if (friendly == null) {
                friendly = humanizeHttpError(-1, e.getMessage(), env);
            }
            if (friendly != null) {
                throw new EnvServiceException(env != null ? env : "", "ENV_SERVICE_UNAVAILABLE", friendly);
            }
            throw new RuntimeException("GraphQL 请求失败: " + e.getMessage(), e);
        }
    }

    /** SSL 握手、证书链等连接层错误。 */
    static String humanizeConnectionError(String message, String env) {
        if (message == null) {
            return null;
        }
        String m = message.toLowerCase();
        if (m.contains("pkix") || m.contains("certpath") || m.contains("certificate")
                || m.contains("sslhandshake") || m.contains("certificate_unknown")) {
            String prefix = (env != null && !env.isBlank()) ? "环境「" + env + "」" : "GraphQL";
            return prefix + " HTTPS 证书未被 Java 信任（PKIX）。浏览器能访问但 DeepModel 用 JVM 自带证书库校验会失败；"
                    + "可在 application.yml 设置 metadata-source.trust-all-ssl=true（内网开发），"
                    + "或将企业 CA 导入 JVM cacerts";
        }
        return null;
    }

    /** 将 nginx 503 HTML 等转为用户可读说明；无法识别时返回 null。 */
    static String humanizeHttpError(int code, String body, String env) {
        String b = body != null ? body : "";
        boolean html503 = b.contains("503 Service Temporarily Unavailable")
                || b.contains("<title>503") || b.contains("<h1>503");
        if (code == 503 || html503) {
            String prefix = (env != null && !env.isBlank()) ? "环境「" + env + "」" : "该环境";
            return prefix + "的 GraphQL 服务不可用（503），通常表示环境已关闭或未部署，请换已启动的环境";
        }
        if (code == 502 || b.contains("502 Bad Gateway")) {
            String prefix = (env != null && !env.isBlank()) ? "环境「" + env + "」" : "该环境";
            return prefix + "网关无响应（502），可能正在启动或已关闭";
        }
        if (b.trim().startsWith("<") && code >= 400) {
            String prefix = (env != null && !env.isBlank()) ? "环境「" + env + "」" : "环境";
            return prefix + "服务返回异常（HTTP " + (code > 0 ? code : "错误") + "），可能已关闭";
        }
        return null;
    }

    private static String truncate(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() > 200 ? t.substring(0, 200) + "…" : t;
    }

    public JsonNode queryRoot(String rootField, String criteriaStr, String selection) {
        String gql = "{\n  " + rootField + "(criteriaStr:\"" + escapeCriteria(criteriaStr) + "\") {\n"
                + selection + "\n  }\n}";
        return execute(gql).path(rootField);
    }

    public JsonNode queryRootWithEnv(String env, String rootField, String criteriaStr, String selection) {
        String gql = "{\n  " + rootField + "(criteriaStr:\"" + escapeCriteria(criteriaStr) + "\") {\n"
                + selection + "\n  }\n}";
        return executeWithEnv(env, gql).path(rootField);
    }

    public List<JsonNode> queryAllPages(String rootField, String baseCriteria, String selection) {
        return queryAllPagesWithEnv(null, rootField, baseCriteria, selection);
    }

    /**
     * 分页拉取全量数据。不再预先 count(*)，按页拉取直到返回条数 &lt; pageSize，每类查询少 1 次 HTTP。
     */
    public List<JsonNode> queryAllPagesWithEnv(String env, String rootField, String baseCriteria, String selection) {
        List<JsonNode> all = new ArrayList<>();
        int offset = 0;
        while (true) {
            String criteria = baseCriteria + " limit " + pageSize + " offset " + offset;
            JsonNode page = (env == null || env.isBlank())
                    ? queryRoot(rootField, criteria, selection)
                    : queryRootWithEnv(env, rootField, criteria, selection);
            if (!page.isArray() || page.isEmpty()) {
                break;
            }
            for (JsonNode item : page) {
                all.add(item);
            }
            int fetched = page.size();
            offset += fetched;
            if (fetched < pageSize) {
                break;
            }
        }
        return all;
    }

    public long count(String entity, String criteriaStr) {
        return countWithEnv(null, entity, criteriaStr);
    }

    public long countWithEnv(String env, String entity, String criteriaStr) {
        String gql = "{\n  AggregateQueryOne(entity: \"" + entity + "\", criteriaStr:\""
                + escapeCriteria(criteriaStr) + "\") {\n    count: aggr(expr: \"count(*)\", isGroup: false)\n  }\n}";
        JsonNode data = (env == null || env.isBlank())
                ? execute(gql).path("AggregateQueryOne").path("count")
                : executeWithEnv(env, gql).path("AggregateQueryOne").path("count");
        return data.isNumber() ? data.asLong() : 0L;
    }

    static String escapeCriteria(String criteria) {
        return criteria.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static String escapeSqlLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    public ArrayNode toArrayNode(List<JsonNode> nodes) {
        ArrayNode array = objectMapper.createArrayNode();
        for (JsonNode node : nodes) {
            array.add(node);
        }
        return array;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    void logSample(String label, JsonNode node) {
        if (log.isDebugEnabled()) {
            log.debug("{} sample: {}", label, node);
        }
    }

    public static List<JsonNode> asList(JsonNode arrayNode) {
        List<JsonNode> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode n : arrayNode) {
                list.add(n);
            }
        }
        return list;
    }

    public static void forEachField(JsonNode objectNode, java.util.function.BiConsumer<String, JsonNode> consumer) {
        Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            consumer.accept(entry.getKey(), entry.getValue());
        }
    }
}
