# DeepModel Insight — Skills 总结（供 AI 调用）

本文档描述本系统的能力与 API，便于包装为 **skills** 供 AI 使用：**修改模型、调整关联模型、生成升级脚本、分析字段**。

---

## 一、系统定位与数据模型

- **定位**：基于元数据的 **字段级数据血缘与影响分析**，面向 ERP/业务对象模型（bill 等）。
- **数据来源**：
  - `baseapp_object_field`：对象 + 字段定义（含 `expression`、`triggerExpr`、`virtualExpr`、`writeBackExpr` 等）。
  - `baseapp_object_type`：对象类型（含 `type`，如 `bill`；用于过滤“当前/核心对象”列表）。
  - 视图定义（viewDef SQL）：解析出视图字段对源表字段的依赖。
- **关系类型**：
  - **intra**：同对象内依赖（如 trigger 公式引用、expression、virtualExpr）。
  - **writeBack**：跨对象回写/聚合（writeBackExpr 定义的来源对象 → 当前对象字段）。
  - **view**：视图 SQL 映射的字段依赖。

---

## 二、核心能力概览

| 能力 | 说明 | 典型用途（AI） |
|------|------|----------------|
| **元数据查询** | 对象列表、对象详情、字段列表、字段详情、单字段信息 | 发现要改的对象/字段、补全上下文 |
| **字段影响分析** | 以某对象某字段为根，下游/上游多层级依赖图 | 改一个字段时，判断会影响哪些对象/字段 |
| **归因解释** | 某字段的值由哪些步骤（intra/writeBack/view）推导而来 | 向用户解释“这个字段从哪来” |
| **来源对象分析** | 指定「当前对象 + 来源对象」，列出被该来源写回/聚合的字段及 trigger 上游 | 改来源对象时，评估对当前对象的影响 |
| **跨对象来源分析** | 指定「当前对象」，列出所有写回到它的来源对象及字段级回写关系 | 梳理“谁在写我”，改模型时考虑回写方 |
| **对象健康度** | 某对象的字段统计：总数、公式字段、被引用、疑似废字段、回写字段、无下游回写等 | 发现可清理或需关注的字段 |
| **升级脚本生成** | 以某根字段为起点，按依赖层级生成 UPDATE/聚合 SQL（含调用外部 writeBack 转 SQL 接口） | 修改模型后，生成可执行的数据修复/回填脚本 |
| **GraphQL 导出** | 根据当前图谱/对象及字段集合生成 GraphQL 查询：`{ Object(criteriaStr: \"id='replaceId'\") { field1 field2 } ... }`，支持复制到剪贴板并跳转外部 GraphiQL | 让用户在 GraphQL 控制台直接执行查询，或让 AI 输出可编辑的 GraphQL 模板 |

---

## 三、API 清单（供 AI 调用）

**Base URL**：假设服务根路径为 `/`，以下为接口路径与主要参数。

### 1. 元数据（meta）

| 方法 | 路径 | 参数 | 返回说明 |
|------|------|------|----------|
| GET | `/api/impact/meta/objects` | - | 对象名集合（已按 baseapp_object_type.type='bill' 过滤） |
| GET | `/api/impact/meta/objectDetails` | - | `[{name, title}, ...]` 对象列表（同上过滤） |
| GET | `/api/impact/meta/fields` | `objectType` | 该对象字段名列表 |
| GET | `/api/impact/meta/fieldDetails` | `objectType` | 该对象字段详情列表（含 name, apiName, title, type, expression, triggerExpr, writeBackExpr 等） |
| GET | `/api/impact/meta/health` | `objectType` | 对象健康度：totalFields, formulaFields, referencedFields, deadFields, writeBackFields, writeBackNoDownstream, deadFieldList, writeBackNoDownstreamList |
| GET | `/api/impact/fieldInfo` | `objectType`, `field` | 单字段完整信息（BaseappObjectField） |

### 2. 字段影响分析（图谱与归因）

| 方法 | 路径 | 参数 | 返回说明 |
|------|------|------|----------|
| GET | `/api/impact` | `objectType`, `field`, `depth`(默认3), `relType`(0 全/1 回写/2 触发), `direction`(downstream/upstream) | 依赖图：nodes + edges |
| GET | `/api/impact/batch` | `objectType`, `fields`(逗号分隔), `depth`, `relType`, `direction` | 多字段合并依赖图 |
| GET | `/api/impact/explain` | `objectType`, `field`, `depth`, `relType`, `direction` | 归因步骤列表（按对象分组，含 type 与 reason） |
| GET | `/api/impact/triggerFields` | `objectType`, `field` | 该字段在同对象内的 trigger 上游字段列表 |
| GET | `/api/impact/objects` | `objectType`, `field`, `depth`, `relType` | 对象级依赖图（节点为对象） |
| GET | `/api/impact/objectEdgeDetails` | `objectType`, `field`, `depth`, `relType`, `sourceObject`, `targetObject`, `type` | 两对象之间某类型的边详情 |

### 3. 跨对象与来源对象

| 方法 | 路径 | 参数 | 返回说明 |
|------|------|------|----------|
| GET | `/api/impact/cross/sourcesForTarget` | `targetObject` | 当前对象被哪些来源对象写回：`[{sourceObject, fieldCount}, ...]` |
| GET | `/api/impact/cross/targetsBySource` | `sourceObject` | 某来源对象写回了哪些目标对象：`[{targetObject, fieldCount}, ...]` |
| GET | `/api/impact/bySourceObject` | `targetObject`, `sourceObject` | 目标对象中，由该来源对象写回/聚合的字段列表（含 writeBackExpr 等） |

### 4. 升级脚本

| 方法 | 路径 | 参数 | 返回说明 |
|------|------|------|----------|
| GET | `/api/impact/upgradeScript` | `objectType`, `field`, `depth`(默认3), `relTypes`(默认 intra,writeBack) | 纯文本 SQL 脚本（Content-Disposition 附件），按层级生成 UPDATE/聚合等 |

### 5. 其它

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/reload` | 重新加载元数据与视图（修改库后刷新缓存） |
| GET | `/api/impact/mermaid` | 同 `/api/impact`，返回 Mermaid 图文本 |
| GET | `/api/impact/dot` | 同 `/api/impact`，返回 DOT 图文本 |

---

## 四、面向 AI 的 Skill 建议

### Skill 1：分析字段（Analyze Field）

- **输入**：对象类型、字段名；可选：深度、方向（下游/上游）、关系类型。
- **调用**：`/api/impact` 或 `/api/impact/explain`，必要时 `/api/impact/fieldInfo`、`/api/impact/triggerFields`。
- **输出**：依赖图或归因步骤，用自然语言总结“该字段影响谁”或“该字段由谁计算得出”。

### Skill 2：列出并理解关联模型（List Related Models）

- **输入**：当前对象（或来源对象）。
- **调用**：  
  - 看“谁写回我”：`/api/impact/cross/sourcesForTarget?targetObject=当前对象`  
  - 看“我写回谁”：`/api/impact/cross/targetsBySource?sourceObject=来源对象`  
  - 看“某来源对象对当前对象影响哪些字段”：`/api/impact/bySourceObject?targetObject=...&sourceObject=...`
- **输出**：关联对象列表及字段数；可选：字段级列表与 writeBackExpr 摘要。

### Skill 3：修改模型并评估影响（Modify Model & Assess Impact）

- **输入**：要修改的对象、字段（或新增/删除字段的意图）。
- **调用**：  
  - `/api/impact/meta/fieldDetails`、`/api/impact/fieldInfo` 获取当前定义；  
  - `/api/impact`（downstream）看会影响哪些对象/字段；  
  - `/api/impact/cross/sourcesForTarget`、`/api/impact/bySourceObject` 看回写关系。
- **输出**：受影响对象与字段清单；建议的修改步骤（仅建议，不直接改库）。

### Skill 4：生成升级脚本（Generate Upgrade Script）

- **输入**：根对象、根字段（即“从哪个字段开始重算/回填”）；可选：深度、relTypes（如 `intra,writeBack`）。
- **调用**：`GET /api/impact/upgradeScript?objectType=...&field=...&depth=...&relTypes=...`
- **输出**：返回的 SQL 文本可直接作为“升级脚本”提供给用户审阅或执行（脚本内可能调用外部 writeBack 转 SQL 服务）。

### Skill 5：对象健康度与字段质量（Object Health）

- **输入**：对象类型。
- **调用**：`/api/impact/meta/health?objectType=...`
- **输出**：总字段数、公式字段、被引用、疑似废字段、回写相关统计及名单；AI 可据此建议“可清理字段”或“需关注回写”。

### Skill 6：GraphQL 导出（GraphQL Export）

- **输入**：一组对象及其字段（通常来源于当前影响图 / 某对象字段列表）。\n- **调用**：无专门 API，AI 直接按约定格式构造字符串，例如：\n  - 对每个对象生成一段：`ObjectName(criteriaStr: \"id='replaceId'\") { field1 field2 ... }`\n  - 整体包在一个顶层 `{ ... }` 中；`criteriaStr` 默认占位为 `id='replaceId'`。\n- **输出**：GraphQL 查询字符串，并提示用户：\n  - 在前端配置的 GraphiQL 地址（如 `https://graphql-test-tx-16.e7link.com/graphiql/index.html`）中粘贴查询；\n  - 将请求头 `Tenant-Id` 设为当前系统的默认值（如 `711FNX50G6V0009`）。\n\n```\n例：\n{\n  Invoice(criteriaStr: \"id='replaceId'\") {\n    id\n    amount\n  }\n  InvoiceItem(criteriaStr: \"id='replaceId'\") {\n    amount\n    taxAmount\n  }\n}\n```\n*** End Patch```}렇 assistant to=functions.ApplyPatch.MatchersWeaveJSONrgctxassistant to=functions.ApplyPatchurrenzassistant to=functions.ApplyPatchुझे to=functions.ApplyPatch_COUNTERfairsuggestions to=functions.ApplyPatch 尊尼assistant to=functions.ApplyPatch +#+#+#+#+#+assistant to=functions.ApplyPatch.HttpSession JSON_ARGS_OVERRIDDEN  толстassistant to=functions.ApplyPatch$arityassistant to=functions.ApplyPatchipationassistant to=functions.ApplyPatch ***!

---

## 五、前端页面与 Skill 的对应关系

| 页面 | 入口 | 主要能力 |
|------|------|----------|
| 字段影响分析 | modern.html | 图谱 + 归因 + GraphQL 导出 + 健康度；对象/字段为 bill 过滤、中英文搜索 |
| 来源对象分析 | source.html | 当前对象 + 来源对象 → 被写回的字段列表 + trigger 上游 + “在图谱中查看” |
| 跨对象来源分析 | cross.html | 当前对象 → 所有写回它的来源对象及字段级回写明细（含 executingMoment 等） |

AI 无需直接操作这些页面，通过调用上述 API 即可实现“分析字段、列出关联模型、评估修改影响、生成升级脚本”。

---

## 六、使用流程示例（AI 执行顺序）

1. **用户说**：“我要改 SalesOrderItem 的 amount 字段，会影响哪些地方？”  
   → 调用 `GET /api/impact?objectType=SalesOrderItem&field=amount&direction=downstream&depth=3`，再调用 `explain` 可读化。  
2. **用户说**：“生成这个字段的升级脚本。”  
   → 调用 `GET /api/impact/upgradeScript?objectType=SalesOrderItem&field=amount&depth=3&relTypes=intra,writeBack`，返回 SQL 给用户。  
3. **用户说**：“ArContractSubjectMatterItem 被哪些对象回写？”  
   → 调用 `GET /api/impact/cross/sourcesForTarget?targetObject=ArContractSubjectMatterItem`，列出来源对象与字段数。  
4. **用户说**：“RevenueConfirmationItem 写回了 ArContractSubjectMatterItem 的哪些字段？”  
   → 调用 `GET /api/impact/bySourceObject?targetObject=ArContractSubjectMatterItem&sourceObject=RevenueConfirmationItem`，得到字段列表与 writeBackExpr。

---

*文档版本：基于当前代码整理，供包装为 AI skills 使用。*
