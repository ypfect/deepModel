# Research: Agent 自然语言元数据匹配

## 现有数据源分析

### Decision: 复用 ImpactAnalyzerService 已有内存索引

**Rationale**: ImpactAnalyzerService 在启动时已加载以下数据到内存，完全满足匹配需求：

| 数据源 | 类型 | 用途 |
|--------|------|------|
| `objectTitles` | Map<objectType, 中文标题> | 中文标题 → 对象名匹配 |
| `GLOBAL_SYNONYMS` | Map<objectType, List<同义词>> | 同义词/缩写匹配 |
| `mainToDetails` | Map<主表, Set<子表>> | "XX的子表"语义解析 |
| `allRows` / `rowsByObject` | 字段全量数据 | 字段名/标题搜索 |
| `getObjectDetails()` | 对象列表+标题 | 对象枚举和排序 |

**Alternatives considered**: 
- 引入 Elasticsearch 全文搜索 — 过重，不需要
- 引入中文分词库（如 HanLP/jieba）— 增加依赖，简单字符串匹配足够

## 匹配算法设计

### Decision: 多路匹配 + 瀑布式评分

**Rationale**: 按置信度从高到低依次尝试，首次命中高置信度即可提前返回：

1. **精确英文名匹配**（score=1.0）：输入文本直接匹配 objectType（PascalCase）或字段 apiName（camelCase）
2. **同义词精确匹配**（score=0.9）：输入文本匹配 GLOBAL_SYNONYMS 中的某个同义词
3. **中文标题精确匹配**（score=0.8）：输入文本与 objectTitles 中的标题完全相等
4. **中文标题包含匹配**（score=0.6）：输入文本包含在标题中，或标题包含在输入文本中

**Alternatives considered**:
- 向量相似度搜索 — 需要 embedding 模型，过于复杂
- 编辑距离匹配 — 对中文效果差，暂不引入

## 子表语义解析

### Decision: 通过关键词检测 + mainToDetails 映射

**Rationale**: 当输入文本中包含"子表"、"明细"、"行项目"等关键词时，提取前置的对象名，通过 `mainToDetails` 返回子表列表。简单有效，不需要复杂 NLP。

## 同义词覆盖范围

### Decision: 第一期复用现有 GLOBAL_SYNONYMS，后续可扩展

**Rationale**: 现有 GLOBAL_SYNONYMS 仅覆盖少量对象（User、Org、ArContract、Project、Customer）。第一期先复用，同时将 `objectTitles`（所有对象的中文标题）作为最大的"同义词库"使用。后续可通过 API 动态扩展同义词。
