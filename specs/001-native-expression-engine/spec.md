# Feature Specification: 本地化表达式引擎

**Feature Branch**: `001-native-expression-engine`
**Created**: 2026-05-05
**Status**: Draft
**Input**: User description: "当前 deepmodel 项目里面实现 trigger 表达式和回写表达式都是调用的其他服务的 HTTP 接口，我想把原始实现那套逻辑搬过来。原始实现在 platform 里面"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Trigger 表达式本地计算 (Priority: P1)

当用户修改某个实体的字段值时，系统能在本地（而非通过 HTTP 调用外部服务）根据 triggerExpr 元数据定义，自动计算出该字段变化所触发的其他字段的新值，并将结果持久化到数据库。

**Why this priority**: Trigger 表达式是实体保存时的核心计算链路，每次保存都会触发，是最高频的表达式计算场景。本地化后可消除网络延迟、减少外部服务依赖、提升保存操作的可靠性和性能。

**Independent Test**: 创建一个实体（如 SampleOrder），配置 triggerExpr 字段（如 amount = quantity * unitPrice），保存实体后验证 amount 字段被正确计算。

**Acceptance Scenarios**:

1. **Given** 实体 SampleOrder 有字段 quantity、unitPrice 和 amount（triggerExpr 定义为 quantity * unitPrice），**When** 用户保存一条 quantity=10, unitPrice=100 的记录，**Then** amount 被自动计算为 1000
2. **Given** 实体字段 A 的 triggerExpr 引用了字段 B 和 C，**When** 字段 B 的值发生变化，**Then** 只有引用了字段 B 的 triggerExpr 字段被重新计算，其他字段不受影响
3. **Given** 实体字段 A 的 triggerExpr 引用了外键关联对象的字段（如 projectId.projectName），**When** 保存实体时，**Then** 系统能通过查询关联对象获取所需字段值并完成计算

---

### User Story 2 - 回写表达式本地执行 (Priority: P1)

当下游单据发生数据变更（新增/修改/删除）时，系统能在本地（而非通过 HTTP 调用外部服务）根据 writeBackExpr 元数据定义，自动计算回写目标、执行聚合计算、并将结果更新到上游单据对应字段。

**Why this priority**: 回写表达式是 ERP 系统中下游单据影响上游汇总字段的核心机制（如发票金额回写到合同已开票金额），与 trigger 表达式同等重要，且逻辑更复杂（涉及跨对象聚合、级联回写、加锁防死锁等）。

**Independent Test**: 创建下游实体（如 SampleOrderItem）关联上游实体（如 SampleOrder），配置 writeBackExpr（如 SampleOrder.totalAmount = sum(SampleOrderItem.amount)），新增/修改/删除下游记录后验证上游字段被正确回写。

**Acceptance Scenarios**:

1. **Given** SampleOrder.totalAmount 配置了 writeBackExpr（srcObjectType=SampleOrderItem, expression=sum(amount)），**When** 新增一条 SampleOrderItem（amount=500），**Then** SampleOrder.totalAmount 被回写为 500
2. **Given** 已有 SampleOrderItem A（amount=500）和 B（amount=300），**When** 删除 SampleOrderItem B，**Then** SampleOrder.totalAmount 被回写为 500
3. **Given** 回写字段配置了 executingMoment 时机控制（如仅在审批通过后回写），**When** 单据处于草稿状态时修改数据，**Then** 不触发回写

---

### User Story 3 - 级联回写支持 (Priority: P2)

当回写目标字段本身又配置了 writeBackExpr（级联回写场景），系统能自动沿回写链递归执行，最多支持 3 级级联深度。

**Why this priority**: 级联回写是复杂业务场景的必要能力（如：发票行 → 合同行 → 合同表头），但相比基础的单级回写，使用频率较低。

**Independent Test**: 配置三级回写链路（C → B → A），修改 C 的数据后验证 B 和 A 都被正确更新。

**Acceptance Scenarios**:

1. **Given** 三级回写链路 C.amount → B.subTotal(=sum(C.amount)) → A.grandTotal(=sum(B.subTotal))，**When** 新增一条 C 记录（amount=100），**Then** B.subTotal 和 A.grandTotal 均被更新
2. **Given** 级联深度超过 3 级的配置，**When** 触发回写，**Then** 第 4 级及以后的回写被跳过，并记录警告日志

---

### Edge Cases

- 字段的 triggerExpr 引用了不存在的字段名时，应记录错误日志并跳过该字段计算，不影响其他字段
- writeBackExpr 的 srcObjectType 指向的对象不存在时，应记录错误日志并跳过
- 回写计算中出现 null 值参与聚合时，应遵循 SQL 语义（null 不参与 sum/count 等聚合）
- 并发场景下多个请求同时触发对同一上游记录的回写时，应通过行锁（SELECT ... FOR UPDATE）防止数据不一致
- 循环回写（A → B → A）应被检测并阻断，避免无限循环

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 支持解析 triggerExpr 元数据定义中的 SQL 表达式，并对实体数据执行本地计算
- **FR-002**: 系统 MUST 支持解析 writeBackExpr 元数据定义（包括 srcObjectType、idField、expression、condition 等字段），并执行聚合回写
- **FR-003**: 系统 MUST 支持 writeBackExpr 的 executingMoment 时机控制（ALWAYS、按单据状态触发等）
- **FR-004**: 系统 MUST 支持回写的级联传播，最大深度为 3 级
- **FR-005**: 系统 MUST 在回写执行前对目标行加锁（按 id 排序加锁），防止死锁
- **FR-006**: 系统 MUST 支持数据变化检测（比较新旧值），仅在相关变量字段发生变化时触发回写计算
- **FR-007**: triggerExpr 计算 MUST 支持跨对象外键字段引用（如 projectId.projectName）
- **FR-008**: 回写执行完成后，系统 MUST 支持 validateExpr 校验（回写后校验）
- **FR-009**: 所有计算过程 MUST 有结构化日志输出，包含对象名、字段名、计算前值、计算后值

### Key Entities

- **ExpressionFieldFunctor**: triggerExpr 表达式的计算引擎，负责解析表达式、查询关联数据、执行计算并回填字段值
- **WriteBackWorker**: writeBackExpr 回写引擎，负责计算回写目标、执行聚合查询、更新上游记录，支持级联和时机控制
- **WriteBackExecutingMoment**: 回写时机枚举/配置，控制在何种单据状态下执行回写
- **MetadataService**: 元数据服务接口，提供实体/字段定义的查询能力，是表达式引擎的数据源

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 实体保存时的 triggerExpr 计算全部在本地完成，不再产生任何外部 HTTP 调用
- **SC-002**: 下游单据变更时的 writeBackExpr 回写全部在本地完成，不再产生任何外部 HTTP 调用
- **SC-003**: 本地计算结果与原 HTTP 调用方式的计算结果 100% 一致（通过对比测试验证）
- **SC-004**: 单实体保存（含 triggerExpr 计算）耗时不超过原 HTTP 方式的 50%
- **SC-005**: 3 级级联回写场景正确执行，无数据不一致

## Assumptions

- 原始实现代码（platform 中的 `ExpressionFieldFunctor` 和 `WriteBackWorker`）可作为参考，但需要适配 DeepModel 项目的技术栈（Java 8 / Spring Boot 2.7 / MyBatis），不需要 100% 照搬
- DeepModel 项目已有的 `FormulaParserService`（JSqlParser 表达式解析）可复用，作为表达式引擎的底层解析器
- 元数据（字段定义、表达式配置）仍从现有的 `baseapp_object_field` 表读取，不需要额外的元数据加载机制
- EQL 执行器需要在 DeepModel 中实现或适配，用于执行动态生成的查询和更新语句
- 变更单（ChangeBill）等高级场景暂不在首版范围内，后续迭代补充
