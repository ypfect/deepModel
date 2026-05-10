# Data Model: 增强自然语言元数据匹配

## 实体变更

### BaseappObjectField（扩展）

| 字段 | 类型 | 来源 | 说明 |
|------|------|------|------|
| description | String | f.description | 字段描述文本 |
| enumType | String | f.enum_type | 枚举类型名（如 ApproveStatus） |
| isDisabled | Boolean | f.is_disabled | 是否停用 |
| isMasterField | Boolean | f.is_master_field | 是否主字段（级联搜索加分用） |

### ObjectTypeMeta（新增）

| 字段 | 类型 | 来源 | 说明 |
|------|------|------|------|
| name | String | baseapp_object_type.name | 对象类型名（如 ArContract） |
| title | String | baseapp_object_type.title | 中文标题（如 应收合同） |
| description | String | baseapp_object_type.description | 对象描述 |
| type | String | baseapp_object_type.type | 类型（bill/document/setting） |
| isDisabled | Boolean | baseapp_object_type.is_disabled | 是否停用 |

### FieldMatch（扩展 ResolveModels 内部类）

| 字段 | 类型 | 新增/修改 | 说明 |
|------|------|----------|------|
| description | String | 新增 | 字段描述 |
| enumType | String | 新增 | 枚举类型名 |
| isDisabled | Boolean | 新增 | 是否停用 |

### ObjectMatch（扩展 ResolveModels 内部类）

| 字段 | 类型 | 新增/修改 | 说明 |
|------|------|----------|------|
| description | String | 新增 | 对象描述 |
| type | String | 新增 | 对象类型（bill/document/setting） |
| isDisabled | Boolean | 新增 | 是否停用 |

## 索引变更

### ImpactAnalyzerService 新增内存索引

| 索引 | 类型 | 构建时机 | 用途 |
|------|------|---------|------|
| objectTypeMetas | `Map<String, ObjectTypeMeta>` | reload() | 替代 objectTitles，包含完整对象元信息 |
| titleToObjectTypes | `Map<String, List<String>>` | reload() 末尾 | 中文标题 → 对象名反向索引（自动同义词） |

### SQL 变更

**selectAll** 新增列：`f.description, f.enum_type, f.is_disabled, f.is_master_field`

**selectObjectTitles** 改为：`select name, title, type, description, is_disabled from baseapp_object_type`

## 关系图

```mermaid
graph TD
    A[用户输入] --> B[Jieba 分词]
    B --> C{结构识别}
    C --> D[对象匹配<br/>titleToObjectTypes + objectTypeMetas]
    C --> E[子表关键词检测]
    C --> F[字段部分提取]
    D --> G[ObjectMatch 列表]
    E --> H[mainToDetails 查子表]
    H --> I[子表 ObjectMatch]
    F --> J[字段匹配<br/>5档评分+紧凑度]
    G --> J
    I --> J
    J --> K[级联搜索<br/>referType 递归 1-2 层]
    K --> L[FieldMatch 列表<br/>深度惩罚 ×0.5]
    L --> M[归一化 0~1.0]
    M --> N[ResolveResult]
```
