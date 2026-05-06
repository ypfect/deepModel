# Tasks: 元数据服务能力提取

**Input**: Design documents from `specs/002-metadata-services/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/rest-api.md

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup

**Purpose**: 新增模型类和 Mapper 扩展，为 3 个服务提供数据基础

- [x] T001 [P] 新增 `WriteBackRelationInfo` 模型类 `src/main/java/com/deepmodel/relation/model/WriteBackRelationInfo.java`
- [x] T002 [P] 新增 `CascadeWriteBackInfo` 模型类 `src/main/java/com/deepmodel/relation/model/CascadeWriteBackInfo.java`
- [x] T003 [P] 新增 `ExpressionFieldInfo` 模型类 `src/main/java/com/deepmodel/relation/model/ExpressionFieldInfo.java`
- [x] T004 [P] 新增 `EntityReferenceIndex` 模型类 `src/main/java/com/deepmodel/relation/model/EntityReferenceIndex.java`
- [x] T005 在 `BaseappObjectField` 中新增 `sourceInfo` 字段 `src/main/java/com/deepmodel/relation/model/BaseappObjectField.java`
- [x] T006 在 Mapper XML 中新增 `selectSourceInfoFields` 查询（LIST 类型字段的 source_info）`src/main/resources/mapper/BaseappObjectFieldMapper.xml`
- [x] T007 在 Mapper 接口中新增 `selectSourceInfoFields` 方法声明 `src/main/java/com/deepmodel/relation/dao/BaseappObjectFieldMapper.java`

---

## Phase 2: Foundational

**Purpose**: 在 `ImpactAnalyzerService` 中准备共享基础设施（子表关系识别、refer_info 解析工具方法）

**⚠️ CRITICAL**: US1/US2/US3 的服务都依赖这些基础数据

- [x] T008 在 `ImpactAnalyzerService.reload()` 中加载 sourceInfo 字段数据，构建 `主表→子表列表` 映射缓存 `src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java`
- [x] T009 在 `ExprUtils` 中新增 `extractVariablesFromExpression(String expr)` 方法——从 expression 中解析变量引用（区分主表字段和子表字段 `listField.xxx`），返回 `Map<String, Set<String>>`（key=KEY_MAIN 或 listFieldName）`src/main/java/com/deepmodel/relation/util/ExprUtils.java`
- [x] T010 为 `extractVariablesFromExpression` 编写单元测试，覆盖纯字段引用、子表字段 `items.qty`、无变量表达式、嵌套函数等场景 `src/test/java/com/deepmodel/relation/util/ExprUtilsExpressionVarsTest.java`

**Checkpoint**: 基础设施就绪——子表映射、变量解析工具可用

---

## Phase 3: User Story 1 - 回写触发关系图 (Priority: P1) 🎯 MVP

**Goal**: 构建 `srcObject → targetObject → targetField` 回写触发全景索引，支持字段变量查询和级联回写检测

**Independent Test**: `curl http://localhost:18080/api/metadata/writeback-relations/ArInvoiceItem` 返回完整的目标对象→字段映射

### Implementation for User Story 1

- [x] T011 [US1] 新增 `WriteBackRelationService`，实现 `buildIndex()` 方法——遍历所有含 writeBackExpr 的字段，按 `srcObjectType` 分组构建 `Map<srcObj, Map<targetObj, Set<WriteBackRelationInfo>>>` 索引 `src/main/java/com/deepmodel/relation/service/WriteBackRelationService.java`
- [x] T012 [US1] 在 `WriteBackRelationService` 中实现 `getWriteBackExprFields(String srcObjectType)` ——返回源对象触发的目标对象→字段集合 `src/main/java/com/deepmodel/relation/service/WriteBackRelationService.java`
- [x] T013 [US1] 在 `WriteBackRelationService` 中实现 `getWriteBackFieldVars(String targetObjectType)` ——返回目标对象每个被回写字段涉及的源变量集合（从 expression + condition 中提取）`src/main/java/com/deepmodel/relation/service/WriteBackRelationService.java`
- [x] T014 [US1] 在 `WriteBackRelationService` 中实现 `getCascadeWriteBackInfo(String srcObjectType)` ——检测回写目标字段是否本身也被其他对象回写，构建级联链路 `src/main/java/com/deepmodel/relation/service/WriteBackRelationService.java`
- [x] T015 [US1] 在 `ImpactAnalyzerService.reload()` 中注入 `WriteBackRelationService` 并调用 `buildIndex()` `src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java`
- [x] T016 [P] [US1] 编写 `WriteBackRelationServiceTest`，覆盖：基本索引构建、空数据容错、JSON 异常跳过、级联检测、字段变量提取（≥5 个测试）`src/test/java/com/deepmodel/relation/service/WriteBackRelationServiceTest.java`
- [x] T017 [US1] 在 `MetadataController` 中新增 `/api/metadata/writeback-relations/{objectType}`、`/api/metadata/writeback-field-vars/{objectType}`、`/api/metadata/writeback-cascade/{objectType}` 三个端点 `src/main/java/com/deepmodel/relation/controller/MetadataController.java`

**Checkpoint**: US1 完成——回写触发关系图可通过 REST API 独立查询和验证

---

## Phase 4: User Story 2 - 表达式字段依赖层级 (Priority: P1)

**Goal**: 构建对象内表达式字段的变量依赖 → 反向映射 → 层级排序，输出 `levelToFields` 视图

**Independent Test**: `curl http://localhost:18080/api/metadata/expression-fields/SalesOrder` 返回完整的层级→字段映射

### Implementation for User Story 2

- [x] T018 [US2] 新增 `ExpressionFieldService`，实现 `buildExpressionFields()` 方法——遍历对象字段，提取有 expression 的字段及其变量依赖，合并子表表达式到主表（参考 platform `buildEntityExpressionFields`）`src/main/java/com/deepmodel/relation/service/ExpressionFieldService.java`
- [x] T019 [US2] 在 `ExpressionFieldService` 中实现 `buildFieldToExprFields()` ——将"表达式字段→变量"反转为"变量→引用该变量的表达式字段"（参考 platform `buildEntityFieldToExprFields`，含子表 LIST 字段和 FK 字段路径解析）`src/main/java/com/deepmodel/relation/service/ExpressionFieldService.java`
- [x] T020 [US2] 在 `ExpressionFieldService` 中实现 `buildLevelToExprFields()` ——从反向映射出发做拓扑排序，计算每个表达式字段的计算层级（-1=变量, 0=叶子表达式, N=依赖 N-1）（参考 platform `calcLevelToExprFields`）`src/main/java/com/deepmodel/relation/service/ExpressionFieldService.java`
- [x] T021 [US2] 封装 `getExpressionFieldInfo(String objectType)` 公共查询方法，整合 exprFieldToVars + noVarExprFields + fieldToExprFields + levelToFields 四个视图 `src/main/java/com/deepmodel/relation/service/ExpressionFieldService.java`
- [x] T022 [US2] 在 `ImpactAnalyzerService.reload()` 中注入 `ExpressionFieldService` 并调用构建方法 `src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java`
- [x] T023 [P] [US2] 编写 `ExpressionFieldServiceTest`，覆盖：单级表达式、多级依赖链、子表字段合并、循环依赖容错、无表达式对象（≥5 个测试）`src/test/java/com/deepmodel/relation/service/ExpressionFieldServiceTest.java`
- [x] T024 [US2] 在 `MetadataController` 中新增 `/api/metadata/expression-fields/{objectType}` 端点 `src/main/java/com/deepmodel/relation/controller/MetadataController.java`

**Checkpoint**: US2 完成——表达式字段层级排序可通过 REST API 独立查询

---

## Phase 5: User Story 3 - 对象引用关系图 (Priority: P2)

**Goal**: 构建 `被引用对象 → 引用对象 → FK字段 → isDetail` 全量反向索引

**Independent Test**: `curl http://localhost:18080/api/metadata/refer-relations/ArContract` 返回所有引用了 ArContract 的对象及 FK 信息

### Implementation for User Story 3

- [x] T025 [US3] 新增 `EntityReferenceService`，实现 `buildIndex()` 方法——遍历所有含 referInfo 的字段，解析 `referEntities[].referEntityName` 和 `isDetail`，构建反向索引 `Map<被引用对象, Map<引用对象, Map<FK字段, Boolean>>>` `src/main/java/com/deepmodel/relation/service/EntityReferenceService.java`
- [x] T026 [US3] 在 `EntityReferenceService` 中处理多态引用——当 `referEntityFieldName` 不为空时，将引用归入 "ALL" 键 `src/main/java/com/deepmodel/relation/service/EntityReferenceService.java`
- [x] T027 [US3] 在 `ImpactAnalyzerService.reload()` 中注入 `EntityReferenceService` 并调用 `buildIndex()` `src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java`
- [x] T028 [P] [US3] 编写 `EntityReferenceServiceTest`，覆盖：普通 FK 引用、Detail 子表引用、多态引用归 ALL、referInfo 为 null 跳过、空数据容错（≥5 个测试）`src/test/java/com/deepmodel/relation/service/EntityReferenceServiceTest.java`
- [x] T029 [US3] 在 `MetadataController` 中新增 `/api/metadata/refer-relations/{objectType}` 和 `/api/metadata/refer-relations`（全量）端点 `src/main/java/com/deepmodel/relation/controller/MetadataController.java`

**Checkpoint**: US3 完成——对象引用关系可通过 REST API 独立查询

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 编译验证、全量测试、日志完善

- [x] T030 编译验证——`mvn compile` 确保零编译错误、零警告
- [x] T031 运行全量测试——`mvn test` 确保所有新增+既有测试通过
- [x] T032 [P] 为 3 个新服务的 `buildIndex()` 方法添加耗时日志（INFO 级别），格式：`"Built XXX index: {} entries in {}ms"`
- [ ] T033 [P] 更新 quickstart.md 中的验证命令，确保示例可实际运行

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: 无依赖——T001-T007 全部可并行
- **Phase 2 (Foundational)**: 依赖 Phase 1 完成（T005-T007 的 Mapper 扩展）
- **Phase 3 (US1)**: 依赖 Phase 2 完成
- **Phase 4 (US2)**: 依赖 Phase 2 完成（与 US1 可并行）
- **Phase 5 (US3)**: 依赖 Phase 2 完成（与 US1/US2 可并行）
- **Phase 6 (Polish)**: 依赖所有 US 完成

### User Story Dependencies

- **US1 (回写关系)**: Phase 2 完成即可开始，不依赖 US2/US3
- **US2 (表达式层级)**: Phase 2 完成即可开始，不依赖 US1/US3（但 T009 的变量解析工具是共享的）
- **US3 (引用关系)**: Phase 2 完成即可开始，不依赖 US1/US2

### Within Each User Story

```
模型(Phase1) → 基础设施(Phase2) → Service构建 → ImpactAnalyzer集成 → 单测 → Controller端点
```

### Parallel Opportunities

- Phase 1: T001-T004 全部可并行（4 个独立模型文件）
- Phase 2: T009-T010 可并行（ExprUtils 工具方法 + 其单测）
- US1/US2/US3: 3 个 User Story 可完全并行（独立服务、独立文件）
- 各 US 内: Service 实现与单测可并行（T016/T023/T028 标记 [P]）

---

## Parallel Example: All User Stories

```bash
# 所有 US 可在 Phase 2 完成后同时启动：
# Developer A: US1 (T011-T017)
# Developer B: US2 (T018-T024)
# Developer C: US3 (T025-T029)
```

## Parallel Example: Phase 1

```bash
# 4 个模型类完全独立，可同时创建：
Task: T001 WriteBackRelationInfo.java
Task: T002 CascadeWriteBackInfo.java
Task: T003 ExpressionFieldInfo.java
Task: T004 EntityReferenceIndex.java
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T007)
2. Complete Phase 2: Foundational (T008-T010)
3. Complete Phase 3: US1 回写关系 (T011-T017)
4. **STOP and VALIDATE**: `curl /api/metadata/writeback-relations/ArInvoiceItem`
5. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → 基础就绪
2. US1 回写关系 → 验证 → 交付 (MVP!)
3. US2 表达式层级 → 验证 → 交付
4. US3 引用关系 → 验证 → 交付
5. Polish → 最终交付

---

## Notes

- [P] tasks = 不同文件、无依赖
- 每个 US 对应一个独立 Service 文件，互不影响
- 所有 Service 通过构造器注入到 ImpactAnalyzerService
- MetadataController 是新文件，US1/US2/US3 依次在其中添加端点（需串行）
- 参考 platform 源码路径：`platform/metadata-impl/src/main/java/com/q7link/framework/metadata/service/impl/loader/SmartLoader.java`
