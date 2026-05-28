package com.deepmodel.relation.env;

import java.util.List;
import java.util.Map;

/**
 * 单个环境的完整运行时信息：基本属性 + 推导出的关键服务地址。
 */
public class EnvInfo {

    /** 环境名，例如 test-tx-21 */
    public final String envName;
    /** 中文名 */
    public final String zhcnName;
    /** 类型：prod / test / dev / ... */
    public final String type;
    /** 全局环境 */
    public final String globalEnv;
    /** GraphQL endpoint，例如 https://graphql-test-tx-21.e7link.com/graphql/withoutAuth */
    public final String graphqlUrl;
    /** 回写 SQL API endpoint，例如 http://arap.test-tx-21.e7link.com/arap/gen/debug/writeBackField2sql */
    public final String writeBackSqlApiUrl;
    /** 原始服务列表（仅在 detail 接口返回） */
    public final List<Map<String, Object>> services;

    public EnvInfo(String envName, String zhcnName, String type, String globalEnv,
                   String graphqlUrl, String writeBackSqlApiUrl, List<Map<String, Object>> services) {
        this.envName = envName;
        this.zhcnName = zhcnName;
        this.type = type;
        this.globalEnv = globalEnv;
        this.graphqlUrl = graphqlUrl;
        this.writeBackSqlApiUrl = writeBackSqlApiUrl;
        this.services = services;
    }
}
