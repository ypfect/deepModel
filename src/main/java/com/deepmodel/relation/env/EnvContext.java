package com.deepmodel.relation.env;

/**
 * 当前请求的环境名（ThreadLocal）。
 * 由 {@link EnvFilter} 从 HTTP Header X-Env 注入；请求结束清理。
 */
public final class EnvContext {

    /** 本地 PostgreSQL 元数据快照的 env 键（与运维环境名区分）。 */
    public static final String LOCAL_ENV_KEY = "@local";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private EnvContext() {}

    public static boolean isLocalEnv(String env) {
        return LOCAL_ENV_KEY.equals(env);
    }

    public static void set(String env) {
        if (env == null || env.isBlank()) {
            CURRENT.remove();
        } else {
            CURRENT.set(env.trim());
        }
    }

    public static String currentOrNull() {
        return CURRENT.get();
    }

    public static String requireCurrent() {
        String env = CURRENT.get();
        if (env == null || env.isBlank()) {
            throw new IllegalStateException("当前请求未指定环境（X-Env header 缺失），请先在前端选择环境");
        }
        return env;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
