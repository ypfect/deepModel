# Expression Consistency Checker Quickstart

校验引擎作为纯内存推演工具被提供。它可以用来检测在代码层或表结构层对数据模型做出增删改之后，是否破坏了遗留的依赖表达式。

1. **环境准备与同步**
   首先使用系统中已有的端点完成元数据同步（如需要）：
   `POST /api/reload`（如果系统不会启动时自动拉取的话）

2. **触发全局扫描**
   通过 cURL 或者浏览器直接发起 RESTful 请求开始应用级别的全量语法及关联引用健康检查：
   ```bash
   curl http://localhost:18080/api/validation/report?appName=arap | jq
   ```

3. **结果判读**
   检查输出的 `totalErrors`。重点关注 `severity` 为 `FATAL` 级别的项，它说明对应的 `expression` 是彻头彻尾的错误 SQL 语法，需立刻排查并在元数据中清理。
