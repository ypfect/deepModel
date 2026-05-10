# Tasks: 增强自然语言元数据匹配（Resolve）

**Input**: Design documents from `/specs/005-enhance-metadata-resolve/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Constitution 要求每个新增 Service 方法附带单元测试（原则 II），因此包含测试任务。

**Organization**: 按 User Story 分组，P1 优先。US1/US2/US3 共享数据源基础（Phase 2），US4/US5 独立增量。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件，无依赖）
- **[Story]**: 所属 User Story（US1-US5）

---

## Phase 1: Setup

**Purpose**: 准备模型和 SQL 层的基础扩展

- [x] T001 [P] 在 `src/main/java/com/deepmodel/relation/model/BaseappObjectField.java` 新增 4 个字段：description, enumType, isDisabled, isMasterField（含 getter/setter）
- [x] T002 [P] 新建 `src/main/java/com/deepmodel/relation/model/ObjectTypeMeta.java`，包含 name, title, description, type, isDisabled 字段
- [x] T003 在 `src/main/resources/mapper/BaseappObjectFieldMapper.xml` 中：(1) resultMap BaseappObjectFieldMap 新增 description, enum_type, is_disabled, is_master_field 映射；(2) selectAll 补这 4 列；(3) 新建 ObjectTypeMetaMap resultMap（映射 name, title, type, description, is_disabled），将 selectObjectTitles 的 resultMap 改为 ObjectTypeMetaMap，返回类型改为 ObjectTypeMeta

---

## Phase 2: Foundational（数据源增强）

**Purpose**: 所有 User Story 依赖的数据源加载和索引构建

**⚠️ CRITICAL**: US1~US5 均依赖此阶段完成

- [x] T004 在 `src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java` 中将 `objectTitles: Map<String, String>` 替换为 `objectTypeMetas: Map<String, ObjectTypeMeta>`，修改 reload() 中 selectObjectTitles 的解析逻辑
- [x] T005 在 `src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java` 的 reload() 末尾新增 `titleToObjectTypes: Map<String, List<String>>` 反向索引构建（遍历 objectTypeMetas，按 title 分组）
- [x] T006 在 `src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java` 新增 getter 方法：getObjectTypeMetas(), getTitleToObjectTypes(), 确保其他引用 objectTitles 的代码兼容（提供 getObjectTitles() 适配方法）
- [x] T007 在 `src/main/java/com/deepmodel/relation/model/ResolveModels.java` 的 FieldMatch 内部类新增 description, enumType, isDisabled 字段；ObjectMatch 新增 description, type, isDisabled 字段

**Checkpoint**: reload() 能正确加载扩展字段和反向索引，日志输出 objectTypeMetas 数量

---

## Phase 3: User Story 1 — 自然语言精准定位对象和字段 (Priority: P1) 🎯 MVP

**Goal**: 修复 resolve 的核心匹配逻辑，使中文自然语言输入能精准匹配对象和字段

**Independent Test**: `GET /api/skills/resolve?query=应收合同` 返回 ArContract，score ≈ 1.0

### Implementation for User Story 1

- [x] T008 [US1] 在 `src/main/java/com/deepmodel/relation/service/SkillsService.java` 的 matchObjects() 方法中用 `titleToObjectTypes` 反向索引替代 GLOBAL_SYNONYMS 做同义词匹配（保留 GLOBAL_SYNONYMS 作为补充合并）
- [x] T009 [US1] 在 `src/main/java/com/deepmodel/relation/service/SkillsService.java` 中实现 `calculateMatchScore(String query, String target)` 方法：5 档评分（精确 1000 / 后缀 600 / 前缀 500 / 包含 400 / 模糊 200）+ 紧凑度修正
- [x] T010 [US1] 在 `src/main/java/com/deepmodel/relation/service/SkillsService.java` 中将 matchObjects() 和 matchFields() 的评分逻辑替换为 calculateMatchScore()，输出归一化到 0.0~1.0（除以 1000）
- [x] T011 [US1] 在 `src/main/java/com/deepmodel/relation/service/SkillsService.java` 中修改 resolve 结果组装逻辑：FieldMatch 填充 description, enumType, isDisabled；ObjectMatch 填充 description, type, isDisabled（从 objectTypeMetas 获取）

### Tests for User Story 1

- [x] T012 [US1] 新建 `src/test/java/com/deepmodel/relation/service/SkillsServiceResolveTest.java`：测试 calculateMatchScore 的 5 档评分和紧凑度修正；测试 matchObjects 精确英文名/中文标题/同义词匹配；测试空输入和纯标点输入返回空结果

**Checkpoint**: resolve 基础对象+字段匹配能力恢复正常，评分排序合理

---

## Phase 4: User Story 2 — 子表级联字段搜索 (Priority: P1)

**Goal**: 修复子表查询丢失 fieldPart 的 bug，实现沿 mainToDetails 和 referType 的级联字段搜索

**Independent Test**: `GET /api/skills/resolve?query=应收合同子表的原始金额` 返回 ArContractSubjectMatterItem 的 originAmount

### Implementation for User Story 2

- [x] T013 [US2] 在 `src/main/java/com/deepmodel/relation/service/SkillsService.java` 修复子表查询逻辑：检测到子表关键词后，正确分离 objectPart 和 fieldPart（而非混在一起），子表查询后继续用 fieldPart 在子表范围内匹配字段
- [x] T014 [US2] 在 `src/main/java/com/deepmodel/relation/service/SkillsService.java` 新增子表导航词识别：当 fieldPart 包含子表名称片段（如"标的"）时，优先在标题匹配的子表中搜索
- [x] T015 [US2] 在 `src/main/java/com/deepmodel/relation/service/SkillsService.java` 实现 `cascadeFieldSearch(String objectType, String fieldQuery, int depth, Set<String> visited)` 方法：沿 mainToDetails 递归搜索子表字段，深度上限 2 层，每层 score × 0.5
- [x] T016 [US2] 在 `src/main/java/com/deepmodel/relation/service/SkillsService.java` 扩展 cascadeFieldSearch：沿 referInfo.referEntityName 递归搜索引用对象字段，共享 visited 防环和深度上限

### Tests for User Story 2

- [x] T017 [US2] 在 `src/test/java/com/deepmodel/relation/service/SkillsServiceResolveTest.java` 新增测试：子表查询不丢 fieldPart；子表导航词"标的"定位正确子表；级联搜索深度惩罚；visited 防环

**Checkpoint**: "应收合同的标的子表里面的收款金额" 能返回 ArContractSubjectMatterItem.receiptAmount

---

## Phase 5: User Story 3 — 数据源质量增强 (Priority: P1)

**Goal**: resolve 返回结果包含 description, enumType, isDisabled 标注

**Independent Test**: 匹配到枚举类型字段时返回 enumType；匹配到停用字段时标注 isDisabled=true

### Implementation for User Story 3

- [x] T018 [US3] 验证 `src/main/resources/mapper/BaseappObjectFieldMapper.xml` 中 selectAll 新增的 4 列（T003）在实际 DB 中可查询（连接测试环境运行 reload 确认无 SQL 错误）
- [x] T019 [US3] 在 `src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java` 的 reload() 日志中新增停用实体和字段的统计输出

### Tests for User Story 3

- [x] T020 [US3] 在 `src/test/java/com/deepmodel/relation/service/SkillsServiceResolveTest.java` 新增测试：返回结果中 FieldMatch 包含 description/enumType/isDisabled；ObjectMatch 包含 description/type/isDisabled

**Checkpoint**: resolve 返回完整的元信息标注

---

## Phase 6: User Story 4 — 智能分词预处理 (Priority: P2)

**Goal**: 用 Jieba 分词替代硬切"的"分隔符，正确识别中文业务术语

**Independent Test**: "收入确认单子表的收款金额" 被 Jieba 分成 ["收入确认单", "子表", "的", "收款金额"]

### Implementation for User Story 4

- [x] T021 [US4] 在 `src/main/java/com/deepmodel/relation/service/SkillsService.java` 新增 `parseQuery(String query)` 方法：调用 JiebaUtils 分词，从分词结果中识别对象部分、子表关键词、字段部分，返回结构化的 ParsedQuery 对象
- [x] T022 [US4] 在 `src/main/java/com/deepmodel/relation/model/ResolveModels.java` 新增 ParsedQuery 内部类：objectPart, isDetailQuery, detailNavWord, fieldPart
- [x] T023 [US4] 在 `src/main/java/com/deepmodel/relation/service/SkillsService.java` 的 doResolve() 入口处，将现有的硬切"的"+"子表关键词检测"逻辑替换为 parseQuery() 调用

### Tests for User Story 4

- [x] T024 [US4] 在 `src/test/java/com/deepmodel/relation/service/SkillsServiceResolveTest.java` 新增测试：parseQuery 对多种输入格式的分词结果验证（纯中文、混合中英文、多个"的"、无分隔符）

**Checkpoint**: Jieba 分词正确识别业务术语，不再依赖硬切分隔符

---

## Phase 7: User Story 5 — 精细化评分算法 (Priority: P2)

**Goal**: 确认评分排序符合预期（精确 > 后缀 > 前缀 > 包含 > 模糊）

**Independent Test**: 搜索"金额"时排序正确

### Implementation for User Story 5

- [x] T025 [US5] 在 `src/main/java/com/deepmodel/relation/service/SkillsService.java` 的 calculateMatchScore() 中补充 isMasterField 加分逻辑（主字段 score × 1.2）
- [x] T026 [US5] 在 `src/main/java/com/deepmodel/relation/service/SkillsService.java` 的 matchFields() 中补充 description 参与匹配（作为最低优先级，分数 100）

### Tests for User Story 5

- [x] T027 [US5] 在 `src/test/java/com/deepmodel/relation/service/SkillsServiceResolveTest.java` 新增测试：isMasterField 加分；description 匹配；多个候选字段的排序验证

**Checkpoint**: 评分排序完全符合 trek grep 逻辑

---

## Phase 8: User Story 6 — 前端展示优化 (Priority: P2)

**Goal**: 优化 resolve.html 的格式化展示和 JSON 原始展示

**Independent Test**: 打开 resolve.html 搜索"应收合同的金额"，matchSource 显示中文标签，JSON 有语法高亮

### Implementation for User Story 6

- [x] T033 [P] [US6] 在 `src/main/resources/static/resolve.html` 的 JS 中新增 `matchSourceLabel(source)` 函数：EXACT_NAME→精确名称 / SYNONYM→同义词 / TITLE_EXACT→标题精确 / TITLE_CONTAINS→标题包含，替换模板中所有 `{{ om.matchSource }}` 和 `{{ fm.matchSource }}`
- [x] T034 [US6] 在 `src/main/resources/static/resolve.html` 中重构字段表格：合并字段名+标题为一列（标题灰色小字）、保留分类和 Score 列、新增描述列（显示 description）、新增标签列（用小标签展示 hasWriteBack/hasTrigger/enumType/isDisabled）；对象卡片加排名序号 `#{{ index + 1 }}` 和 isDisabled 停用标记
- [x] T035 [US6] 在 `src/main/resources/static/resolve.html` 中新增 `syntaxHighlight(json)` 函数：用正则将 key/string/number/boolean/null 包裹在不同颜色的 span 中，替换原始 JSON 展示区域的 `{{ JSON.stringify(result, null, 2) }}`
- [x] T036 [US6] 在 `src/main/resources/static/resolve.html` 的 CSS 中新增语法高亮样式（.json-key/.json-string/.json-number/.json-boolean）和停用标记样式（.disabled-badge）

**Checkpoint**: resolve.html 格式化展示和 JSON 展示均已优化，matchSource 全部中文化

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 整体质量收尾

- [x] T028 [P] 更新 `src/main/java/com/deepmodel/relation/service/SkillsService.java` 中的 Javadoc：resolve, calculateMatchScore, cascadeFieldSearch, parseQuery
- [x] T029 [P] 确保所有改动通过 `mvn compile -pl . -q` 零警告
- [x] T030 运行 `mvn test -pl .` 全量测试通过（ExpressionValidatorServiceTest 和 WriteBackSqlGeneratorTest 为 pre-existing 失败，与本次修改无关）
- [ ] T031 运行 quickstart.md 中的 3 个 curl 验证场景（需启动服务后手动验证）
- [ ] T032 在 `src/test/java/com/deepmodel/relation/service/SkillsServiceResolveTest.java` 中新增 SC-001 验证：创建 20 个预设中文业务术语查询（涵盖直接对象/子表导航/字段搜索），验证 top-3 命中率 ≥ 90%（需连接真实数据库）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: 无依赖，立即开始
- **Phase 2 (Foundational)**: 依赖 Phase 1 的 T001-T003
- **Phase 3 (US1)**: 依赖 Phase 2 的 T004-T007
- **Phase 4 (US2)**: 依赖 Phase 3（T008-T010 的评分和匹配逻辑）
- **Phase 5 (US3)**: 可与 Phase 3 并行（仅依赖 Phase 2）
- **Phase 6 (US4)**: 依赖 Phase 3（替换 resolve 入口逻辑）
- **Phase 7 (US5)**: 依赖 Phase 3（基于 calculateMatchScore 扩展）
- **Phase 8 (US6)**: 依赖 Phase 3（需要后端返回新字段），可与 Phase 4/6/7 并行
- **Phase 9 (Polish)**: 依赖全部完成

### User Story Dependencies

```
Phase 1 (Setup) → Phase 2 (Foundational)
                        ↓
                   Phase 3 (US1) ←─── Phase 5 (US3) [可并行]
                        ↓
              ┌─── Phase 4 (US2)
              ├─── Phase 6 (US4) [可并行]
              ├─── Phase 7 (US5) [可并行]
              └─── Phase 8 (US6) [可并行]
                        ↓
                   Phase 9 (Polish)
```

### Parallel Opportunities

- **Phase 1**: T001, T002, T003 全部可并行（不同文件）
- **Phase 3 vs Phase 5**: US1 和 US3 可并行（US3 只依赖 Phase 2）
- **Phase 4/6/7**: US2, US4, US5 可并行（均依赖 Phase 3 但改不同方法）
- **Phase 8**: T028, T029 可并行

---

## Parallel Example: Phase 1

```bash
# 三个任务可同时执行（不同文件，无依赖）：
Task T001: 扩展 BaseappObjectField.java
Task T002: 新建 ObjectTypeMeta.java
Task T003: 修改 BaseappObjectFieldMapper.xml
```

---

## Implementation Strategy

### MVP First (US1 Only)

1. Complete Phase 1: Setup（3 tasks）
2. Complete Phase 2: Foundational（4 tasks）
3. Complete Phase 3: User Story 1（5 tasks）
4. **STOP and VALIDATE**: 测试基础对象+字段匹配
5. 已可提供给 AI Agent 使用

### Incremental Delivery

1. Setup + Foundational → 数据源就绪
2. US1 → 基础匹配能力 → **MVP!**
3. US2 → 子表级联搜索 → 覆盖复杂查询
4. US3 → 元信息标注 → AI 获得更多上下文
5. US4 → Jieba 分词 → 中文处理更精准
6. US5 → 评分优化 → 排序更合理
7. US6 → 前端展示优化 → 调试体验提升

---

## Notes

- Constitution 要求每个新增 Service 方法附带单元测试（原则 II），已在每个 US 阶段包含测试任务
- 所有 SQL 使用参数化查询（原则 I），selectAll 只是新增 SELECT 列，无安全风险
- 单方法 ≤ 80 行（原则 I），cascadeFieldSearch 和 parseQuery 需注意控制长度
- 测试命名遵循 `方法名_场景_期望结果` 格式（原则 II）
