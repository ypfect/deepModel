# Feature Specification: Agent 自然语言元数据匹配

**Feature Branch**: `004-agent-metadata-matching`  
**Created**: 2026-05-09  
**Status**: Draft  
**Input**: User description: "元数据服务有哪些信息，我在 Agent 里面说应收合同或者应收合同的子表什么的，如果 AI 自己猜测的话可能不准确，我想结合元数据服务根据我输入的内容匹配，然后召回匹配的合理的信息，然后判断真正的对象和字段信息"

## User Scenarios & Testing

### User Story 1 - 自然语言解析对象名称 (Priority: P1)

AI Agent 用户在对话中使用中文业务术语（如"应收合同"、"采购订单子表"、"收入确认"）描述对象，系统自动将其匹配到准确的元数据对象（如 `ArContract`、`PurchaseOrderItem`、`RevenueConfirmation`）。

**Why this priority**: 这是核心需求。AI Agent 无法准确识别对象名是下游所有元数据查询的阻碍点。如果对象识别不准，后续的字段查询、影响分析等操作全部无效。

**Independent Test**: 可通过调用匹配 API 传入中文对象名，验证返回结果是否包含正确的对象及其元数据摘要。

**Acceptance Scenarios**:

1. **Given** 元数据服务已加载所有对象信息，**When** 用户输入"应收合同"，**Then** 系统返回 `ArContract` 及其中文标题和子表列表作为最佳匹配
2. **Given** 元数据服务已加载所有对象信息，**When** 用户输入"应收合同的子表"，**Then** 系统返回 `ArContract` 的所有直接子表对象列表（如 `ArContractSubjectMatterItem` 等）
3. **Given** 元数据服务已加载所有对象信息，**When** 用户输入"收款单"，**Then** 系统返回与收款相关的对象列表，按匹配度排序
4. **Given** 元数据服务已加载所有对象信息，**When** 用户输入一个不存在的对象名"火箭发射器"，**Then** 系统返回空结果或低置信度提示

---

### User Story 2 - 自然语言解析字段名称 (Priority: P1)

AI Agent 用户在对话中提到具体字段（如"应收合同的原始金额"、"采购订单数量"），系统自动匹配到准确的对象+字段组合。

**Why this priority**: 与对象匹配同等重要。Agent 场景中经常需要确定某个业务概念对应哪个具体字段名。

**Independent Test**: 可通过调用匹配 API 传入中文字段描述，验证返回结果是否包含正确的对象和字段名。

**Acceptance Scenarios**:

1. **Given** 元数据服务已加载所有字段信息，**When** 用户输入"应收合同的原始金额"，**Then** 系统返回 `ArContract.originAmount` 及其字段元数据（标题、类型、业务类型）
2. **Given** 元数据服务已加载所有字段信息，**When** 用户输入"合同金额"，**Then** 系统返回所有对象中包含"金额"相关字段的匹配列表，按相关性排序
3. **Given** 元数据服务已加载所有字段信息，**When** 用户输入"付款状态"，**Then** 系统返回所有对象中包含付款状态的字段匹配

---

### User Story 3 - 匹配结果附带上下文信息 (Priority: P2)

匹配结果不仅返回对象/字段的技术名称，还附带业务上下文信息（如子表关系、回写关系、字段分类），帮助 AI Agent 做出更准确的判断。

**Why this priority**: 只返回名称不够，Agent 需要足够的上下文来判断匹配是否合理以及如何使用。

**Independent Test**: 可验证匹配结果中是否包含对象的子表关系、字段的业务类型等上下文信息。

**Acceptance Scenarios**:

1. **Given** 用户查询了"应收合同"，**When** 系统返回匹配结果，**Then** 结果中包含该对象的中文标题、子表列表和入站/出站回写关系摘要
2. **Given** 用户查询了"应收合同的原始金额"，**When** 系统返回字段匹配结果，**Then** 结果中包含字段的中文标题、业务类型（如 Amount）、是否有回写/触发表达式

---

### Edge Cases

- 用户输入模糊简称（如"合同"可匹配应收合同 ArContract 和应付合同 ApContract），系统应返回多个候选并按匹配度排序
- 用户输入拼写错误或近似词（如"应收和同"），系统应能容忍一定程度的模糊匹配
- 用户同时提及对象和字段（如"采购订单的税额"），系统应能同时解析对象和字段
- 用户使用英文技术名（如"ArContract"），系统应直接精确匹配

## Requirements

### Functional Requirements

- **FR-001**: 系统 MUST 提供一个统一的 REST HTTP 匹配 API（与现有 SkillsController 同级，如 `/api/skills/resolve`），接收用户输入文本，返回匹配的对象和/或字段信息
- **FR-002**: 系统 MUST 支持中文业务名称到对象 PascalCase 名称的映射（利用已有的 `objectTitles` 和 `GLOBAL_SYNONYMS`）
- **FR-003**: 系统 MUST 支持中文字段标题到字段 camelCase 名称的映射（利用已有的字段 `title` 属性）
- **FR-004**: 当用户提及"XX 的子表"时，系统 MUST 利用已有的 `mainToDetails` 映射返回子表列表
- **FR-005**: 匹配结果 MUST 包含置信度评分，供 Agent 判断匹配质量。评分规则：精确英文名（1.0）> 同义词精确匹配（0.9）> 中文标题精确匹配（0.8）> 中文标题包含匹配（0.6）
- **FR-006**: 匹配结果 MUST 包含对象/字段的业务上下文（标题、子表关系、字段分类等）
- **FR-007**: 系统 MUST 支持对英文精确名称（PascalCase/camelCase）的直接匹配，优先级高于模糊匹配
- **FR-008**: 系统 SHOULD 支持同义词/缩写匹配（扩展已有的 `GLOBAL_SYNONYMS` 机制）

### Key Entities

- **MatchRequest**: 用户输入的自然语言文本，可能包含对象名、字段名、子表描述等
- **MatchResult**: 分层结构的匹配结果集——先返回对象匹配列表，每个对象匹配下嵌套该对象的字段匹配列表，每项带有置信度评分和上下文信息
- **ObjectMatch**: 单个对象匹配项，包含对象技术名、中文标题、子表列表、匹配来源（精确/同义词/标题模糊），下挂 fieldMatches 列表
- **FieldMatch**: 嵌套在 ObjectMatch 下的字段匹配项，包含字段名、中文标题、业务类型、字段分类（金额/数量/回写/触发/虚拟/基础）

## Success Criteria

### Measurable Outcomes

- **SC-001**: 对于系统中已有中文标题的对象，自然语言精确匹配准确率达到 95% 以上
- **SC-002**: 匹配 API 响应时间在 50ms 以内（基于内存索引，无需 DB 查询）
- **SC-003**: Agent 使用匹配结果后，对象/字段识别准确率相比 AI 直接猜测显著提升
- **SC-004**: 支持至少 3 种匹配模式：精确英文名、中文标题、同义词/缩写

## Assumptions

- 元数据服务（ImpactAnalyzerService）已启动并加载了所有对象和字段信息到内存
- 已有的 `objectTitles`（对象中文标题）、`GLOBAL_SYNONYMS`（同义词映射）、`mainToDetails`（子表关系）可直接复用
- 已有的 `SkillsService.searchFields` 提供了字段名/标题/bizType 的模糊搜索能力，可作为字段匹配的基础
- 匹配 API 面向 AI Agent 程序调用，不需要前端 UI
- 中文分词/NLP 处理使用简单的关键词提取和字符串匹配，不依赖外部 NLP 服务

## Clarifications

### Session 2026-05-09

- Q: API 返回结果结构如何组织？ → A: 分层结构——先返回对象匹配列表，每个对象匹配下嵌套该对象的字段匹配列表
- Q: Agent 如何调用匹配 API？ → A: REST HTTP API，与现有 SkillsController 同级新增端点
- Q: 匹配优先级策略如何？ → A: 精确英文名(1.0) > 同义词精确(0.9) > 中文标题精确(0.8) > 中文标题包含(0.6)
