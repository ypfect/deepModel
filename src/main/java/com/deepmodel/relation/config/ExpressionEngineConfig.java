package com.deepmodel.relation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 表达式引擎配置（Feature Flag）。
 * <p>
 * 控制回写 SQL 的生成路径：本地引擎 vs HTTP 远程调用。
 * 默认 false（HTTP 模式），切换为 true 启用本地生成。
 */
@Component
public class ExpressionEngineConfig {

    @Value("${expression-engine.local-writeback-sql:false}")
    private boolean localWritebackSql;

    public boolean isLocalWritebackSql() {
        return localWritebackSql;
    }
}
