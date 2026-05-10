# Research: 增强自然语言元数据匹配

## R1: Jieba 分词在 resolve 中的集成方式

**Decision**: 在 `doResolve()` 入口处调用 `JiebaUtils.getSegmenter().process(query, SegMode.SEARCH)` 做搜索模式分词，然后从分词结果中提取对象部分和字段部分。

**Rationale**: 搜索模式（SEARCH）会对长词再做细粒度切分（如"收入确认单" → "收入确认单"+"收入"+"确认"），既保留整词又有容错。项目已有 JiebaUtils 和 3 个领域词典（825 对象标题 + 19030 字段标题），词频 99999 确保不被切散。

**Alternatives considered**:
- INDEX 模式：只保留最长词，缺少容错
- 不用 Jieba，硬切"的"分隔符：当前方案，已证明不够

## R2: 自动同义词反向索引的构建时机

**Decision**: 在 `ImpactAnalyzerService.reload()` 最后一步，从 `objectTitles` 构建 `Map<String, List<String>> titleToObjectTypes`（中文标题 → 对象名列表）。保留 `GLOBAL_SYNONYMS` 作为补充，合并两个来源。

**Rationale**: objectTitles 在 reload 中已加载，构建反向索引只需一次 O(N) 遍历。同一标题可能映射多个对象（如"合同"匹配 ArContract 和 ApContract），所以 value 是 List。

**Alternatives considered**:
- 只用 GLOBAL_SYNONYMS：覆盖率太低（5/835）
- 每次 resolve 调用时动态构建：浪费，reload 频率远低于 resolve

## R3: 级联搜索的递归策略

**Decision**: 沿两条路径递归，深度上限 2 层：
1. `mainToDetails`（子表关系）：ArContract → ArContractSubjectMatterItem
2. `referInfo.referEntityName`（引用关系）：字段 referType 指向的实体

每增加一层深度，评分乘以 0.5。使用 `Set<String> visited` 防止循环。

**Rationale**: trek 的 scope 搜索也是 3 层递归 + visited 防环。2 层已覆盖"主表 → 子表 → 子表的引用对象"场景，再深意义不大且影响性能。

**Alternatives considered**:
- 不做级联：无法匹配子表字段（当前 bug）
- 无限递归：风险高，trek 也限制了 3 层

## R4: 5 档评分算法的适配

**Decision**: 对象匹配和字段匹配共用同一套评分逻辑，内部整数（0-1000），输出归一化到 0.0~1.0。

评分规则：
- 精确匹配（name/title == query）：1000
- 后缀匹配（name/title endsWith query）：600
- 前缀匹配（name/title startsWith query）：500
- 包含匹配（name/title contains query）：400
- 模糊匹配（query contains name/title）：200

紧凑度修正：`score × (0.8 + 0.2 × queryLen / targetLen)`

**Rationale**: 直接移植 trek 的 `calculateMatchScore` 5 档逻辑。紧凑度修正确保短目标（"金额"）比长目标（"金额调整原因"）得分更高。归一化保持 API 兼容。

**Alternatives considered**:
- 保持 4 路瀑布：区分度不够（0.8 和 0.6 之间没有前缀/后缀差异）
- 输出整数：破坏 API 兼容

## R5: DB 列是否存在的验证

**Decision**: 假设 `baseapp_object_field` 表已有 `description`, `enum_type`, `is_disabled`, `is_master_field` 列。如果某列不存在，SQL 查询会报错，通过 MyBatis 的错误日志即可发现。不做动态列检测。

**Rationale**: 这些都是 MDD 框架标准字段，在 Q7Link 数据库中一定存在。DeepModel 连接的是同一个 DB。

**Alternatives considered**:
- 动态列检测（`information_schema.columns`）：过度设计
- try-catch 降级查询：增加代码复杂度，收益低
