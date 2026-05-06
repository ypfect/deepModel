# Feature Specification: 本地化表达式解析引擎

**Feature Branch**: `001-native-expression-engine`
**Created**: 2026-05-05
**Status**: Draft
**Input**: User description: "当前 deepmodel 项目里面实现 trigger 表达式和回写表达式都是调用的其他服务的 HTTP 接口，我想把原始实现那套逻辑搬过来。原始实现在 platform 里面"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Trigger 表达式依赖关系本地解析 (Priority: P1)

当用户查询某个字段的影响范围时，系统能在本地（而非通过 HTTP 调用外部服务）解析 triggerExpr 元数据定义，准确识别字段间的触发依赖关系（哪些字段变化会触发哪些字段重算），并将结果纳入依赖图谱。

**Why this priority**: TriggerExpr 是实体内字段间依赖关系的核心来源，影响分析的准确性直接取决于 triggerExpr 的解析质量。当前通过 HTTP 调用外部服务解析，存在网络延迟、服务不可用等风险。

**Independent Test**: 查询一个配置了 triggerExpr 的字段（如 amount = quantity * unitPrice）的影响范围，验证 quantity 和 unitPrice 被正确识别为 amount 的上游依赖。

**Acceptance Scenarios**:

1. **Given** 字段 amount 的 triggerExpr 定义为 `quantity * unitPrice`，**When** 查询 quantity 的下游影响，**Then** 结果中包含 amount 字段及其触发关系
2. **Given** triggerExpr 中包含嵌套函数调用（如 `COALESCE(taxAmount, 0) + amount`），**When** 解析依赖，**Then** 正确提取 taxAmount 和 amount 两个依赖字段
3. **Given** triggerExpr 引用了外键关联字段（如 `projectId.projectName`），**When** 解析依赖，**Then** 正确识别跨对象依赖关系（当前对象.projectId → Project.projectName）

---

### User Story 2 - 回写表达式依赖关系本地解析 (Priority: P1)

当用户查询某个字段的回写来源或某个对象的回写影响范围时，系统能在本地解析 writeBackExpr 元数据定义，准确识别跨对象的回写依赖关系（哪个下游对象的哪些字段变化会导致当前字段被重算），包括回写条件、聚合表达式、时机控制等完整语义。

**Why this priority**: WriteBackExpr 定义了跨对象的聚合回写关系，是 ERP 系统中最复杂的字段依赖类型。当前解析逻辑不完整，导致依赖图谱中跨对象关系缺失或不准确。

**Independent Test**: 查询一个配置了 writeBackExpr 的字段（如 ArContract.invoicedAmount，回写源为 ArInvoiceItem），验证回写关系被正确解析。

**Acceptance Scenarios**:

1. **Given** 字段 invoicedAmount 的 writeBackExpr 定义了 srcObjectType=ArInvoiceItem, expression=sum(amount), idField=contractId，**When** 查询 invoicedAmount 的依赖来源，**Then** 结果显示来自 ArInvoiceItem.amount 的聚合回写关系
2. **Given** writeBackExpr 包含 condition 条件（如 `isDeleted=false and billStatus='approved'`），**When** 解析回写关系，**Then** 条件中引用的字段（isDeleted、billStatus）也被识别为依赖变量
3. **Given** writeBackExpr 的 expression 包含多字段聚合（如 `sum(quantity * unitPrice)`），**When** 解析依赖，**Then** quantity 和 unitPrice 均被识别为回写变量字段

---

### User Story 3 - 回写时机（ExecutingMoment）解析 (Priority: P2)

系统能在本地解析 writeBackExpr 的 executingMoment 配置，将时机语义（何种单据状态下才触发回写）纳入依赖关系图谱的展示和校验中。

**Why this priority**: ExecutingMoment 是理解回写行为的关键维度，但当前依赖分析中未体现这一语义。加入后可提升分析结果的实用性，帮助用户理解"为什么某些场景下回写没有触发"。

**Independent Test**: 查询一个配置了 executingMoment 的回写字段，验证时机信息在依赖图谱中正确展示。

**Acceptance Scenarios**:

1. **Given** 回写字段配置了 executingMoment（如仅在 billStatus=approved 时触发），**When** 查询该字段依赖关系，**Then** 返回结果中包含时机条件信息
2. **Given** executingMoment 配置为 ALWAYS，**When** 解析该字段，**Then** 标记该回写关系为"任何数据变化均触发"

---

### Edge Cases

- triggerExpr 包含非标准 SQL 语法（如 EQL 特有写法）时，应降级为正则解析并记录警告，不中断分析流程
- writeBackExpr JSON 格式异常（如单引号、缺少必填字段）时，应容错解析尽可能多的信息
- 回写链路中存在循环依赖（A → B → A）时，应在依赖图中标记为环，不无限递归
- triggerExpr 引用的字段名不存在于元数据定义中时，应在分析结果中标记为"悬空引用"

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 能在本地解析 triggerExpr 中的 SQL/EQL 表达式，提取出所有引用的字段名及其依赖方向
- **FR-002**: 系统 MUST 能在本地解析 writeBackExpr JSON 定义，提取 srcObjectType、idField、expression、condition 中的完整依赖关系
- **FR-003**: 系统 MUST 能解析 writeBackExpr 中 expression 字段的聚合语义（sum/count/max/min 等），识别参与聚合的变量字段
- **FR-004**: 系统 MUST 能解析 writeBackExpr 中 condition 字段引用的过滤字段，纳入依赖变量集合
- **FR-005**: 系统 MUST 能解析 executingMoment 配置，将时机语义作为依赖关系的元数据属性存储和展示
- **FR-006**: 系统 MUST 能识别级联回写关系（回写目标字段本身又有 writeBackExpr），在依赖图中标注级联深度
- **FR-007**: triggerExpr 解析 MUST 支持跨对象外键字段引用（如 `projectId.projectName`），正确建立跨对象依赖边
- **FR-008**: 所有解析过程 MUST 有结构化日志输出，包含对象名、字段名、解析出的依赖关系
- **FR-009**: 系统 MUST 通过 Feature Flag（配置开关）控制表达式解析的执行路径（HTTP 远程 vs 本地引擎），支持运行时切换，默认保持 HTTP 模式以确保平滑过渡

### Key Entities

- **ExpressionFieldFunctor（本地适配）**: triggerExpr 表达式的关系解析引擎，负责从表达式文本中提取字段依赖关系并注入依赖图谱
- **WriteBackWorker（本地适配）**: writeBackExpr 回写关系解析引擎，负责解析回写定义中的源对象、聚合表达式、条件、时机等语义并建立跨对象依赖边
- **WriteBackExecutingMoment**: 回写时机枚举/配置解析，将时机语义转化为依赖关系的属性标注
- **MetadataService**: 基于现有 ImpactAnalyzerService 内存缓存封装的轻量适配层，提供表达式解析引擎所需的元数据查询接口（getEntity、getField、getWriteBackExprFields 等），不引入 platform 的完整 MetadataLoader 链路

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 字段影响分析时的 triggerExpr 解析全部在本地完成，不再产生任何外部 HTTP 调用
- **SC-002**: 字段影响分析时的 writeBackExpr 关系解析全部在本地完成，不再产生任何外部 HTTP 调用
- **SC-003**: 本地解析结果与原 HTTP 调用方式的解析结果 100% 一致（通过对比测试验证）
- **SC-004**: 单对象字段影响分析（含本地 triggerExpr + writeBackExpr 解析）耗时不超过原 HTTP 方式的 50%
- **SC-005**: 级联回写关系（≤ 3 级）在依赖图中正确标注深度和路径

## Clarifications

### Session 2026-05-05

- Q: EQL 执行器如何适配？（直接搬入 platform EqlExecutor / MyBatis 原生 SQL / 轻量 EQL 子集解析器） → A: 引入轻量 EQL 子集解析器，仅支持回写/触发场景用到的 EQL 语法
- Q: MetadataService 接口形态？（搬入 platform 完整实现 / 基于 ImpactAnalyzerService 封装适配层 / 不抽接口直接依赖） → A: 基于现有 ImpactAnalyzerService 的内存缓存封装一个轻量 MetadataService 适配层，仅实现表达式引擎所需的方法
- Q: 迁移切换策略？（Feature Flag / 一次性替换 / 双写验证） → A: Feature Flag 功能开关控制，默认仍走 HTTP，通过配置切换为本地引擎，验证稳定后移除 HTTP 路径
- Q: 项目是否涉及真正的数据回写？ → A: 不涉及真正的数据回写，DeepModel 仅做关系/依赖分析，不执行实际业务数据变更

## Assumptions

- 原始实现代码（platform 中的 `ExpressionFieldFunctor` 和 `WriteBackWorker`）的**表达式解析逻辑**可作为参考，但仅需迁移关系提取部分，不需要迁移数据变更执行部分
- DeepModel 项目已有的 `FormulaParserService`（JSqlParser 表达式解析）可复用，作为 triggerExpr 解析的底层引擎
- 元数据（字段定义、表达式配置）仍从现有的 `baseapp_object_field` 表读取，不需要额外的元数据加载机制
- 引入一个轻量 EQL 子集解析器（非 platform 完整 EqlExecutor），仅支持回写和触发场景所需的 EQL 语法子集解析（提取字段引用和聚合语义），底层复用 JSqlParser
- 变更单（ChangeBill）等高级场景暂不在首版范围内，后续迭代补充
- DeepModel 不执行任何实际的数据库写入操作（INSERT/UPDATE/DELETE），所有分析结果仅用于展示依赖关系图谱
