# Chat Conversation

Note: _This is purely the output of the chat conversation and does not contain any raw data, codebase snippets, etc. used to generate the output._

### User Input

这个是我们前端的 ai Agent 项目，帮我看下这个项目的设计的目前已经实现的功能

### Planner Response

我来帮你分析这个前端 AI Agent 项目的设计和已实现的功能。先看看项目结构。

*Listed directory [front](file:///Users/pengfyu/workspace/77ai/front) *

*Listed directory [ai-frontend](file:///Users/pengfyu/workspace/77ai/front/ai-frontend) *

*Listed directory [ai-server](file:///Users/pengfyu/workspace/77ai/front/ai-server) *

*Listed directory [packages](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/packages) *

*Listed directory [platform](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform) *

*Listed directory [frontend](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend) *

*Viewed [README.md](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/README.md) *

*Listed directory [src](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src) *

*Viewed [package.json](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/package.json) *

*Viewed [routes.tsx](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/routes.tsx) *

*Listed directory [pages](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages) *

*Listed directory [ApplicationPage](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/ApplicationPage) *

*Listed directory [KnowledgePage](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/KnowledgePage) *

*Listed directory [SettingsPage](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/SettingsPage) *

*Viewed [index.tsx](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/ApplicationPage/index.tsx) *

*Listed directory [pages](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/SettingsPage/pages) *

*Viewed [List.tsx](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/ApplicationPage/List.tsx) *

*Listed directory [pages](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/KnowledgePage/pages) *

*Viewed [list.tsx](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/KnowledgePage/pages/list.tsx) *

*Listed directory [DocumentPage](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/KnowledgePage/pages/DocumentPage) *

*Listed directory [FlowPage](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/FlowPage) *

*Listed directory [CustomNodes](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/CustomNodes) *

*Viewed [index.tsx](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/FlowPage/index.tsx) *

*Listed directory [controllers](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/controllers) *

*Listed directory [AiProviderPage](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/SettingsPage/pages/AiProviderPage) *

*Listed directory [MainPage](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/MainPage) *

*Viewed [list.tsx](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/SettingsPage/pages/AiProviderPage/list.tsx) *

*Listed directory [pages](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/MainPage/pages) *

*Viewed [index.tsx](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/DashboardWrapperPage/index.tsx) *

*Listed directory [stores](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/stores) *

*Viewed [README.md](file:///Users/pengfyu/workspace/77ai/front/ai-server/README.md) *

*Listed directory [apps](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps) *

*Listed directory [libs](file:///Users/pengfyu/workspace/77ai/front/ai-server/libs) *

*Listed directory [docs](file:///Users/pengfyu/workspace/77ai/front/ai-server/docs) *

*Listed directory [方案设计](file:///Users/pengfyu/workspace/77ai/front/ai-server/docs/%E6%96%B9%E6%A1%88%E8%AE%BE%E8%AE%A1) *

*Viewed [系统架构.md](file:///Users/pengfyu/workspace/77ai/front/ai-server/docs/%E6%96%B9%E6%A1%88%E8%AE%BE%E8%AE%A1/%E7%B3%BB%E7%BB%9F%E6%9E%B6%E6%9E%84.md) *

### Planner Response

现在我已经掌握了足够的信息来生成一份完整的分析报告。让我创建分析文档并通知用户。

*Edited relevant file*

### User Input

刚刚代码不是最新的，重新分析一下。尤其是功能层面

### Planner Response

好的，我来重新拉取最新代码进行深度分析，重点看功能实现细节。

*Listed directory [MainPage](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/MainPage) *

*Listed directory [components](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/FlowPage/components) *

*Viewed [index.tsx](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/MainPage/pages/index.tsx) *

*Listed directory [components](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/MainPage/components) *

*Listed directory [Playground](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/Playground) *

*Listed directory [flowSidebarComponent](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/FlowPage/components/flowSidebarComponent) *

*Viewed [index.tsx](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/Playground/index.tsx) *

*Viewed [index.tsx](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/pages/FlowPage/components/flowSidebarComponent/index.tsx) *

*Listed directory [modals](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/modals) *

*Listed directory [API](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/controllers/API) *

*Listed directory [queries](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/controllers/API/queries) *

*Listed directory [IOModal](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/modals/IOModal) *

*Viewed [new-modal.tsx](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/modals/IOModal/new-modal.tsx) *

*Listed directory [chat](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/packages/chat) *

*Listed directory [src](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/packages/chat/src) *

*Listed directory [examples](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/examples) *

*Listed directory [agents](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents) *

*Viewed [flow_constants.tsx](file:///Users/pengfyu/workspace/77ai/front/ai-frontend/platform/frontend/src/flow_constants.tsx) *

*Listed directory [src](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src) *

*Listed directory [server](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/server) *

*Listed directory [qiqiai](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai) *

*Listed directory [src](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/server/src) *

*Listed directory [agents](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents) *

*Listed directory [qiqiai](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/server/src/qiqiai) *

*Viewed [__init__.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/__init__.py) *

*Listed directory [server](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/server/src/qiqiai/server) *

*Listed directory [routers](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/server/src/qiqiai/server/routers) *

*Listed directory [services](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/server/src/qiqiai/server/services) *

*Listed directory [meta_retrieval](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval) *

*Listed directory [report_insight_v2](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/report_insight_v2) *

*Viewed [__init__.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/__init__.py) *

*Viewed [__init__.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/report_insight_v2/__init__.py) *

*Edited relevant file*

### User Input

我看有一个帮忙编写表达式的功能，帮我详细分析一下这个功能是怎么实现的。

### Planner Response

我来查找项目中与"表达式编写"相关的功能代码。

*Grep searched codebase*

*Grep searched codebase*

*Grep searched codebase*

*Listed directory [formula](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/formula) *

*Viewed [system_message.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/formula/agent/system_message.py) *

*Listed directory [agent_tools](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/formula/agent_tools) *

*Listed directory [formula_tools](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/formula/agent_tools/formula_tools) *

*Listed directory [context](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/formula/context) *

*Viewed [factory.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/formula/agent_tools/formula_tools/factory.py) *

*Viewed [__init__.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/formula/context/__init__.py) *

*Viewed [utils.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/formula/context/utils.py) *

*Viewed [service.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/server/src/qiqiai/server/services/formula_service/service.py) *

*Listed directory [extension](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/formula/extension) *

*Viewed [manifest.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/formula/extension/manifest.py) *

*Listed directory [agent_state](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/formula/agent_state) *

*Viewed [schema.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/formula/agent_state/schema.py) *

### Planner Response

现在我已经掌握了所有核心实现细节，可以生成完整的分析报告了。

### User Input

增加具体例子 

### Planner Response

以下是带具体例子的完整说明：

---

## 表达式编写辅助功能——完整例子演示

### 📌 场景一：简单条件判断

**用户说：** "合同金额超过 10 万时显示高风险，否则低风险"

**Agent 执行步骤：**
1. 查字段 `amount`（`BigDecimal` 类型，确认字段名）
2. 生成草稿：`decode(amount > 100000, '高风险', '低风险')`
3. 校验语法 → 通过
4. 预览数据：取一条真实合同数据执行，返回 `"低风险"`
5. 写回公式

**❌ 用户也可能这样说：** "if amount > 10 万 then 高风险"  
**Agent 会纠正：** 禁止 `if/else`，改用 `decode()`

---

### 📌 场景二：子表聚合

**用户说：** "计算订单所有明细行的金额合计"

**业务上下文：** 主表 `SalesOrder`，子表字段 `items`（类型是 `List<OrderItem>`），明细有 `amount` 字段

**生成公式：** `sum(items, 'amount')`

**预览结果（真实数据）：**
```json
[
  { "success": true, "data": { "id": "order001" }, "result": 58000.00 },
  { "success": true, "data": { "id": "order002" }, "result": 12300.50 }
]
```

---

### 📌 场景三：级联字段引用

**用户说：** "取项目分类名称"

当前业务对象 `Contract` 中没有"分类"字段，Agent 会：
1. 找到 `Contract.project`（`referType` 指向 `Project` 对象）
2. 在 `Project` 中找到 `category`（`referType` 指向 `Category`）
3. 在 `Category` 中找到 `name`
4. 构造路径：`project.category.name`

**生成公式：** `project.category.name`

---

### 📌 场景四：空值处理 + 环境变量

**用户说：** "如果责任人没填，显示'待分配'，否则显示责任人名字"

**生成公式：** `coalesce(owner.name, '待分配')`

**另一个例子：** "计算合同距今天还有几天到期"

**生成公式：** `dayDelta(__currentDate, expireDate)`

---

### 📌 场景五：复杂嵌套条件（decode 嵌套）

**用户说：** "金额大于 10 万且项目分类是'重点项目'，标为 A 级；金额大于 5 万标为 B 级；其余 C 级"

**生成公式：**
```
decode(
  amount > 100000 && project.category.name == '重点项目', 'A级',
  decode(amount > 50000, 'B级', 'C级')
)
```

---

### 📌 场景六：关联字段等值比较（枚举/ID）

**用户说：** "判断状态是否为已审批"（status 是枚举字段）

**Agent 行为：** 先通过枚举类型查出枚举值的 ID，比如 `approved` 对应 ID 为 `"STATUS_APPROVED"`

**生成公式：** `status == 'STATUS_APPROVED'`（而不是 `status == '已审批'`，因为枚举存的是 ID）

---

### 📌 校验失败重试示例

**草稿：** `sum(items, amount)` （明细字段名忘了加引号）

**第1次校验：** 失败，错误 "MVEL parse error: amount cannot be resolved"  
**Agent 自动修正：** `sum(items, 'amount')`  
**第2次校验：** 通过 ✅

如果连续 3 次仍然失败，Agent **停止重试**，直接把草稿和错误信息展示给用户，不会死循环。

### User Input

他是怎么知道decode 这种方法，还有对象的级联呢

### Planner Response

很好的问题！这两件事的来源是**不同机制**，分开说清楚：

---

## 1. `decode` 是怎么知道的？

**来源：System Prompt 硬编码 + 动态函数列表注入**

在 [system_message.py](cci:7://file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/formula/agent/system_message.py:0:0-0:0) 的 `BASE_SYSTEM_MESSAGE` 里**明确写死**了这条规则：

```python
## 2. 逻辑控制规范
*   **条件分支**: **必须**且**只能**使用 `decode()` 函数。
    *   *正确*: `decode(amount > 100, 'High', 'Low')`
    *   *错误*: `if (amount > 100) { 'High' } else { 'Low' }`
    *   *错误*: `amount > 100 ? 'High' : 'Low'` (严禁使用三元表达式)
```

同时，`formula_service.list_functions()` 在 Agent 启动时把 `decode` 的完整用法注入到 System Prompt 尾部：

```
# 可用的公式函数
| 函数名 | 标题 | 用法 |
| decode | case when 表达式 | decode用于根据不同的条件返回相应的值，c为条件，v1为true对应值，否则v2 |
| sum | 汇总 | sum(明细表名, '字段名') |
| dayDelta | 日期相差天数 | dayDelta(日期字段, 日期字段) |
...
```

所以 LLM 在生成公式时就知道：只能用 `decode`，不能用 [if](cci:1://file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/__init__.py:24:0-26:14)。

---

## 2. 级联字段（`project.category.name`）怎么知道的？

**来源：[meta_retrieval](cci:1://file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/__init__.py:19:0-21:14) Agent 动态查询业务元数据**

这部分不是硬编码的，而是**运行时动态查询**！System Prompt 中只定义了**查询策略和格式规则**：

```python
## 字段解析策略
*  语义拆解：将"项目分类"拆解为["项目", "分类"]
*  查找关联对象：找到 Contract.project（referType 不为空）
*  探索下级字段：在 Project 中找 category
*  构建级联路径：project.category
*  级联层级：最多 3 层，比较时用 .id 而非中文名
```

然后 Agent 会**主动调用工具**去查：

```
Agent 内部执行步骤：
1. 调用 meta_retrieval 工具 → 查询 Contract 对象的字段列表
   返回: [{ name: "project", referType: "Project", ... }, ...]

2. 发现 project 是关联字段 → 再查 Project 对象的字段
   返回: [{ name: "category", referType: "Category", ... }, ...]

3. 发现 category 是关联字段 → 再查 Category 对象的字段
   返回: [{ name: "name", type: "String", ... }, ...]

4. 拼出路径：project.category.name ✅
```

---

## 总结

| 知识来源 | 知道什么 |
|---|---|
| **System Prompt 硬编码** | 语法规则（只能用 decode/禁止 if/三元禁止）、级联路径策略、字段类型规范 |
| **[list_functions()](cci:1://file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/server/src/qiqiai/server/services/formula_service/service.py:69:4-254:24) 动态注入** | 具体函数名 + 用法（decode/sum/dayDelta/coalesce 等） |
| **[meta_retrieval](cci:1://file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/__init__.py:19:0-21:14) 工具调用** | 具体业务字段名、类型、关联关系（每次对话时实时查） |

简单说：**规则是预先灌输的，字段信息是用工具实时查的。**

### User Input

元数据和字段这些是哪里做的 怎么实现的精准查询

### Planner Response

让我深入看一下 [meta_retrieval](cci:1://file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/__init__.py:19:0-21:14) 的实现。

*Listed directory [agent](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/agent) *

*Listed directory [agent_tools](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/agent_tools) *

*Viewed [retrieval_object_field.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/agent_tools/retrieval_object_field.py) *

*Viewed [retrieval_object_type.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/agent_tools/retrieval_object_type.py) *

*Listed directory [companion](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/companion) *

*Viewed [retrieval_meta.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/agent_tools/retrieval_meta.py) *

*Viewed [companion.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/companion/companion.py) *

*Listed directory [graph_db](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/graph_db) *

### User Input

Neo4j 图数据库	 这个他的实现在哪里，怎么跟我们业务系统结合起来的

### Planner Response

我来直接看 `graph_db` 模块的实现。

*Listed directory [implementations](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/graph_db/implementations) *

*Viewed [facade.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/graph_db/facade.py) *

*Viewed [neo4j_impl.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/graph_db/implementations/neo4j_impl.py) *

### Planner Response

现在已经把整个实现读完了。以下是完整的说明：

---

## Neo4j 图数据库的实现与业务系统集成

### 1. 连接配置（环境变量驱动）

**文件：** [graph_db/facade.py](cci:7://file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/graph_db/facade.py:0:0-0:0)

```python
config = GraphDBConfig(
    uri=os.getenv("NEO4J_URI", "bolt://localhost:7687"),
    username=os.getenv("NEO4J_USERNAME", "neo4j"),
    password=os.getenv("NEO4J_PASSWORD", "password")
)
```

通过三个环境变量 `NEO4J_URI` / `NEO4J_USERNAME` / `NEO4J_PASSWORD` 配置，单例模式，整个进程共用一个连接池。

---

### 2. 数据如何进入 Neo4j（数据来源）

关键问题：业务系统的对象和字段是怎么同步进 Neo4j 的？

这由 **`etl_service`**（31个文件）负责，是一套 ETL 管道，负责把业务系统的元数据定期/实时同步到 Neo4j。同步进去的节点结构是：

```cypher
// 业务对象节点
(:ObjectType {
  id, name, title, type,
  tenantId,          ← 多租户隔离
  appName,           ← 所属应用
  isTree, isDetail,  ← 是否树形/明细对象
  isCustomizedEntity ← 是否自定义对象
})

// 字段节点
(:ObjectField {
  id, name, title, type, bizType,
  tenantId,          ← 多租户隔离
  expression,        ← 取值表达式
  writeBackExpr,     ← 回写表达式
  triggerExpr,       ← 触发表达式
  enumType,          ← 枚举类型
  referInfo,         ← 引用信息(JSON)
  sourceInfo         ← 来源信息
})

// 关系
(:ObjectType)-[:HAS_FIELD]→(:ObjectField)     // 对象拥有字段
(:ObjectField)-[:REFERS_TO]→(:ObjectType)     // 外键引用
(:ObjectType)-[:HAS_LIST]→(:ObjectType)       // 子表关系
```

---

### 3. 多租户隔离

所有查询都带 `tenantId` 过滤：

```cypher
WHERE ot.tenantId = $tenantId OR ot.tenantId IS NULL
-- 匹配自己租户的数据 OR 系统公共数据（tenantId为空）
```

---

### 4. 查询层实现要点

**[neo4j_impl.py](cci:7://file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/graph_db/implementations/neo4j_impl.py:0:0-0:0)（750行）**核心能力：

| 功能 | 实现方式 |
|---|---|
| **多组对象并行查询** | `ThreadPoolExecutor`，每组一个线程同时查 |
| **相似度匹配** | Cypher 内用 `CASE WHEN` 计算 1.0/0.8/0.6 三档相似度 |
| **核心词提取** | [_extract_core_words()](cci:1://file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/graph_db/implementations/neo4j_impl.py:531:4-570:32) 去除"明细"/"子表"/"编号"等业务后缀再模糊匹配 |
| **结果缓存** | 父类 `BaseGraphDB` 提供缓存层，避免重复查询 |
| **断线重试** | 失败自动重试 3 次，指数退避（1s/2s/4s） |
| **referTo 跨对象查询** | 专门的 [_query_refer_fields()](cci:1://file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/graph_db/implementations/neo4j_impl.py:666:4-748:22) 沿 `REFERS_TO` 边查目标对象 |

---

### 5. 整体集成流程

```
                  业务系统（原有 Java 服务）
                         ↓
                   etl_service（ETL 管道）
                         ↓ 定期/实时同步
                       Neo4j 图数据库
                    (:ObjectType) ←→ (:ObjectField)
                         ↓
           meta_retrieval companion（Python 层查询）
           ├── Jieba 分词 → 关键词
           ├── 对话前预检索（对象+关系+字段注入 System Prompt）
           └── LLM 调用工具（精准 Cypher 查询）
                         ↓
                   formula agent 使用字段信息
                   生成并校验 MVEL 表达式
```

**简单说：业务系统是数据源 → ETL 把元数据同步进 Neo4j → AI 层通过 Cypher 查 → 告诉 LLM → LLM 写公式。**

### User Input

 Neo4j 图数据库是怎么生成的

### Planner Response

让我直接看 ETL 服务的实现。

*Listed directory [etl_service](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/server/src/qiqiai/server/services/etl_service) *

*Listed directory [etl](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/server/src/qiqiai/server/routers/etl) *

*Viewed [impl.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/server/src/qiqiai/server/services/etl_service/impl.py) *

*Viewed [router.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/server/src/qiqiai/server/routers/etl/router.py) *

*Listed directory [extractors](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/server/src/qiqiai/server/services/etl_service/extractors) *

*Viewed [manifest.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/server/src/qiqiai/server/services/etl_service/manifest.py) *

*Grep searched codebase*

### Planner Response

关键找到了 [scripts/neo4j_init.py](cci:7://file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/scripts/neo4j_init.py:0:0-0:0) 和 [scripts/sync_metadata.py](cci:7://file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/scripts/sync_metadata.py:0:0-0:0)！这就是答案。

*Viewed [neo4j_init.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/scripts/neo4j_init.py) *

*Viewed [sync_metadata.py](file:///Users/pengfyu/workspace/77ai/front/ai-server/apps/agents/src/qiqiai/agents/meta_retrieval/scripts/sync_metadata.py) *