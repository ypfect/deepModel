# Research: 元数据服务能力提取

## R1: 回写触发关系构建算法

**Decision**: 参考 platform `SmartLoader.buildEntityToWriteBackExprFields()` 的索引构建算法，在 DeepModel 中用 `BaseappObjectField` 的 `writeBackExpr` JSON 字段替代 platform 的 `Field.getWriteBackExpr()` 对象。

**Rationale**:
- platform 的核心算法是：遍历所有字段 → 找到有 writeBackExpr 的字段 → 按 `srcObjectType` 分组 → 建立 `srcObject → targetObject → Set<targetField>` 索引
- DeepModel 已有 `WriteBackExpr` 模型和 JSON 解析逻辑（`ImpactAnalyzerService` 中），直接复用
- 级联回写信息需要分析回写表达式中引用的变量字段是否本身也是被回写字段（即 A 回写 B 的 fieldX，而 fieldX 又回写 C）

**Alternatives Considered**:
- 直接调用 platform HTTP API：被排除，DeepModel 的核心价值在于独立诊断
- 复制 platform 的 `WriteBackExprUtils.getSourceEntityVarsAndCascadeWriteBackInfo()`：过重，依赖反射调用 app-common

## R2: 表达式字段依赖层级算法

**Decision**: 移植 platform `SmartLoader` 中 3 个构建方法的核心逻辑（`buildEntityExpressionFields` → `buildEntityFieldToExprFields` → `buildEntityLevelToExprFields`），简化为纯字符串解析。

**Rationale**:
- platform 用 `ExpressionUtils.getVariables()` 解析 expression 中的变量引用，DeepModel 已有 `ExprUtils.extractCamelFieldsFromSql()` 做类似事情
- 层级排序算法（`calcLevelToExprFields`）是纯拓扑排序，不依赖 platform 特有的 Entity/Field 类型
- 子表合并逻辑（将 detail entity 的表达式字段归入主表）需要从 `refer_info` 中识别 `isDetail=true` 的引用关系

**Alternatives Considered**:
- 使用现有 BFS 图引擎：BFS 解决的是跨对象影响传播，而表达式层级是同对象内的计算顺序问题，语义不同
- 使用 JSqlParser 解析：expression 不是标准 SQL，`ExpressionUtils.getVariables()` 用的是自定义解析器

## R3: 对象引用关系反向索引

**Decision**: 从 `BaseappObjectField.referInfo` JSON 中提取 `referEntities[].referEntityName` 和 `isDetail`，构建反向索引。

**Rationale**:
- DeepModel 已有 `selectReferencingFields` Mapper 查询（L109-L130），用 `jsonb_array_elements` 查找引用了指定对象的字段
- 反向索引的数据结构与 platform 的 `entityReferedInfos` 完全一致：`被引用对象 → 引用对象 → FK字段 → isDetail`
- 多态引用（referEntityFieldName 不为空）归入 "ALL" 键

**Alternatives Considered**:
- 每次查询时实时扫描：性能差，改为启动时一次性构建全量索引

## R4: 数据模型适配

**Decision**: 不引入 platform 的 Entity/Field 模型类，继续使用 `BaseappObjectField` + JSON 字符串解析。新增 3 个轻量级结果模型。

**Rationale**:
- `BaseappObjectField` 已有所需的全部原始字段
- platform 的 Entity/Field 是 Immutable 生成类，依赖链极深（38 个类），引入成本远大于收益
- DeepModel 只需要结果视图（查询响应），不需要运行时的 Field 对象树

## R5: 子表（Detail Entity）识别

**Decision**: 从 `refer_info` JSON 中提取 `isDetail=true` 的关系来识别子表。同时检查字段 `type` 是否为 LIST 且 `sourceInfo` 存在。

**Rationale**:
- platform 用 `FieldTypeEnum.LIST + sourceInfo.isDetail` 判断子表关系
- DeepModel 的 `BaseappObjectField` 中 `type` 字段值为 "LIST" 时结合 `referInfo` 中的 `isDetail` 可等效判断
- 但 `BaseappObjectField` 没有 `sourceInfo` 字段，需要通过 Mapper 从 DB 中新增查询
