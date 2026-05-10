# Feature Specification: 增强自然语言元数据匹配（Resolve）

**Feature Branch**: `005-enhance-metadata-resolve`  
**Created**: 2026-05-10  
**Status**: Draft  
**Input**: 增强 DeepModel resolve 功能，覆盖 trek grep 全部匹配能力，同时增强数据源层面以支撑更精准的自然语言到对象+子表+字段的匹配。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 自然语言精准定位对象和字段 (Priority: P1)

AI Agent 在处理用户的业务元数据查询时，通过 resolve API 将中文自然语言描述精准匹配到 MDD 元数据中的对象、子表和字段，返回带置信度评分的分层匹配结果，使 AI 能够选择正确的元数据实体进行后续操作。

**Why this priority**: 这是核心功能——如果匹配不精准或范围不包含目标，所有下游场景（GQL 查询、字段分析、影响范围预估等）都无法工作。

**Independent Test**: 调用 `GET /api/skills/resolve?query=收入确认单子表的收款金额`，验证返回结果包含 `RevenueConfirmationItem.receiptAmount`（或对应正确字段），且 score 排序合理。

**Acceptance Scenarios**:

1. **Given** 系统已加载元数据缓存, **When** 用户输入"应收合同的标的子表里面的收款金额", **Then** 返回结果中包含 ArContractSubjectMatterItem 下的收款金额字段，评分排名前 3
2. **Given** 系统已加载元数据缓存, **When** 用户输入"收入确认单", **Then** 返回 RevenueConfirmation 对象匹配，score ≥ 0.8
3. **Given** 系统已加载元数据缓存, **When** 用户输入"ArContract", **Then** 返回 ArContract 对象匹配，score = 1.0（精确英文名匹配）
4. **Given** 系统已加载元数据缓存, **When** 用户输入不存在的对象名（如"火星合同"）, **Then** 返回空结果或极低评分结果

---

### User Story 2 - 子表级联字段搜索 (Priority: P1)

当用户查询涉及子表字段时，系统能沿对象的子表关系（mainToDetails）和引用关系（referType/sourceType）向下递归搜索 1-2 层，确保返回范围覆盖子表和引用对象中的匹配字段。

**Why this priority**: 当前 resolve 的子表查询会丢失字段部分，导致用户明确指定了"子表的XX字段"却无法返回字段匹配——这是已知的关键 bug。

**Independent Test**: 调用 `GET /api/skills/resolve?query=应收合同子表的原始金额`，验证返回结果中包含 ArContractSubjectMatterItem 的 originAmount 字段。

**Acceptance Scenarios**:

1. **Given** 用户查询包含"子表"关键词, **When** 输入"应收合同子表的原始金额", **Then** 系统在 ArContract 的子表（ArContractSubjectMatterItem 等）中搜索字段"原始金额"并返回匹配结果
2. **Given** 用户查询包含子表导航词, **When** 输入"应收合同的标的子表里面的收款金额", **Then** 系统识别"标的"为子表导航词，优先在标题含"标的"的子表中搜索
3. **Given** 字段存在于子表的引用对象中, **When** 按 scope 级联搜索, **Then** 引用对象中的匹配字段也被返回，但施加深度惩罚降低评分

---

### User Story 3 - 数据源质量增强 (Priority: P1)

系统在元数据加载时补充加载字段描述（description）、枚举类型（enumType）、停用标记（isDisabled）、主字段标记（isMasterField）等维度，并在 resolve 返回结果中标注停用状态，使匹配结果信息更完整，AI 可自行判断是否使用停用数据。

**Why this priority**: 数据源是匹配精度的基础。缺少 isDisabled 标注会让 AI 无法区分活跃和停用的对象/字段；缺少 description 和 enumType 会丢失 AI 理解字段语义的重要信息。

**Independent Test**: 确认 reload 后停用的对象和字段在 resolve 结果中正确标注 isDisabled=true；确认返回的 FieldMatch 包含 description 和 enumType 信息。

**Acceptance Scenarios**:

1. **Given** 某对象在 baseapp_object_type 中 is_disabled=true, **When** 用户搜索该对象名, **Then** 该对象仍出现在结果中但标注 isDisabled=true，由 AI 自行判断是否使用
2. **Given** 某字段在 baseapp_object_field 中 is_disabled=true, **When** 搜索该字段所在对象, **Then** 该字段仍出现在匹配列表中但标注 isDisabled=true
3. **Given** 一个枚举类型字段, **When** 匹配到该字段, **Then** 返回结果包含 enumType 信息
4. **Given** 系统 reload 完成, **When** 查看加载日志, **Then** 日志显示加载的停用实体和字段数量

---

### User Story 4 - 智能分词预处理 (Priority: P2)

系统使用 Jieba 分词器对用户输入进行预处理，正确切分中文业务术语（如"收入确认单"不被切成"收入"+"确认"+"单"），并利用 objectTitles 自动构建中文标题到对象名的反向索引，替代硬编码的 5 条同义词映射，覆盖全部 835+ 个对象。

**Why this priority**: 分词和同义词是匹配精度的重要提升手段，但 P1 的 bug 修复和数据源补齐优先级更高。

**Independent Test**: 输入"收入确认单子表的收款金额"，验证 Jieba 分词结果将"收入确认单"作为一个整词（而非切散），且 objectTitles 反向索引能覆盖到"收入确认单"→RevenueConfirmation 的映射。

**Acceptance Scenarios**:

1. **Given** Jieba 已加载领域词典, **When** 分词"收入确认单子表的收款金额", **Then** 结果包含"收入确认单"作为一个完整词
2. **Given** objectTitles 包含 835 个中文标题, **When** 系统构建反向索引, **Then** 能从"应收合同"直接索引到 ArContract，不再依赖硬编码同义词
3. **Given** 某对象的中文标题为"费用报销单", **When** 用户输入"费用报销单", **Then** resolve 能匹配到该对象（即使 GLOBAL_SYNONYMS 中没有该条目）

---

### User Story 5 - 精细化评分算法 (Priority: P2)

匹配评分从当前的 4 路固定分数瀑布升级为 5 档基准分 + 紧凑度修正算法（参考 trek grep 的评分策略），支持精确匹配 > 后缀匹配 > 前缀匹配 > 包含匹配 > 模糊匹配的分层评分，并对匹配长度占比进行紧凑度修正。

**Why this priority**: 评分改进能让返回结果排序更合理，但不影响基本功能。

**Independent Test**: 搜索"金额"时，"originAmount"（title="原始金额"，后缀匹配）的评分应高于"amountAdjustReason"（title="金额调整原因"，前缀匹配）。

**Acceptance Scenarios**:

1. **Given** 多个字段标题包含"金额", **When** 搜索"金额", **Then** 标题为"金额"的精确匹配 score 最高，"原始金额"次之，"金额调整原因"再次之
2. **Given** 匹配关键词较短而字段名较长, **When** 计算评分, **Then** 紧凑度修正降低长字段的得分（score × (0.8 + 0.2 × matchLen/strLen)）

---

### User Story 6 - 前端展示优化 (Priority: P2)

前端 resolve.html 的格式化展示和 JSON 原始展示都进行优化：格式化展示中 matchSource 显示中文标签、字段表格精简合并列、新增字段（description/enumType/isDisabled）的可视化展示、对象卡片加排名序号和停用标记；JSON 原始展示加语法高亮。

**Why this priority**: 纯前端改动，不影响后端逻辑，但显著提升调试和测试体验。

**Independent Test**: 打开 resolve.html，输入"应收合同的金额"，确认格式化展示中 matchSource 显示为中文（如"标题包含"而非"TITLE_CONTAINS"），JSON 展示有颜色区分。

**Acceptance Scenarios**:

1. **Given** resolve 返回结果包含 matchSource=TITLE_CONTAINS, **When** 前端渲染, **Then** 显示为中文标签"标题包含"而非枚举值
2. **Given** 字段表格原有 7 列, **When** 优化后渲染, **Then** 合并为 5 列（字段名+标题、分类、Score、描述、标签），回写/触发/枚举/停用用小标签展示
3. **Given** 某对象 isDisabled=true, **When** 前端渲染该对象卡片, **Then** 卡片显示停用标记（如红色"已停用"标签）
4. **Given** JSON 原始展示区域, **When** 展开查看, **Then** key/string/number/boolean 有不同颜色的语法高亮
5. **Given** 多个对象匹配结果, **When** 前端渲染, **Then** 每个对象卡片左上角显示排名序号（#1, #2, ...)

---

### Edge Cases

- 用户输入纯英文技术名时（如"ArContractSubjectMatterItem"），系统能精确匹配对应对象
- 用户输入混合中英文时（如"ArContract的金额"），系统能正确分割并分别匹配
- 用户输入多个"的"分隔符时（如"合同的标的的子表的金额"），系统能正确解析多层导航
- 用户输入模糊简称时（如"合同"），系统返回所有标题包含"合同"的对象（ArContract, ApContract 等），按评分排序
- 元数据缓存为空时，resolve 返回空结果而非异常
- 输入空字符串或纯标点时，返回 400 Bad Request

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统必须在 selectAll SQL 中补充加载 `description`, `enum_type`, `is_disabled`, `is_master_field` 四个字段列
- **FR-002**: 系统必须在 selectObjectTitles SQL 中补充加载 `type`, `description`, `is_disabled` 三个字段列
- **FR-003**: 系统必须在元数据加载（reload）时保留 is_disabled=true 的实体和字段，并在 resolve 返回结果中标注 isDisabled 状态，由 AI 调用方自行判断是否使用
- **FR-004**: 系统必须使用 Jieba 分词器（已有 JiebaUtils）对 resolve 输入进行中文分词预处理
- **FR-005**: 系统必须从 objectTitles 自动构建中文标题→对象名反向索引，替代硬编码的 GLOBAL_SYNONYMS
- **FR-006**: 系统必须修复子表查询时 fieldPart 丢失的 bug——子表查询后继续在子表范围内匹配字段
- **FR-007**: 系统必须支持字段级联搜索——沿 mainToDetails（子表关系）和 referInfo.referEntityName（引用关系）向下递归搜索 1-2 层
- **FR-008**: 系统必须实现 5 档评分算法（精确=1000 / 后缀=600 / 前缀=500 / 包含=400 / 模糊=200），施加紧凑度修正（score × (0.8 + 0.2 × matchLen/strLen)），输出时归一化到 0.0~1.0（除以最大分 1000）以保持 API 兼容
- **FR-009**: 系统必须为级联搜索的深层匹配施加深度惩罚（每增加一层深度，score × 0.5）
- **FR-010**: 系统必须保持现有 resolve API 的响应格式（ResolveResult → ObjectMatch → FieldMatch 分层结构）不变，仅扩展 FieldMatch 增加 description、enumType、isDisabled 字段，ObjectMatch 增加 description、type、isDisabled 字段
- **FR-011**: 前端 resolve.html 必须将 matchSource 枚举值映射为中文标签（EXACT_NAME→精确名称 / SYNONYM→同义词 / TITLE_EXACT→标题精确 / TITLE_CONTAINS→标题包含），字段表格精简为 5 列，新增字段（description/enumType/isDisabled）可视化展示，对象卡片加排名序号和停用标记
- **FR-012**: 前端 resolve.html 的 JSON 原始展示必须添加语法高亮（key/string/number/boolean 不同颜色）

### Key Entities

- **ObjectTypeMeta**: 扩展后的对象类型元信息——name、title、description、type（bill/document/setting）、isDisabled
- **BaseappObjectField**: 扩展后的字段元信息——新增 description、enumType、isDisabled、isMasterField
- **FieldMatch**: resolve 返回的字段匹配结果——扩展增加 description、enumType

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 对于 20 个预设的中文业务术语查询（涵盖直接对象、子表导航、字段搜索），resolve 返回结果的 top-3 中包含正确答案的比率 ≥ 90%
- **SC-002**: 子表字段查询（如"XX子表的YY字段"）不再返回空字段匹配——回归测试 0 个失败
- **SC-003**: 自动同义词覆盖的对象数从 5 个提升到 835+（全部有中文标题的对象）
- **SC-004**: 停用的实体和字段在 resolve 结果中正确标注 isDisabled=true——标注准确率 100%
- **SC-005**: resolve API 响应时间在现有基础上增幅不超过 50%（Jieba 分词和级联搜索的额外开销可控）
- **SC-006**: resolve.html 前端展示中所有 matchSource 显示为中文标签，无原始枚举值泄漏

## Clarifications

### Session 2026-05-10

- Q: 停用过滤的作用范围——是加载时直接过滤、加载但 resolve 排除、还是不过滤只标注？ → A: 不过滤，在返回结果中标注 isDisabled 让 AI 自行判断（选项 C）
- Q: 评分归一化策略——内部整数输出整数、归一化到 0~1、还是双字段？ → A: 内部整数计算，输出归一化到 0.0~1.0（除以 1000），保持 API 兼容（选项 A）
- Q: Jieba 词典同步策略——reload 自动生成、静态文件、还是不用 Jieba？ → A: 继续使用静态词典文件，依赖人工更新（选项 B）

## Assumptions

- Jieba 分词器和领域词典（base_object_types.txt / base_object_fields.txt）已在项目中初始化完毕（JiebaUtils.java），本次只需在 resolve 流程中调用
- Jieba 词典为静态文件，不随 reload 自动同步 DB 数据；新增自定义对象/字段需人工更新词典文件
- baseapp_object_field 表中存在 `description`, `enum_type`, `is_disabled`, `is_master_field` 列（这些是 MDD 框架标准字段，已在数据库中）
- baseapp_object_type 表中存在 `type`, `description`, `is_disabled` 列
- 级联搜索深度限制为 2 层，不做无限递归
- 现有 resolve API 的调用方（AI Agent）不依赖具体的 score 数值范围，只依赖排序——因此评分算法的调整不会破坏下游
- 保留 GLOBAL_SYNONYMS 作为后备，自动反向索引优先级更高
