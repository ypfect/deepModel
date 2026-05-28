# Bug 报告: resolve 查询「销售模块的邮箱字段」时 bizType=email 未优先于名称匹配

**Bug 目录**: `specs/006-enhance-metadata-resolve/bugs/002-fix-email-biztype-sort/`  
**所属迭代**: `specs/006-enhance-metadata-resolve/`  
**Created**: 2026-05-18  
**Status**: Draft  
**严重级别**: P2-一般  
**Input**: Bug 描述: "试用：对 resolve 请求「销售模块的邮箱字段」，返回的 fieldMatches 中，靠前的若干条记录的 bizType 不是 email（仅靠字段名或标题里含「邮箱」「mail」等模糊命中），与同迭代 User Story 2 验收场景（bizType 精确匹配应排在名称模糊匹配前）不符。"

## 复现步骤 *(mandatory)*

<!--
  按顺序列出可稳定复现该 bug 的最小步骤。
-->

1. 启动 DeepModel 服务（假定元数据已在本地或通过远程 PostgreSQL 加载）。
2. 调用 resolve 接口，查询文案为：**「销售模块的邮箱字段」**（或与迭代 spec 一致的等价中文表述）。
3. 在响应的 **fieldMatches**（或等价嵌套路径）中取前 **5** 条字段命中结果。
4. 核对每条记录的 **bizType** 字段：**若前 5 条中出现 bizType≠`email` 的项排在 bizType=`email` 的项之前**，则判定与本 bug 描述一致。

**复现概率**: [推断] 高概率 / 待你在目标环境跑一次接口确认

## 期望行为 vs 实际行为 *(mandatory)*

### 期望行为

- 与同迭代 **User Story 2 — bizType 维度字段匹配** 及验收场景一致：当查询意图包含 bizType 标准类型关键词（此处为「邮箱」→ `bizType=email`）且同时带有对象过滤条件（此处为「销售模块」），**所有 `bizType=email` 的字段应在排序上优先于**仅靠名称或标题模糊匹配「邮箱」、`bizType` 不为 `email` 的字段。
- 结果应可解释：用户对「邮箱字段」的语义预期是标准类型维度，而非任意含「邮箱」字符串的字段。

### 实际行为

- **[推断]** 返回列表中靠前的命中项混入 `bizType≠email` 的字段（例如仅 title/name 中含「邮箱」或「mail」），而 `bizType=email` 的字段排在后面或未出现在首屏优先位置，导致与迭代验收场景不一致。
- （待补充）若你已保存具体 HTTP 路径、样例响应 JSON 片段或日志，可直接粘贴在本文「关联信息」或实际行为小节。

## 影响范围 *(mandatory)*

- **受影响的功能**: resolve 自然语言字段匹配（bizType 中文关键词映射 + 与对象/app 维度过滤组合的排序策略）。
- **受影响的用户范围**: 依赖 `/api/skills/resolve`（或等价入口）进行元数据解析的 Agent / 控制台调用方。
- **数据影响**: 无数据损坏风险；仅为匹配顺序与可读性问题。
- **是否有临时绕过方案**: 有——查询改为更明确的实体限定 + 英文 `email` 关键词，或分两步先对象后字段；但不应作为长期方案。

## 根因假设 *(optional)*

- **假设 1**: 对象模块过滤（如 `appName` / `businessModuleId` 与「销售」相关）与 **bizType 匹配分支**的得分或排序键未正确合成，导致名称模糊匹配分数压过 bizType 精确命中。
- **假设 2**: 「邮箱」→ `email` 的映射在 **带多条件**的 query 解析路径上未生效，回退为纯文本匹配。
- **可疑代码/配置位置**: 迭代 `plan.md` / 实现中负责 `matchFields`、bizType 关键词表、以及 resolve 结果排序的类（需在代码库中按「bizType」「email」「matchFields」检索确认）。
- **相关日志/错误信息**: （待跑一遍接口后补充 requestId / 响应摘要）

## 验收标准 *(mandatory)*

- **AC-001**: 在上述复现条件下，返回的 fieldMatches 中，**所有 `bizType=email` 的字段在排序上必须早于**任一 `bizType≠email` 且仅靠名称/标题文本匹配「邮箱」语义的字段（与同迭代 FR-003 / User Story 2 一致）。
- **AC-002**: **回归**：对 User Story 2 已有验收用例仍通过（例如「应收合同的金额字段」「哪些对象有邮箱字段」「比率字段」等），未发现排序退化或其它维度（特性过滤、枚举）回归。
- **AC-003**: **边界**：当查询不包含 bizType 关键词、仅模糊文本「邮箱」时，行为可以保持以名称为主的匹配策略（不改变无 bizType 意图时的用户体验）。
