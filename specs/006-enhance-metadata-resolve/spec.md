# Feature Specification: 增强 Resolve 元数据匹配接口

**Feature Branch**: `006-enhance-metadata-resolve`  
**Created**: 2026-05-11  
**Status**: Draft  
**Input**: 基于模型元数据说明文档，对 resolve 自然语言元数据匹配接口进行多维度增强，覆盖对象特性感知、枚举搜索、关系网络导航、表达式语义联动、bizType 维度匹配、变更单/多版本感知六大场景。

## User Scenarios & Testing *(mandatory)*

### User Story 1 — 按对象特性筛选 (Priority: P1)

AI Agent 在分析业务问题时，需要快速定位"哪些单据支持变更单"、"树型档案有哪些"、"自定义对象列表"等，当前 resolve 无法回答此类问题。

**Why this priority**: 实现成本最低（只需扩展 ObjectTypeMeta 加载更多字段），且是后续增强场景的基础——对象的 isTree/isDetail/isSupportChangeLog 等特性标记是理解对象语义的核心维度。

**Independent Test**: 输入"树型对象"、"支持变更单的单据"等查询，验证返回结果正确过滤了具有对应特性的对象。

**Acceptance Scenarios**:

1. **Given** 系统已加载元数据且多个对象具有不同特性, **When** 用户输入"树型对象", **Then** 返回所有 isTree=true 的对象列表，每个对象包含特性标签
2. **Given** 系统已加载元数据, **When** 用户输入"支持变更单的单据", **Then** 返回所有 isSupportChangeLog=true 且 type=bill 的对象
3. **Given** 系统已加载元数据, **When** 用户输入"自定义对象", **Then** 返回所有 isCustomizedEntity=true 的对象
4. **Given** 系统已加载元数据, **When** 用户输入"销售模块的单据", **Then** 返回 appName 或 businessModuleId 匹配"销售"相关的对象

---

### User Story 2 — bizType 维度字段匹配 (Priority: P1)

AI Agent 需要查找"金额字段"、"数量字段"、"价格字段"等，当前 resolve 仅通过字段名后缀判断金额/数量，未利用元模型定义的 bizType 标准类型（quantity/amount/price/ratio/percent/email/mobile/phone）进行匹配。

**Why this priority**: 实现成本极低（在 matchFields 中新增一路 bizType 关键词映射），直接提升字段匹配精度。

**Independent Test**: 输入"应收合同的价格字段"，验证返回了 bizType=price 的字段，而非仅靠名称包含"price"的字段。

**Acceptance Scenarios**:

1. **Given** 系统已加载元数据, **When** 用户输入"应收合同的金额字段", **Then** 返回 bizType=amount 的字段，且这些字段排在名称模糊匹配的结果前面
2. **Given** 系统已加载元数据, **When** 用户输入"哪些对象有邮箱字段", **Then** 全局搜索 bizType=email 的字段，按对象分组返回
3. **Given** 系统已加载元数据, **When** 用户输入"比率字段", **Then** 返回 bizType=ratio 或 bizType=percent 的字段

---

### User Story 3 — 枚举类型搜索 (Priority: P2)

AI Agent 需要了解"审批状态有哪些值"、"应收合同的类型有哪些"等枚举信息，当前 resolve 虽然返回字段的 enumType 但不参与搜索匹配，也无法展开枚举值。

**Why this priority**: 高频使用场景（理解字段可选值是分析业务逻辑的基础），但需要新增枚举数据的加载和索引机制。

**Independent Test**: 输入"审批状态"，验证返回匹配的枚举类型及其枚举值列表；输入"应收合同的类型有哪些"，验证先匹配对象再展开枚举字段的值。

**Acceptance Scenarios**:

1. **Given** 系统已加载枚举元数据, **When** 用户输入"审批状态", **Then** 返回 ApproveStatus 枚举类型匹配，包含所有枚举值（value + title）
2. **Given** 系统已加载元数据和枚举数据, **When** 用户输入"应收合同的单据类型有哪些", **Then** 先匹配 ArContract 对象，找到 businessTypeId 等枚举字段，返回对应枚举值列表
3. **Given** 系统已加载枚举元数据, **When** 用户输入"ApproveStatus", **Then** 返回精确匹配的枚举类型

---

### User Story 4 — 表达式语义联动 (Priority: P2)

AI Agent 排查字段计算问题时，需要知道某字段被哪些表达式字段依赖、回写来源是什么等。当前 resolve 返回的字段信息不包含依赖/回写摘要，需要额外调用专用接口才能获取。

**Why this priority**: ExpressionFieldService 已经构建了完整的依赖图，作为字段匹配结果的附加信息返回，联动成本低，收益大。

**Independent Test**: 搜索某对象的 quantity 字段，验证返回结果中附带了 dependedByCount 和 writeBackSource 摘要信息。

**Acceptance Scenarios**:

1. **Given** ExpressionFieldService 已构建依赖索引, **When** 用户搜索"ArContractSubjectMatterItem quantity", **Then** 返回的字段匹配结果中附带 dependedByCount（被多少个表达式字段依赖）和 dependedByFields（依赖字段名列表）
2. **Given** 系统已加载元数据, **When** 用户搜索一个有 writeBackExpr 的字段, **Then** 返回的字段匹配结果中附带 writeBackSource 摘要（srcObjectType + expression 简述）
3. **Given** 用户获得字段匹配结果, **When** 需要深入分析完整依赖链, **Then** 可通过 /api/skills/impact 等专用接口下钻

---

### User Story 5 — 关系网络导航 (Priority: P3)

AI Agent 需要理解"哪些对象引用了客户"、"附件属于哪些对象"等反向关系和多态参照，当前 resolve 只支持正向单跳引用。

**Why this priority**: 实现复杂度最高（需要解析 referInfo JSON 建立完整的双向关系图），但对理解对象网络的价值很大。

**Independent Test**: 输入"哪些对象引用了 Customer"，验证返回所有 referInfo.referEntityName=Customer 的字段及其所属对象。

**Acceptance Scenarios**:

1. **Given** 系统已构建双向关系图, **When** 用户输入"哪些对象引用了 Customer", **Then** 返回所有通过外键参照 Customer 的对象及字段列表
2. **Given** 系统已解析多态参照, **When** 用户输入"附件属于哪些对象", **Then** 识别 Attachment 的 objectId 多态参照，返回所有 referEntities 列表
3. **Given** 系统已解析反向参照, **When** 用户输入"客户的主联系人", **Then** 识别 referedFieldName=primaryContact + referedCondition，返回 Contact 对象

---

### Edge Cases

- 输入同时包含特性关键词和字段查询时（如"树型对象的上级"），应先按特性过滤对象再匹配字段
- 枚举类型名和字段名冲突时（如 Status 既是枚举类型又是字段名），应在结果中同时返回两种匹配并标注类型
- bizType 中文关键词存在歧义时（如"数量"可能指 bizType=quantity，也可能指字段标题包含"数量"），应优先 bizType 精确匹配，名称匹配作为补充
- 依赖图中存在循环依赖的字段，查询其依赖关系时应标注循环并限制展示深度

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统必须在加载元数据时，通过复用 selectEntityMetadataContents 的 content JSON（在 enrichFieldMetadata 同一遍历中），额外提取对象级特性标记（isTree, isDetail, isSupportChangeLog, isCustomizedEntity, isMultiDataVersion, businessModuleId, appName）并填充到 ObjectTypeMeta
- **FR-002**: 系统必须支持按特性关键词（"树型"、"子表"、"变更单"、"自定义"、"多版本"）过滤对象匹配结果
- **FR-003**: 系统必须在字段匹配时，增加一路 bizType 维度匹配，支持中文关键词到 bizType 标准值的映射（"金额"→amount, "数量"→quantity, "价格"→price, "比率"→ratio, "百分比"→percent, "邮箱"→email, "手机"→mobile, "电话"→phone）
- **FR-004**: 系统必须加载枚举类型（EnumType）和枚举值（EnumValue）的索引，支持按枚举名称和标题搜索
- **FR-005**: 系统必须支持将枚举搜索与字段匹配联动——当字段的 enumType 被搜索命中时，展开返回枚举值列表
- **FR-006**: 系统必须在字段匹配结果中附带依赖摘要信息（dependedByCount + dependedByFields），数据来源为 ExpressionFieldService 的 fieldToExprFields 反向映射，不改变 resolve 的自然语言解析逻辑
- **FR-007**: 系统必须在有 writeBackExpr 的字段匹配结果中附带回写来源摘要（writeBackSource: srcObjectType + expression 简述），Agent 可通过专用接口下钻完整依赖链
- **FR-008**: 系统必须构建基于 referInfo 的双向关系索引（正向参照 + 反向被引用 + 多态参照），支持"哪些对象引用了 XX"类查询
- **FR-009**: 所有新增的匹配维度必须纳入现有的 resolveCache 缓存机制，数据刷新时同步清除

### Key Entities

- **ObjectTypeMeta**: 对象类型元信息，需扩展 isTree/isDetail/isSupportChangeLog/isCustomizedEntity/isMultiDataVersion/businessModuleId/appName 字段
- **EnumTypeMeta**: 枚举类型元信息（name, title, description, isCustomizable），数据来源为 baseapp_system_metadata 表中 type_id LIKE '%enum%' 的 content JSON
- **EnumValueMeta**: 枚举值元信息（value, title, ordinal, isDisabled, isHidden），嵌套在 EnumTypeMeta 的 enumValueDefs 数组中
- **EnumMatch**: 枚举匹配结果模型，作为 ResolveResult 的顶级 enumMatches 列表元素，与 objectMatches 平级
- **ReferRelation**: 参照关系描述（sourceObject, sourceField, targetObject, isDetail, isPolymorphic, referedFieldName, referedCondition）

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 输入对象特性关键词（"树型对象"、"支持变更单"等）时，匹配准确率达到 95% 以上
- **SC-002**: 输入 bizType 中文关键词（"金额字段"、"价格字段"等）时，bizType 精确匹配的字段排在结果首位
- **SC-003**: 枚举类型搜索的响应时间与现有对象搜索持平（首次查询 < 200ms，缓存命中 < 10ms）
- **SC-004**: 表达式依赖查询能在 5 层以内完整展示依赖链，循环依赖场景正确标注
- **SC-005**: 反向引用查询能覆盖所有通过 referInfo 定义的参照关系（普通外键 + 多态参照 + 反向参照）

## Clarifications

### Session 2026-05-11

- Q: 枚举匹配结果应放在响应结构的哪个位置？ → A: 在 ResolveResult 中新增 enumMatches 顶级列表，与 objectMatches 平级。枚举类型不属于"对象"概念，独立的顶级字段更干净。
- Q: 对象特性（isTree/isDetail 等）的数据来源和加载策略？ → A: 复用 selectEntityMetadataContents 已有的 content JSON，在 enrichFieldMetadata 同一遍历中提取对象级特性，扩展到 ObjectTypeMeta。无需新增 SQL。
- Q: 表达式依赖查询的触发方式？ → A: 不改变 resolve 的自然语言解析逻辑，而是在字段匹配结果中附带依赖/回写摘要信息（dependedByCount、writeBackSource），Agent 可通过专用接口下钻。

## Assumptions

- 元数据已通过现有的 SQL 查询从 baseapp_object_type 和 baseapp_object_field 表加载，新增字段只需扩展现有 SQL
- 枚举数据来源于 baseapp_system_metadata 表中 type_id LIKE '%enum%' 的 content JSON（而非独立的 enum_type/enum_value 表），当前已有 loadEnumDefinitions 方法加载了 name 到 Set(value) 映射，需扩展为保存完整的 title/ordinal/isDisabled 等属性
- referInfo 字段以 JSON 字符串存储在 baseapp_object_field 表中，可通过 JSON 解析提取结构化关系信息
- ExpressionFieldService 的依赖图在系统启动时已构建完成，resolve 可直接引用
- 所有新增能力不影响现有 resolve 接口的入参签名，通过扩展响应结构实现向后兼容

