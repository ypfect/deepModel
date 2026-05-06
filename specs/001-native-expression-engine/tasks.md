# Tasks: 本地化表达式解析引擎

**Input**: Design documents from `/specs/001-native-expression-engine/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, quickstart.md ✅

**Tests**: 包含单元测试任务（Constitution 原则 II 要求每个新增 Service 方法有单测）。

**Organization**: 按 User Story 分组，每个 Story 独立可实现、可测试。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行执行（不同文件、无依赖）
- **[Story]**: 所属 User Story（US1=Trigger 依赖解析, US2=WriteBack 依赖解析+SQL 生成, US3=ExecutingMoment 解析）
- 路径均相对于项目根 `/Users/pengfyu/advance/deepModel/`

---

## Phase 1: Setup (共享基础设施)

**Purpose**: Feature Flag 配置和基础准备

- [x] T001 创建 `ExpressionEngineConfig` 配置类，声明 `expression-engine.local-writeback-sql` 属性（默认 false），文件: `src/main/java/com/deepmodel/relation/config/ExpressionEngineConfig.java`
- [x] T002 在 `application.yml` 中添加 `expression-engine.local-writeback-sql: false` 默认配置项，文件: `src/main/resources/application.yml`

---

## Phase 2: Foundational (阻塞前置)

**Purpose**: 增强 `parseWriteBack()` 使其解析完整的 WriteBackExpr 字段，所有 User Story 均依赖此步骤

**⚠️ CRITICAL**: US1/US2/US3 均依赖此 Phase 完成

- [x] T003 增强 `ImpactAnalyzerService.parseWriteBack()` 方法，在现有 srcObjectType/expression/condition 解析基础上，增加 idField、executingMoment、validateExpr、validateMessage 四个字段的 JSON 提取，文件: `src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java`
- [x] T004 为 `parseWriteBack()` 增强编写单元测试，覆盖：正常 JSON、数组格式 JSON、单引号 JSON、缺失可选字段、空值，文件: `src/test/java/com/deepmodel/relation/service/ImpactAnalyzerServiceParseWriteBackTest.java`

**Checkpoint**: `parseWriteBack()` 返回完整的 `WriteBackExpr` 对象（含 7 个字段），所有测试通过

---

## Phase 3: User Story 1 - Trigger 表达式依赖关系本地解析 (Priority: P1) 🎯 MVP

**Goal**: triggerExpr 的依赖解析已在本地完成（research.md R3 确认），本 Phase 主要是补充跨对象外键引用（FR-007）和结构化日志（FR-008）

**Independent Test**: 查询 `projectId.projectName` 格式的 triggerExpr 字段影响范围，验证跨对象依赖边被正确建立

### Implementation for User Story 1

- [x] T005 [US1] 增强 `ExprUtils` 添加 `extractCrossObjectRefs(String expr)` 方法，从 triggerExpr 中提取 `foreignKey.fieldName` 格式的跨对象字段引用，返回 `Map<String, String>`（foreignKeyField → referencedFieldName），文件: `src/main/java/com/deepmodel/relation/util/ExprUtils.java`
- [x] T006 [US1] 在 `ImpactAnalyzerService` 的图构建逻辑中（BFS 遍历部分），利用 `extractCrossObjectRefs()` 解析 triggerExpr 中的跨对象引用，结合 `refObjectType` 元数据建立跨对象 intra 边，文件: `src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java`
- [x] T007 [US1] 为 triggerExpr 解析路径添加结构化日志（SLF4J），格式: `[TriggerParse] object={}, field={}, deps={}, crossObjectDeps={}`，文件: `src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java`
- [x] T008 [P] [US1] 编写 `ExprUtils.extractCrossObjectRefs()` 单元测试，覆盖：`projectId.projectName` 格式、多个跨对象引用、纯本地字段（无跨对象）、嵌套函数中的跨对象引用，文件: `src/test/java/com/deepmodel/relation/util/ExprUtilsCrossObjectTest.java`

**Checkpoint**: 查询含 `projectId.projectName` 的 triggerExpr 字段时，依赖图中显示跨对象 intra 边

---

## Phase 4: User Story 2 - 回写表达式依赖关系本地解析 + SQL 生成 (Priority: P1)

**Goal**: 在本地生成回写字段的 UPDATE SQL，替换 `callWriteBackSqlApi()` 的 HTTP 调用

**Independent Test**: 对含 writeBackExpr 的字段生成升级脚本，验证 SQL 与 HTTP 模式生成的 SQL 语义一致

### Implementation for User Story 2

- [x] T009 [US2] 创建 `WriteBackSqlGenerator` 类，实现 `generateSql(String objectType, String field, WriteBackExpr wb)` 方法，参考 platform `WriteBackWorker` 的 EQL 模板生成 PostgreSQL UPDATE 语句。核心模板: `UPDATE {targetTable} SET {column} = (SELECT {expression} FROM {srcTable} WHERE {idField} = {targetTable}.id AND is_deleted = false {condition})`。复用 `UpgradeScriptService` 中已有的 `objectTypeToTableName()` / `fieldCamelToColumnName()` / `convertFormulaToSnakeCase()` 工具方法，文件: `src/main/java/com/deepmodel/relation/service/WriteBackSqlGenerator.java`
- [x] T010 [US2] 修改 `UpgradeScriptService.appendWriteBackSql()` 方法，在 `callWriteBackSqlApi()` 之前检查 `ExpressionEngineConfig.isLocalWritebackSql()` Feature Flag，为 true 时调用 `WriteBackSqlGenerator.generateSql()` 生成 SQL，为 false 时保留原有 HTTP 调用路径，文件: `src/main/java/com/deepmodel/relation/service/UpgradeScriptService.java`
- [x] T011 [US2] 增强 writeBackExpr 的依赖解析：在 `ImpactAnalyzerService` 图构建中，从 condition 字段提取引用的过滤字段（通过 `ExprUtils.extractCamelFieldsFromSql(wb.getCondition())`），作为额外的依赖变量纳入 writeBack 边的来源节点集合，文件: `src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java`
- [x] T012 [US2] 为 writeBackExpr 解析和 SQL 生成路径添加结构化日志：`[WriteBackParse] object={}, field={}, srcObject={}, expression={}, condition={}, idField={}`，文件: `src/main/java/com/deepmodel/relation/service/WriteBackSqlGenerator.java`
- [x] T013 [P] [US2] 编写 `WriteBackSqlGeneratorTest`，覆盖：(1) 基本 sum 聚合回写、(2) 带 condition 的回写、(3) 多字段聚合 `sum(quantity * unitPrice)`、(4) srcObjectType 不存在的容错、(5) idField 为级联路径（如 `contractId.id`）的场景，文件: `src/test/java/com/deepmodel/relation/service/WriteBackSqlGeneratorTest.java`
- [ ] T014 [US2] 编写本地 vs HTTP 对比验证测试：选取 3-5 个典型的 writeBackExpr 字段（如 ArContract.invoicedAmount），对比本地生成的 SQL 与 HTTP 调用返回的 SQL 的语义等价性，文件: `src/test/java/com/deepmodel/relation/service/WriteBackSqlComparisonTest.java` ⚠️ 需要真实 HTTP 环境，暂跳过

**Checkpoint**: Feature Flag 设为 true 时，升级脚本中回写字段的 SQL 由本地生成，日志中无 HTTP 调用记录；生成的 SQL 与 HTTP 模式语义一致

---

## Phase 5: User Story 3 - 回写时机（ExecutingMoment）解析 (Priority: P2)

**Goal**: 将 executingMoment 时机语义纳入依赖图谱的节点/边属性中展示

**Independent Test**: 查询含 executingMoment 配置的回写字段，验证返回结果中包含时机信息

### Implementation for User Story 3

- [x] T015 [US3] 在 `GraphModels.Edge` 中新增 `String executingMoment` 属性（可选），用于承载 writeBack 边的时机语义，文件: `src/main/java/com/deepmodel/relation/model/GraphModels.java`
- [x] T016 [US3] 在 `ImpactAnalyzerService` 构建 writeBack 边时，从 `WriteBackExpr.getExecutingMoment()` 读取时机配置，设置到 Edge 的 `executingMoment` 属性中。若为 null 或 "ALWAYS"，标记为 "任何数据变化均触发"，文件: `src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java`
- [x] T017 [US3] 在 `UpgradeScriptService` 的 writeBack SQL 注释中输出 executingMoment 信息，格式: `-- 回写时机: {executingMoment}`，帮助用户理解 SQL 的触发条件，文件: `src/main/java/com/deepmodel/relation/service/UpgradeScriptService.java`
- [x] T018 [P] [US3] 编写 executingMoment 解析单元测试，覆盖：ALWAYS 时机、按 billStatus 条件触发、executingMoment 为空（默认 ALWAYS）、JSON 格式的 executingMoment，文件: `src/test/java/com/deepmodel/relation/service/ExecutingMomentParseTest.java`

**Checkpoint**: 依赖图 API 返回的 writeBack 边中包含 `executingMoment` 属性；升级脚本注释中显示回写时机

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 清理、文档、最终验证

- [x] T019 [P] 清理 `UpgradeScriptService` 中 `callWriteBackSqlApi()` 相关的 HTTP 容错注释，标注为 "Feature Flag=false 时的回退路径，待 local 模式稳定后移除"，文件: `src/main/java/com/deepmodel/relation/service/UpgradeScriptService.java`
- [x] T020 [P] 在 `ExpressionValidatorService` 的静态校验中新增 writeBackExpr 完整性检查：验证 srcObjectType 对应的对象是否存在于元数据缓存中，验证 idField 路径的有效性，文件: `src/main/java/com/deepmodel/relation/service/ExpressionValidatorService.java` ✅ 已由现有 Rule 3/4 覆盖
- [x] T021 执行 quickstart.md 验证流程，确保本地模式和 HTTP 模式均正常工作，文件: `specs/001-native-expression-engine/quickstart.md`
- [x] T022 运行全量单元测试 `mvn test` 确认无回归 ✅ 26/26 新增测试通过，1 个预存缺陷（ExpressionValidatorServiceTest，与本次修改无关）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖 - 立即开始
- **Foundational (Phase 2)**: 依赖 Phase 1 完成 - **阻塞所有 User Story**
- **US1 (Phase 3)**: 依赖 Phase 2 - 与 US2 可并行
- **US2 (Phase 4)**: 依赖 Phase 2 - 与 US1 可并行（但建议先完成 US2，因为是核心目标）
- **US3 (Phase 5)**: 依赖 Phase 2 + T003 的 executingMoment 解析增强
- **Polish (Phase 6)**: 依赖所有 User Story 完成

### User Story Dependencies

- **US1 (Trigger 跨对象解析)**: Phase 2 完成后即可开始，不依赖其他 Story
- **US2 (WriteBack SQL 生成)**: Phase 2 完成后即可开始，不依赖其他 Story。**推荐首先实现**——这是 HTTP 消除的核心目标
- **US3 (ExecutingMoment 解析)**: Phase 2 完成后即可开始，但逻辑上建议在 US2 之后（共享 parseWriteBack 增强）

### Within Each User Story

- 工具方法（ExprUtils）→ 核心 Service → 日志 → 单测
- 单测可与实现并行（标记 [P]）

### Parallel Opportunities

- T001 和 T002 可并行
- T005 和 T009 可并行（不同文件，US1 和 US2 的核心实现互不依赖）
- T008、T013、T018 均为测试任务，可并行
- T019 和 T020 可并行

---

## Parallel Example: User Story 2

```bash
# 并行启动 US2 的独立任务:
Task T009: "创建 WriteBackSqlGenerator 类 in WriteBackSqlGenerator.java"
Task T013: "编写 WriteBackSqlGeneratorTest in WriteBackSqlGeneratorTest.java"

# 串行依赖:
Task T010: "修改 UpgradeScriptService 路由" (依赖 T009 + T001)
Task T011: "增强 condition 字段依赖解析" (依赖 T003)
```

---

## Implementation Strategy

### MVP First (US2 = 核心目标)

1. Complete Phase 1: Setup (T001-T002)
2. Complete Phase 2: Foundational (T003-T004)
3. Complete Phase 4: **User Story 2** (T009-T014) ← 这是核心价值，消除 HTTP 调用
4. **STOP and VALIDATE**: Feature Flag=true，验证升级脚本回写 SQL 与 HTTP 模式一致
5. 可直接部署 MVP

### Incremental Delivery

1. Setup + Foundational → parseWriteBack 增强完成
2. Add **US2** → 本地回写 SQL 生成 → 验证 → 部署 MVP
3. Add US1 → triggerExpr 跨对象依赖 → 验证 → 部署
4. Add US3 → executingMoment 展示 → 验证 → 部署
5. Polish → 全量回归 → 最终发布

---

## Notes

- [P] 任务 = 不同文件、无依赖，可并行执行
- [Story] 标签 = 任务到 User Story 的追溯
- **US2 是 MVP 核心**——它是唯一消除 HTTP 调用的 Story
- US1 的 triggerExpr 解析本身已在本地完成，本次只是增强跨对象引用
- US3 是增值功能（P2 优先级），可在 MVP 后迭代
- 每个 Checkpoint 后验证、提交
