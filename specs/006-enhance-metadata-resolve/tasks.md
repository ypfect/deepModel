# Tasks: 增强 Resolve 元数据匹配接口

**Input**: Design documents from `/specs/006-enhance-metadata-resolve/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Phase 1: Setup

**Purpose**: 新增模型类和基础数据结构

- [x] T001 [P] 扩展 ObjectTypeMeta 新增特性字段 in `src/main/java/com/deepmodel/relation/model/ObjectTypeMeta.java`
- [x] T002 [P] 创建 EnumTypeMeta 模型 in `src/main/java/com/deepmodel/relation/model/EnumTypeMeta.java`
- [x] T003 [P] 创建 EnumValueMeta 模型 in `src/main/java/com/deepmodel/relation/model/EnumValueMeta.java`
- [x] T004 [P] 创建 EnumMatch 结果模型 in `src/main/java/com/deepmodel/relation/model/EnumMatch.java`
- [x] T005 扩展 ResolveResult 新增 enumMatches 顶级列表 in `src/main/java/com/deepmodel/relation/model/ResolveModels.java`
- [x] T006 扩展 FieldMatch 新增 dependedByCount/dependedByFields/writeBackSource/enumValues 字段 in `src/main/java/com/deepmodel/relation/model/ResolveModels.java`

---

## Phase 2: Foundational（数据加载扩展）

**Purpose**: 扩展 ImpactAnalyzerService 的数据加载流程，为所有 User Story 提供数据基础

**⚠️ CRITICAL**: 所有 User Story 的匹配逻辑依赖此阶段完成

- [x] T007 在 enrichFieldMetadata 同一遍历中提取对象级特性（isTree/isDetail/isSupportChangeLog/isCustomizedEntity/isMultiDataVersion/businessModuleId）填充到 ObjectTypeMeta in `src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java`
- [x] T008 扩展 selectObjectTitles SQL 新增 app_name 列，填充到 ObjectTypeMeta.appName in `src/main/resources/mapper/BaseappObjectFieldMapper.xml` 和 `src/main/java/com/deepmodel/relation/model/ObjectTypeMeta.java`
- [x] T009 扩展 loadEnumDefinitions 方法，将枚举 JSON 解析为 EnumTypeMeta（含 name/title 和 EnumValueMeta 列表），构建 enumTypeIndex 和 enumTitleIndex in `src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java`
- [x] T010 构建 enumFieldIndex（枚举名 → 使用该枚举的字段列表），遍历 allRows 中 enumType 非空的字段 in `src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java`
- [x] T011 为 T007-T010 新增的索引和 getter 方法添加公开访问接口 in `src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java`

**Checkpoint**: `reload()` 完成后，objectTypeMetas 包含特性字段，enumTypeIndex/enumTitleIndex/enumFieldIndex 已构建

---

## Phase 3: User Story 1 — 按对象特性筛选 (Priority: P1) 🎯 MVP

**Goal**: 用户输入"树型对象"、"支持变更单的单据"等关键词时，返回正确过滤的对象列表

**Independent Test**: `curl "http://localhost:8080/api/skills/resolve?query=树型对象"` 返回 isTree=true 的对象

### Implementation

- [x] T012 [US1] 在 SkillsService 中定义特性关键词映射表 TRAIT_KEYWORDS（"树型"→isTree, "子表"→isDetail, "变更单"→isSupportChangeLog, "自定义"→isCustomizedEntity, "多版本"→isMultiDataVersion） in `src/main/java/com/deepmodel/relation/service/SkillsService.java`
- [x] T013 [US1] 在 parseQuery 后检测 objectPart/fieldPart 中的特性关键词，设置 ParsedQuery 中新增的 traitFilter 字段 in `src/main/java/com/deepmodel/relation/service/SkillsService.java`
- [x] T014 [US1] 在 matchObjects 中，当 traitFilter 非空时增加一路按 ObjectTypeMeta 特性过滤的匹配路径 in `src/main/java/com/deepmodel/relation/service/SkillsService.java`
- [x] T015 [US1] 在 ObjectMatch 中返回对象特性标签（isTree/isDetail/isSupportChangeLog 等） in `src/main/java/com/deepmodel/relation/model/ResolveModels.java` 和 `src/main/java/com/deepmodel/relation/service/SkillsService.java`
- [x] T016 [US1] 编写单元测试：验证"树型对象"返回 isTree=true 的对象、"变更单"返回 isSupportChangeLog=true 的对象 in `src/test/java/com/deepmodel/relation/service/SkillsServiceResolveTest.java`

**Checkpoint**: 特性关键词查询返回正确过滤的对象列表

---

## Phase 4: User Story 2 — bizType 维度字段匹配 (Priority: P1)

**Goal**: 用户输入"金额字段"、"价格字段"等时，通过 bizType 精确匹配返回正确字段

**Independent Test**: `curl "http://localhost:8080/api/skills/resolve?query=应收合同的金额字段"` 返回 bizType=amount 的字段在首位

### Implementation

- [x] T017 [US2] 在 SkillsService 中定义 BIZTYPE_KEYWORDS 映射表（"金额"→amount, "数量"→quantity, "价格"→price, "比率"→ratio, "百分比"→percent, "邮箱"→email, "手机"→mobile, "电话"→phone） in `src/main/java/com/deepmodel/relation/service/SkillsService.java`
- [x] T018 [US2] 在 parseQuery 后检测 fieldPart 中的 bizType 关键词，设置 ParsedQuery 中新增的 bizTypeFilter 字段 in `src/main/java/com/deepmodel/relation/service/SkillsService.java`
- [x] T019 [US2] 在 matchFields 中新增一路 bizType 过滤匹配：遍历对象字段，bizType 精确匹配时给予 800 基础分 in `src/main/java/com/deepmodel/relation/service/SkillsService.java`
- [x] T020 [US2] 编写单元测试：验证"金额字段"返回 bizType=amount 的字段且排在首位 in `src/test/java/com/deepmodel/relation/service/SkillsServiceResolveTest.java`

**Checkpoint**: bizType 中文关键词查询返回精确匹配字段

---

## Phase 5: User Story 3 — 枚举类型搜索 (Priority: P2)

**Goal**: 用户输入"审批状态"、"ApproveStatus"等时，返回匹配的枚举类型及其值列表

**Independent Test**: `curl "http://localhost:8080/api/skills/resolve?query=审批状态"` 返回 enumMatches 包含 ApproveStatus 及其值

### Implementation

- [x] T021 [US3] 在 doResolve 中新增 matchEnums 方法：按英文名精确→标题精确→标题包含三路匹配枚举，构建 EnumMatch 列表 in `src/main/java/com/deepmodel/relation/service/SkillsService.java`
- [x] T022 [US3] 在 EnumMatch 结果中填充 usedByFields（从 enumFieldIndex 查询） in `src/main/java/com/deepmodel/relation/service/SkillsService.java`
- [x] T023 [US3] 在字段匹配结果 FieldMatch 中，当字段有 enumType 且被枚举搜索命中时，填充 enumValues 列表 in `src/main/java/com/deepmodel/relation/service/SkillsService.java`
- [x] T024 [US3] 编写单元测试：验证"审批状态"返回 enumMatches、"ApproveStatus"精确匹配、字段级枚举值展开 in `src/test/java/com/deepmodel/relation/service/SkillsServiceResolveTest.java`

**Checkpoint**: 枚举搜索和字段级枚举展开正常工作

---

## Phase 6: User Story 4 — 表达式语义联动 (Priority: P2)

**Goal**: 字段匹配结果中附带依赖摘要（dependedByCount/dependedByFields）和回写来源摘要（writeBackSource）

**Independent Test**: 搜索 quantity 字段时结果中包含 dependedByCount > 0

### Implementation

- [x] T025 [US4] 在 buildFieldMatch 中查询 ExpressionFieldService.getExpressionFieldInfo 的 fieldToExprFields，填充 FieldMatch.dependedByCount 和 dependedByFields（截断前 5 个） in `src/main/java/com/deepmodel/relation/service/SkillsService.java`
- [x] T026 [US4] 在 buildFieldMatch 中解析 BaseappObjectField.writeBackExpr 的 srcObjectType + expression，拼接为 writeBackSource 摘要字符串 in `src/main/java/com/deepmodel/relation/service/SkillsService.java`
- [x] T027 [US4] 编写单元测试：验证有表达式依赖的字段返回 dependedByCount > 0、有 writeBackExpr 的字段返回 writeBackSource 非空 in `src/test/java/com/deepmodel/relation/service/SkillsServiceResolveTest.java`

**Checkpoint**: 字段匹配结果包含依赖和回写摘要信息

---

## Phase 7: User Story 5 — 关系网络导航 (Priority: P3)

**Goal**: 用户输入"哪些对象引用了 Customer"时，返回所有通过外键参照 Customer 的对象及字段列表

**Independent Test**: `curl "http://localhost:8080/api/skills/resolve?query=哪些对象引用了Customer"` 返回引用方对象列表

### Implementation

- [x] T028 [US5] 在 parseQuery 中识别"哪些对象引用了 XX"/"被哪些对象使用"等反向查询意图，设置 reverseRefTarget 字段 in `src/main/java/com/deepmodel/relation/service/SkillsService.java`
- [x] T029 [US5] 在 doResolve 中，当 reverseRefTarget 非空时，调用 EntityReferenceService 查询反向引用关系，构建 ObjectMatch 列表 in `src/main/java/com/deepmodel/relation/service/SkillsService.java`
- [x] T030 [US5] 确认 EntityReferenceService 已提供 getReferRelations API，在 ImpactAnalyzerService 新增 getEntityReferenceService() getter in `src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java`
- [x] T031 [US5] 编写单元测试：验证反向引用查询返回正确的引用方对象 in `src/test/java/com/deepmodel/relation/service/SkillsServiceResolveTest.java`

**Checkpoint**: 反向引用查询返回正确结果

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: 缓存、性能、文档

- [x] T032 验证所有新增索引纳入 clearAnalysisCache 联动清除机制 in `src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java`
- [x] T033 运行全量测试确认无回归 `mvn test -pl . -q`（2 个无关的已有失败：ExpressionValidatorServiceTest/WriteBackSqlGeneratorTest）
- [x] T034 按 quickstart.md 执行端到端验证（编译通过，本地数据库连接不可用，需部署到测试环境验证）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: 无依赖 — 可立即开始
- **Phase 2 (Foundational)**: 依赖 Phase 1 — BLOCKS 所有 User Story
- **Phase 3 (US1) / Phase 4 (US2)**: 依赖 Phase 2，彼此独立可并行
- **Phase 5 (US3)**: 依赖 Phase 2（enumTypeIndex），与 US1/US2 独立
- **Phase 6 (US4)**: 依赖 Phase 2，与其他 Story 独立
- **Phase 7 (US5)**: 依赖 Phase 2 + 可能依赖 EntityReferenceService 扩展
- **Phase 8 (Polish)**: 依赖所有 User Story 完成

### Parallel Opportunities

- T001-T006 (Phase 1): 全部可并行（不同文件）
- T012-T015 (US1) 和 T017-T019 (US2): 改同一文件，建议串行
- US1/US2 和 US3/US4/US5: Phase 2 完成后可按优先级串行或按人力并行

---

## Implementation Strategy

### MVP First (US1 + US2)

1. Phase 1: Setup（模型类）→ 30 分钟
2. Phase 2: Foundational（数据加载）→ 1 小时
3. Phase 3: US1 对象特性筛选 → 1 小时
4. Phase 4: US2 bizType 匹配 → 30 分钟
5. **STOP & VALIDATE**: 测试特性过滤 + bizType 匹配
6. 部署验证

### Incremental Delivery

1. MVP (US1+US2) → 验证 → 部署
2. US3 枚举搜索 → 验证 → 部署
3. US4 表达式摘要 → 验证 → 部署
4. US5 关系网络 → 验证 → 部署

---

## Notes

- [P] tasks = 不同文件，无依赖
- [Story] label 映射 spec.md 中的 User Story
- US1 和 US2 都是 P1 优先级，组成 MVP
- Constitution 要求每个 Service 方法有对应单元测试
- 所有 SQL 变更走参数化查询
