# Analysis & Design Decisions

### 1. Parsing & AST Evaluation
- **Decision**: 扩展并复用现存的 `FormulaParserService` 作为表达式的解析器。 
- **Rationale**: 系统本就依赖 `JSqlParser` 处理字段图构建谱系追踪。为了实现 `FATAL_PARSE_ERROR` 记录，我们需要将之前可能抛出或被忽略的 `JSQLParserException` 显示在本次 Validation 流程中拦截，并继续进行后续字段处理。
- **Alternatives**: 重写纯正则匹配器 (被否决，极易漏判聚合函数)。

### 2. Metadata Source (元数据源)
- **Decision**: 依赖 `ImpactAnalyzerService` 加载后的内存缓存或直接使用底层的 `MetadataService`/`SnapshotService`。
- **Rationale**: 这样不仅性能极高 (`O(1)` 时间复杂度)，且这确保了我们校验的数据基础与生成执行脚本和依赖图的数据完全一致。
- **Alternatives**: 每次直连 PostgreSQL 数据库查元数据体系 (被否决，极度缓慢会导致不满足 5秒/模块 的性能目标)。

### 3. Severity Level Logic (严重级判定逻辑)
- **Decision**: 硬编码错误分类策略：
  - `FATAL_PARSE_ERROR` -> `FATAL`
  - 被引用的对象或字段完全丢失 (`FIELD_NOT_FOUND`) -> `ERROR`
  - 表达式使用了与数据类型不完全匹配，但在 DB 层尚有隐式转换可能的场景 -> `WARNING`
- **Rationale**: 通过代码分类满足了规范中区分警告和错误的核心诉求。
