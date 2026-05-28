package com.deepmodel.relation.env;

/**
 * 环境不可用（已关闭、未启动、GraphQL 503 等）时抛出，由全局异常处理返回友好 JSON。
 */
public class EnvServiceException extends RuntimeException {

    private final String env;
    private final String code;

    public EnvServiceException(String env, String code, String userMessage) {
        super(userMessage);
        this.env = env != null ? env : "";
        this.code = code != null ? code : "ENV_UNAVAILABLE";
    }

    public String getEnv() {
        return env;
    }

    public String getCode() {
        return code;
    }

    /** 面向用户的中文说明 */
    public String getUserMessage() {
        return getMessage();
    }
}
