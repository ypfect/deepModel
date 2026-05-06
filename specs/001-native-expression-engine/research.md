# Research: 本地化表达式解析引擎

**Date**: 2026-05-05

## R1: HTTP 调用点定位与替换策略

**Decision**: `UpgradeScriptService.callWriteBackSqlApi()` (L1125-1158) 是唯一的回写 SQL HTTP 调用点，调用路径为 `appendWriteBackSql()` → `callWriteBackSqlApi()` → OkHttp POST 到 `arap.{env}.e7link.com/arap/gen/debug/writeBackField2sql`

**Rationale**: 
- 通过全局搜索 `httpClient.newCall` 确认仅此一处与回写 SQL 生成相关
- 其他 OkHttp 使用（`BatchRequest`、`BatchInvoiceToRevenueRequest`）是独立的工具类，与本次迁移无关
- `resolveWriteBackApiUrl()` 从 JDBC URL 推导环境名，再拼 arap 微服务地址——这是强环境耦合

**Alternatives considered**:
- 全局替换所有 OkHttp 调用 → 范围过大，其他 HTTP 调用有独立用途

## R2: Platform 回写 SQL 生成逻辑分析

**Decision**: 参考 `WriteBackWorker` 的 EQL 模板（L50-55）在本地生成等效的 PostgreSQL UPDATE 语句

**Rationale**:
- Platform 中回写 SQL 的核心模板：
  ```
  writeBackValueEqlTemplate = "%s=(select %s from %s where %s=m.id and isDeleted=false %s)"
  writeBackEqlTemplate = "update %s set %s %s id in (:targetIds)"
  ```
- DeepModel 中 `UpgradeScriptService.appendWriteBackSql()` 已有 trigger SQL 的本地生成逻辑（`appendIntraSql`），回写 SQL 生成可参照相同模式
- 核心输入数据已有：`WriteBackExpr`（srcObjectType, idField, expression, condition）全部可从 `ImpactAnalyzerService` 缓存获取

**Key findings**:
1. `WriteBackExpr` 模型已包含所有必要字段（包括 executingMoment、validateExpr）
2. `ImpactAnalyzerService.parseWriteBack()` 已能解析 writeBackExpr JSON（L808-822）
3. `ExprUtils` 已有 `extractCamelFieldsFromSql()`、`snakeToCamel()` 等工具方法
4. `UpgradeScriptService` 已有 `objectTypeToTableName()`、`fieldCamelToColumnName()`、`convertFormulaToSnakeCase()` 等 EQL→SQL 转换工具
5. `appendIntraSql()` 已实现 trigger 字段的本地 SQL 生成，回写 SQL 可参考其模式

**Alternatives considered**:
- 引入 platform 的 EqlExecutor → 过重，EQL→SQL 转换仅涉及简单的驼峰→下划线、对象名→表名映射，`UpgradeScriptService` 已有现成工具

## R3: triggerExpr 解析现状分析

**Decision**: triggerExpr 的依赖解析已在本地完成（`buildTriggerAliasMap()`、`buildIntraDependencies()`），无需额外迁移

**Rationale**:
- `ImpactAnalyzerService.buildTriggerAliasMap()` (L853-875) 已使用 `ExprUtils.extractCamelFieldSequence()` 解析 triggerExpr 并建立同对象内的依赖映射
- `buildIntraDependencies()` 和 `buildIntraUpstreamDependencies()` 已用于影响分析图的 intra 边构建
- 唯一缺失的是：triggerExpr 的 SQL 生成（`appendIntraSql()`）已在 `UpgradeScriptService` 本地完成
- 结论：triggerExpr 方面不存在 HTTP 调用，本项目只需关注 writeBackExpr 的 SQL 生成本地化

## R4: WriteBackExpr 完整语义解析增强

**Decision**: 当前 `parseWriteBack()` 仅提取 srcObjectType、expression、condition 三个字段，需增强解析 idField、executingMoment、validateExpr

**Rationale**:
- `WriteBackExpr` Java 模型已包含全部 7 个字段，但 `parseWriteBack()` 只读取了 3 个
- 回写 SQL 生成需要 `idField`（确定关联条件）
- 依赖图谱需要 `executingMoment`（时机语义展示）
- 这是一个小改动，只需在 `parseWriteBack()` 中增加 JSON 字段提取

## R5: Feature Flag 配置设计

**Decision**: 使用 Spring Boot `@Value` 配置属性 `expression-engine.local-writeback-sql=false`

**Rationale**:
- 与现有 `writeback-sql.api-url` 和 `writeback-sql.tenant-id` 配置风格一致
- `false` 默认值保证向后兼容（继续走 HTTP）
- 切换为 `true` 后 `appendWriteBackSql()` 直接调用 `WriteBackSqlGenerator` 生成 SQL
