# Research: 增强 Resolve 元数据匹配接口

**Date**: 2026-05-11

## R1: 对象特性数据来源验证

**Decision**: 从 `selectEntityMetadataContents` 返回的 content JSON 中提取对象特性。

**Rationale**: 
- `enrichFieldMetadata()` 已遍历所有 entity 的 content JSON（`mapper.selectEntityMetadataContents()`），当前只提取字段级属性
- content JSON 根节点包含 `isTree`、`isDetail`、`isSupportChangeBill`、`isMultiDataVersion` 等对象级字段
- 在同一遍历中读取根节点属性，零额外 SQL 成本
- `isSupportChangeBill` 虽已有独立 SQL（`selectChangeBillSupportedEntities`），但可以在 enrichFieldMetadata 中统一提取，保持一致性

**Alternatives considered**: 
- 为每个特性写独立 SQL → 拒绝，SQL 数量线性增长
- 修改 `selectObjectTitles` 的 SQL 加 JOIN → baseapp_object_type 表上没有这些列，需要 JOIN baseapp_system_metadata，不如直接复用已有 JSON 遍历

## R2: 枚举索引扩展可行性

**Decision**: 扩展 `loadEnumDefinitions()` 方法，从 `enumValueDefs` 中保存完整的 title/ordinal/isDisabled。

**Rationale**:
- 当前 `loadEnumDefinitions` 已解析 content JSON 到 `enumValueMap: Map<String, Set<String>>`（只保存了 value）
- JSON 结构中每个 enumValueDef 包含 `value`/`title`/`ordinal`/`isDisabled`/`isHidden`，只需多读几个字段
- 新增 `Map<String, EnumTypeMeta> enumTypeIndex`，每个 EnumTypeMeta 包含 name/title 和 List<EnumValueMeta>
- 枚举搜索索引：建立 title → enumName 反向索引（类似对象的 titleToObjectTypes）

**Alternatives considered**: 
- 从独立的 enum_type/enum_value 表查 → 表在部分环境可能不存在，且已有 JSON 数据源

## R3: ResolveResult 扩展兼容性

**Decision**: 在 `ResolveResult` 中新增 `List<EnumMatch> enumMatches` 字段。

**Rationale**:
- Java 类新增字段，Jackson 序列化后 JSON 自动包含新 key
- 如果 `enumMatches` 为空列表，JSON 输出 `"enumMatches":[]`，不影响旧客户端的 `objectMatches` 解析
- Agent 端（Python）的响应解析代码只取 `objectMatches`，新增字段被忽略，向后兼容

**Alternatives considered**:
- 枚举作为 ObjectMatch(type="enum") → 会混淆对象匹配的语义
- 新增独立接口 → 增加 Agent 调用次数，不如一次请求返回所有匹配维度

## R4: FieldMatch 附加摘要信息

**Decision**: 在 `FieldMatch` 中新增 `dependedByCount` / `dependedByFields` / `writeBackSource` 字段。

**Rationale**:
- `ExpressionFieldService.getExpressionFieldInfo(objectType)` 返回 `ExpressionFieldInfo`，包含 `fieldToExprFields: Map<String, Set<String>>`（变量字段 → 依赖它的表达式字段集合）
- 在构建 FieldMatch 时，查一次 fieldToExprFields 即可填充 dependedByCount/dependedByFields
- writeBackExpr 已在 BaseappObjectField 上加载，解析 srcObjectType + expression 拼接为摘要字符串
- 这些都是 O(1) 查表操作，不影响性能

**Alternatives considered**:
- 在 resolve 的 NLP 解析中识别"依赖"意图 → 拒绝，自然语言推断意图容易误判，摘要模式更稳定

## R5: bizType 关键词映射

**Decision**: 在 `matchFields` 中新增一路 bizType 维度匹配。

**Rationale**:
- `BaseappObjectField.bizType` 字段已在 `selectAll` SQL 中加载
- 当前仅用于金额/数量的后缀判断（AMOUNT_SUFFIXES / QTY_EXACT_NAMES），未利用 bizType 列
- 新增中文关键词 → bizType 映射表：`{"金额":"amount", "数量":"quantity", "价格":"price", "比率":"ratio", "百分比":"percent", "邮箱":"email", "手机":"mobile", "电话":"phone"}`
- 在 parseQuery 之后、matchFields 之前检查 fieldPart 是否命中 bizType 关键词
- 命中时，在字段匹配中增加一路 bizType 精确过滤，给予高基础分（800，高于 TITLE_CONTAINS 的 600）

**Alternatives considered**:
- 纯依赖名称后缀 → 当前方案，miss rate 高（如 unitPrice 的 bizType=price 但不含 amount/qty 后缀）

## R6: EntityReferenceService 现有能力

**Decision**: 利用已有的 `EntityReferenceService.buildIndex(allRows)` 构建的反向索引。

**Rationale**:
- EntityReferenceService 在 reload 时已被调用，已有数据基础
- 需要确认其是否已提供按目标对象查引用方的 API
- 如果没有，需在 EntityReferenceService 中新增 `getReferencingEntities(targetObjectType)` 方法

**Alternatives considered**: 
- 在 SkillsService 中重复解析 referInfo → 违反 DRY
