<!--
Sync Impact Report
- Version change: N/A (initial) → 1.0.0
- Modified principles: N/A (initial creation)
- Added sections:
  - Core Principles: I–IV (代码质量、测试标准、用户体验一致性、性能要求)
  - 技术约束
  - 开发工作流
  - Governance
- Removed sections: N/A
- Templates requiring updates:
  - plan-template.md: ✅ Constitution Check 部分已兼容四原则
  - spec-template.md: ✅ Success Criteria 部分已兼容性能/UX 原则
  - tasks-template.md: ✅ Phase 结构已兼容测试/性能任务类型
- Follow-up TODOs: None
-->

# DeepModel Constitution

## Core Principles

### I. 代码质量 (Code Quality)

- 所有生产代码 MUST 遵循现有项目风格（Java 8 / Spring Boot 2.7 / MyBatis 约定），禁止引入不兼容的语言特性或框架版本。
- 每个公共 API 方法 MUST 有清晰的 Javadoc，包含参数说明、返回值语义和异常场景。
- 单个方法体 MUST NOT 超过 80 行；超过时 MUST 拆分为职责单一的私有方法。
- 所有 `@Service` / `@Component` 类 MUST 通过构造器注入依赖，禁止字段注入。
- 新增代码 MUST 零编译警告（`-Xlint:all`），未使用的 import / 变量由提交者清理。
- SQL 语句（MyBatis Mapper XML 或注解）MUST 使用参数化查询，禁止字符串拼接。

### II. 测试标准 (Testing Standards)

- 每个新增 Service 方法 MUST 有对应的单元测试，覆盖正常路径 + 至少一个边界/异常路径。
- 图引擎（BFS/DFS 遍历、Tarjan SCC 检测）的算法变更 MUST 附带回归测试，验证环检测、深链检测、跨对象依赖等核心场景不退化。
- 测试 MUST 可独立运行，不依赖外部数据库或网络服务；需要数据时使用内嵌 H2 或 Mock。
- 测试命名 MUST 遵循 `方法名_场景_期望结果` 格式（如 `analyzeImpact_circularDependency_detectsAndReports`）。
- 集成测试（涉及真实数据库）MUST 标记 `@Tag("integration")`，可通过 Maven Profile 单独执行，不阻塞快速反馈循环。
- 代码合入前 MUST 通过 `mvn test`（单元测试全量通过），失败即阻断。

### III. 用户体验一致性 (UX Consistency)

- 所有 REST API 响应 MUST 使用统一的 JSON 信封格式：`{ "code": int, "message": string, "data": T }`。
- 错误响应 MUST 包含可操作的错误码（`code`）和人类可读的 `message`，禁止返回裸异常栈。
- SSE 流式输出（如模型扫描、快照对比）MUST 遵循 `event: type\ndata: json\n\n` 标准格式，确保前端无需适配不同的事件结构。
- API 路径 MUST 遵循 RESTful 命名（`/api/{resource}`），查询参数 MUST 使用 camelCase。
- 前端诊断页面（`checker.html` 等）MUST 在最新版 Chrome / Edge 上功能完整，交互行为与现有页面保持一致。
- 任何面向用户的变更（API 签名修改、响应结构变更）MUST 在 CHANGELOG 或 PR 描述中注明。

### IV. 性能要求 (Performance Requirements)

- 单对象字段影响分析（`/api/impact`）MUST 在 3 秒内返回（depth ≤ 5，数据量 ≤ 10 万字段记录）。
- 全量模型扫描（所有对象的静态校验）MUST 在 30 秒内完成（≤ 500 个对象类型）。
- 跨环境快照对比 MUST 支持流式输出，首条结果 MUST 在 5 秒内推送，避免前端长时间空白等待。
- 数据库查询 MUST 走索引；新增查询 MUST 附带 `EXPLAIN` 验证，禁止全表扫描。
- 图缓存（内存中的依赖图谱）MUST 支持按 objectType 粒度失效，禁止每次请求重建全图。
- 内存使用 MUST 控制在 JVM 堆 512MB 以内（默认配置），大数据集场景使用流式处理而非全量加载。

## 技术约束

- **语言/运行时**: Java 8, Spring Boot 2.7.x, MyBatis
- **数据库**: PostgreSQL（生产）/ H2（测试）
- **构建**: Maven, 禁止引入 Gradle 混合构建
- **依赖管理**: 新增第三方依赖 MUST 在 PR 中说明引入理由和许可证兼容性
- **生成代码**: `generated/` 包下的代码 MUST NOT 手动修改
- **编译范围**: MUST NOT 编译整个项目，只编译新增/修改的模块

## 开发工作流

- 所有功能变更 MUST 通过 feature 分支提交，直接推送 main/master 分支被禁止。
- PR 描述 MUST 包含：变更目的、影响范围、测试验证方式。
- 代码审查 MUST 验证本 Constitution 四项核心原则的合规性。
- 每个 PR MUST 保持原子性：一个 PR 解决一个问题或实现一个功能。

## Governance

- 本 Constitution 是项目开发的最高准则，所有 PR / 代码审查 MUST 验证合规性。
- 修改 Constitution MUST 提交修订说明（含旧版本 → 新版本对比）、经团队负责人批准、并更新版本号。
- 版本号遵循语义化版本：MAJOR（原则删除/重定义）、MINOR（新增原则/实质扩展）、PATCH（措辞澄清/格式调整）。
- 每季度进行一次合规性回顾，检查是否存在系统性违反原则的模式并纠正。

**Version**: 1.0.0 | **Ratified**: 2026-05-05 | **Last Amended**: 2026-05-05
