# Feature Specification: 元数据服务能力提取

**Feature Branch**: `002-metadata-services`  
**Created**: 2026-05-06  
**Status**: Draft  
**Input**: 从 platform 的 MetadataService 中按需提取 3 个核心元数据能力到 DeepModel：回写触发关系图、表达式字段依赖层级、对象引用关系图。复用 DeepModel 现有的 DB 直查模式，不搬 platform 的 Entity/Field 模型体系。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 回写触发关系图全景查询 (Priority: P1)

用户（DeepModel 诊断工程师）需要快速了解某个源对象（如 ArInvoiceItem）在提交/生效时会触发哪些目标对象的哪些字段的回写，以及这些回写字段涉及哪些源对象变量（表达式字段 + 条件字段）。目前这些信息散落在各个 writeBackExpr JSON 中，需要逐个解析才能看到全貌。

**Why this priority**: 回写触发关系是升级脚本排序、影响范围预估、级联回写链路分析的核心基础数据。platform 的 `getWriteBackExprFields()` 和 `getWriteBackFieldVars()` 正是为此设计，DeepModel 当前缺少这一结构化视图。

**Independent Test**: 调用 `/api/metadata/writeback-relations/{objectType}` 查询 ArInvoiceItem，返回完整的目标对象→字段→源变量映射，可独立验证结果与 platform 的 `getWriteBackExprFields` 输出一致。

**Acceptance Scenarios**:

1. **Given** DeepModel 已加载元数据缓存，**When** 查询 ArInvoiceItem 的回写触发关系，**Then** 返回 `Map<目标对象, Set<目标字段>>` 结构，列出所有被回写的目标对象及其字段
2. **Given** ArContract 有被回写的字段（如 invoicedAmount），**When** 查询 ArContract 的回写字段变量映射，**Then** 返回 `Map<目标字段, Set<源变量>>` 结构，列出每个被回写字段涉及的源对象变量
3. **Given** 存在级联回写关系（A 回写 B，B 又触发回写 C），**When** 查询 A 的级联回写信息，**Then** 返回包含 B→C 链路的 `CascadeWriteBackInfo` 集合

---

### User Story 2 - 表达式字段依赖层级排序 (Priority: P1)

用户需要了解某个对象内所有含表达式（expression/triggerExpr）的字段的计算顺序——哪些字段依赖哪些变量字段，按什么层级顺序执行。这对排查"字段计算顺序导致的值不对"问题至关重要。

**Why this priority**: 与 US1 同等重要。platform 的 `getExpressionFields()` + `getLevelToExprFields()` + `getFieldToExprFields()` 三个方法提供了完整的表达式依赖拓扑，DeepModel 虽有 BFS 图遍历，但缺少按层级排序的结构化输出。

**Independent Test**: 调用 `/api/metadata/expression-levels/{objectType}` 查询含表达式的对象，返回层级→字段集合映射，可独立验证计算顺序的正确性。

**Acceptance Scenarios**:

1. **Given** ArContract 有多个表达式字段（如 totalAmount 依赖 qty 和 unitPrice），**When** 查询表达式字段依赖，**Then** 返回 `Map<表达式字段, Set<变量字段>>` 和无变量表达式字段集合
2. **Given** 存在多级计算链（C = A + B，D = C * E），**When** 查询层级排序，**Then** 返回 `Map<层级, Set<字段>>`，其中 level 0 的字段最先计算，level 越高越后计算
3. **Given** 查询字段的反向映射，**When** 调用 fieldToExprFields，**Then** 返回 `Map<变量字段, Set<引用该变量的表达式字段>>`

---

### User Story 3 - 对象引用关系图 (Priority: P2)

用户需要查看对象之间的引用关系全景图：哪些对象引用了某个对象、通过哪个 FK 字段引用、引用关系是否为 Detail（子表）关系。这对跨对象 triggerExpr 解析、升级脚本的对象排序、影响范围预估都很重要。

**Why this priority**: P2 因为 DeepModel 已有部分引用关系数据（`refer_info` 字段和 `selectReferencingFields` 查询），但缺少 platform 那样的 `被引用对象 → 引用对象 → FK字段 → isDetail` 结构化反向索引。

**Independent Test**: 调用 `/api/metadata/refer-relations/{objectType}` 查询 ArContract，返回所有引用了 ArContract 的对象及其 FK 字段信息。

**Acceptance Scenarios**:

1. **Given** ArInvoiceItem 有外键 contractId 引用 ArContract，**When** 查询 ArContract 的被引用关系，**Then** 返回包含 `ArInvoiceItem.contractId(isDetail=false)` 的映射
2. **Given** ContractSubjectMatterItem 是 ArContract 的子表（isDetail=true），**When** 查询 ArContract 的被引用关系，**Then** 返回的 isDetail 标记为 true
3. **Given** 多态引用场景（FK 指向 ObjectData），**When** 查询全量引用关系，**Then** 多态引用归入 "ALL" 键下

---

### Edge Cases

- 某个对象没有任何回写触发关系时，查询结果为空 Map，不报错
- writeBackExpr JSON 格式异常时，该字段跳过并记录 WARN 日志
- 表达式字段存在循环依赖时（A→B→A），层级排序标记为 -1（无法排序）并输出 WARN
- 对象不存在于元数据中时，返回空结果并记录 INFO 日志
- refer_info 为 null 或空 JSON 的字段，在引用关系构建中跳过

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 提供 `WriteBackRelationService`，从 DB 中的 writeBackExpr JSON 构建源对象→目标对象→目标字段的回写触发全景图
- **FR-002**: 系统 MUST 提供按目标对象查询回写字段变量（expression + condition 中引用的源字段）的能力
- **FR-003**: 系统 MUST 提供级联回写信息查询——当源对象的某个字段本身也是被回写字段时，构建级联链路
- **FR-004**: 系统 MUST 提供 `ExpressionFieldService`，解析每个对象内所有表达式字段的变量依赖关系
- **FR-005**: 系统 MUST 按拓扑排序生成表达式字段的计算层级（level 0 = 无依赖/纯变量，level N = 依赖 level N-1 的字段）
- **FR-006**: 系统 MUST 提供反向映射：变量字段 → 引用该变量的所有表达式字段
- **FR-007**: 系统 MUST 提供 `EntityReferenceService`，从 refer_info JSON 构建被引用对象→引用对象→FK字段→isDetail 的反向索引
- **FR-008**: 上述 3 个服务 MUST 在 `@PostConstruct` 时随 `ImpactAnalyzerService.loadCache()` 同步构建缓存
- **FR-009**: 系统 MUST 暴露 REST API（`/api/metadata/writeback-relations`、`/api/metadata/expression-levels`、`/api/metadata/refer-relations`）供前端和外部调用
- **FR-010**: 所有服务 MUST 支持 `reload()` 方法，在元数据刷新时重新构建缓存

### Key Entities

- **WriteBackRelation**: 回写触发关系——源对象触发的目标对象→目标字段集合，以及每个目标字段涉及的源变量
- **CascadeWriteBackInfo**: 级联回写信息——源对象、目标对象、目标字段、级联层级
- **ExpressionFieldDependency**: 表达式字段依赖——表达式字段→变量字段集合、层级→字段集合
- **EntityReferenceInfo**: 对象引用关系——被引用对象→引用对象→FK字段名→是否 Detail 关系

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 查询任意对象的回写触发关系，响应时间不超过 100ms（从内存缓存读取）
- **SC-002**: 表达式字段依赖层级的拓扑排序结果与 platform `getLevelToExprFields()` 的输出语义一致，对 3 个典型对象（ArContract、ApPaymentItem、SalesOrderItem）的层级结果完全匹配
- **SC-003**: 对象引用关系全景图覆盖所有含 refer_info 的字段，包括 isDetail 标记和多态引用归类
- **SC-004**: 3 个服务的缓存构建在应用启动时完成，不增加超过 2 秒的启动时间
- **SC-005**: 每个服务至少有 5 个单元测试覆盖核心逻辑和边界场景

## Assumptions

- DeepModel 继续使用 MyBatis 直查 PostgreSQL 的模式获取原始元数据，不引入 platform 的 Entity/Field 模型类
- 当前 `BaseappObjectField` 模型已包含所需的核心字段（writeBackExpr、expression、triggerExpr、referInfo），如需新增字段则通过 MyBatis Mapper 扩展
- 3 个新服务复用 `ImpactAnalyzerService` 已有的 `allRows` 和 `rowsByObject` 缓存数据，避免重复查询 DB
- 级联回写信息的构建逻辑参考 platform 的 `WriteBackExprUtils.getSourceEntityVarsAndCascadeWriteBackInfo()`，但简化为纯解析逻辑（不依赖 app-common 的反射调用）
- platform 的"自定义元数据合并"能力不在本次范围内——DeepModel 直读 DB 中已合并后的数据
- `refObjectType` 字段在 `BaseappObjectField` 模型中已存在，如未定义则需在 Mapper XML 中新增映射
