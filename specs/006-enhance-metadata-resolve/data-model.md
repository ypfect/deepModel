# Data Model: 增强 Resolve 元数据匹配接口

## 扩展的实体模型

### ObjectTypeMeta（扩展）

现有字段保留，新增从 content JSON 提取的特性字段：

| 字段 | 类型 | 来源 | 说明 |
|------|------|------|------|
| name | String | baseapp_object_type.name | 已有 |
| title | String | baseapp_object_type.title | 已有 |
| description | String | baseapp_object_type.description | 已有 |
| type | String | baseapp_object_type.type | 已有（bill/document/setting） |
| isDisabled | Boolean | baseapp_object_type.is_disabled | 已有 |
| isTree | Boolean | content JSON → isTree | **新增** |
| isDetail | Boolean | content JSON → isDetail | **新增** |
| isSupportChangeLog | Boolean | content JSON → isSupportChangeBill | **新增** |
| isCustomizedEntity | Boolean | content JSON → isCustomizedEntity | **新增** |
| isMultiDataVersion | Boolean | content JSON → isMultiDataVersion | **新增** |
| businessModuleId | String | content JSON → businessModuleId | **新增** |
| appName | String | baseapp_object_type.app_name | **新增**（selectObjectTitles SQL 扩展） |

### EnumTypeMeta（新增）

| 字段 | 类型 | 来源 | 说明 |
|------|------|------|------|
| name | String | content JSON → name | 枚举类型名 |
| title | String | content JSON → title | 中文标题 |
| description | String | content JSON → description | 描述 |
| values | List\<EnumValueMeta\> | content JSON → enumValueDefs[] | 枚举值列表 |

### EnumValueMeta（新增）

| 字段 | 类型 | 来源 | 说明 |
|------|------|------|------|
| value | String | enumValueDefs[].value | 枚举值 |
| title | String | enumValueDefs[].title | 中文标题 |
| ordinal | Integer | enumValueDefs[].ordinal | 排序序号 |
| isDisabled | Boolean | enumValueDefs[].isDisabled | 是否停用 |

### EnumMatch（新增）

ResolveResult 中 enumMatches 列表的元素：

| 字段 | 类型 | 说明 |
|------|------|------|
| enumType | String | 枚举类型名 |
| title | String | 中文标题 |
| description | String | 描述 |
| score | double | 匹配评分 |
| matchSource | MatchSource | 匹配来源（EXACT_NAME/TITLE_EXACT/TITLE_CONTAINS） |
| values | List\<EnumValueMeta\> | 枚举值列表 |
| usedByFields | List\<String\> | 使用该枚举的字段列表（objectType.fieldName 格式） |

### FieldMatch（扩展）

在现有字段基础上新增：

| 字段 | 类型 | 说明 |
|------|------|------|
| dependedByCount | Integer | 被多少个表达式字段依赖（null 表示未计算） |
| dependedByFields | List\<String\> | 依赖该字段的表达式字段名列表（最多 5 个） |
| writeBackSource | String | 回写来源摘要（如 "RevenueConfirmationItem.sum(amount)"），无回写时为 null |
| enumValues | List\<EnumValueMeta\> | 字段关联的枚举值列表，无枚举时为 null |

### ResolveResult（扩展）

| 字段 | 类型 | 说明 |
|------|------|------|
| query | String | 已有 |
| objectMatches | List\<ObjectMatch\> | 已有 |
| enumMatches | List\<EnumMatch\> | **新增**，枚举匹配结果，与 objectMatches 平级 |

## 索引结构（内存）

### 新增索引

| 索引名 | 类型 | 说明 |
|--------|------|------|
| enumTypeIndex | Map\<String, EnumTypeMeta\> | 枚举名 → 枚举元信息 |
| enumTitleIndex | Map\<String, List\<String\>\> | 枚举中文标题 → 枚举名列表 |
| enumFieldIndex | Map\<String, List\<String\>\> | 枚举名 → 使用该枚举的字段列表 |
| bizTypeKeywordMap | Map\<String, String\> | 中文关键词 → bizType 标准值 |
| traitKeywordMap | Map\<String, TraitFilter\> | 特性关键词 → 过滤条件 |

### 特性关键词映射表

| 中文关键词 | 目标特性 | 过滤条件 |
|-----------|---------|---------|
| 树型/树状/树形 | isTree | isTree=true |
| 子表/明细 | isDetail | isDetail=true |
| 变更单 | isSupportChangeLog | isSupportChangeLog=true |
| 自定义 | isCustomizedEntity | isCustomizedEntity=true |
| 多版本 | isMultiDataVersion | isMultiDataVersion=true |

### bizType 关键词映射表

| 中文关键词 | bizType 值 |
|-----------|-----------|
| 金额 | amount |
| 数量 | quantity |
| 价格 | price |
| 比率 | ratio |
| 百分比 | percent |
| 邮箱 | email |
| 手机 | mobile |
| 电话 | phone |
