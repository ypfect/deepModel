# Feature Specification: Expression Consistency Checker

**Feature Branch**: `001-expression-consistency-checker`
**Created**: 2026-04-14
**Status**: Draft
**Input**: User description: "表达式一致性校验引擎（Expression Consistency Checker）动机：当前系统只「解析」表达式提取字段引用，但不「验证」表达式本身是否与元数据一致。实际开发中经常遇到：writeBackExpr 引用了已被重命名/删除的字段, triggerExpr 中引用的字段实际不属于当前对象, expression 中的聚合函数（SUM/COUNT）与字段类型不匹配"

## Clarifications

### Session 2026-04-14
- Q: 表达式存在严重语法错误无法解析时如何处理？ → A: 记录为 FATAL 级别的 ParseError 并包含在报告中，跳过当前表达式，继续扫描后续表达式。
- Q: 校验报告是否需要区分异常的严重程度？ → A: 是的，支持区分 ERROR 和 WARNING 两个级别。
- Q: 新增的校验 API 应该如何与系统现有架构暴露？ → A: 作为一个新的独立 Controller（如 ValidationController），以 `/api/validation/...` 的形式暴露。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 验证全量/单个对象的表达式一致性 (Priority: P1)

开发人员或业务实施人员希望能够在系统启动前或配置变更后，主动触发对单个对象或整个应用模块的表达式配置进行检查，以便及时发现并修复错误引用，防止运行时崩溃或数据计算错误。

**Why this priority**: 核心诉求是排错。系统当前只解析不验证，导致无效的表达式可能顺利进入系统，在运行时产生难以排查的 Bug。

**Independent Test**: 能够针对配置了错误表达式（例如引用不存在的字段）的测试对象调用校验 API，并准确收到失败报告即可验证功能完整性。

**Acceptance Scenarios**:

1. **Given** 一个包含 `writeBackExpr` 回写公式且引用了下游已删除字段的对象，**When** 用户调用针对该对象的校验 API，**Then** 返回的报告中明确指出该字段引用不存在的对象或字段。
2. **Given** 一个包含 `triggerExpr` 的对象，且公式中引用了非该对象的字段，**When** 用户调用模块级别的校验 API，**Then** 返回的报告中指出对象名和具体的语法或上下文错误。

---

### User Story 2 - 输出结构化的异常修复报告 (Priority: P2)

作为系统维护者，希望获取一份清晰的、按严重程度分类的校验结果列表，以便安排修复计划或在 CI/CD 流程中进行卡控。

**Why this priority**: 验证引擎只有搭配清晰的报告才能发挥价值，否则使用者面对一堆日志无法直接定位到是哪个对象的哪个字段配错了。

**Independent Test**: 可以通过 API 的返回格式或者生成的可视化结果（如 JSON 或简易列表）进行验证。

**Acceptance Scenarios**:

1. **Given** 系统中存在多种类型的表达式错误（字段不存在、类型不匹配等），**When** 查看校验报告，**Then** 可以看到错误被按对象、字段名清晰地归类，并标注了具体的错误原因（如：聚合函数 SUM 用于 String 类型字段）。

### Edge Cases & Error Handling

- **语法无法解析**: 若公式包含严重语法错误导致完全无法被解析时，校验引擎不能被异常中断。引擎应捕获该解析异常，并在最终报告中将其记录为 FATAL (如 `FATAL_PARSE_ERROR`) 的异常项，随后继续处理其他的表达式。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST 验证 `writeBackExpr` 中引用的目标字段和来源字段是否存在。
- **FR-002**: System MUST 验证 `triggerExpr` 中引用的字段是否属于当前上下文对象。
- **FR-003**: System MUST 验证 `expression` 中的聚合函数（如 SUM, COUNT）与作用字段的数据类型是否兼容。
- **FR-004**: System MUST 提供一个独立的 API 端点（如 `/api/validation/check`），用于校验指定单一对象类型的表达式健康度。
- **FR-005**: System MUST 提供一个独立的 API 端点（如 `/api/validation/report`），支持全量扫描指定应用模块下的所有对象的表达式健康度。
- **FR-006**: System MUST 返回结构化的校验结果，包含：对象名、字段名、引用的错误表达式类型、具体的错误分类（如 FIELD_NOT_FOUND, TYPE_MISMATCH）、错误严重级别（ERROR/WARNING）和错误提示文本。

### Key Entities

- **ValidationReport**: 整个校验任务的结果承载体，包含一系列校验异常项的集合以及整体的统计数据。
- **ValidationErrorItem**: 表示单条校验异常，包含出问题的 `objectType`, `fieldName`, `expressionType` (writeBack/trigger/expression), `severity` (ERROR/WARNING) 以及具体的错误描述。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 校验引擎能够 100% 准确识别测试用例中所有存在字段引用丢失或上下文错误的配置。
- **SC-002**: 针对单个模块（约数百个对象）的全量校验耗时不超过 5 秒。
- **SC-003**: 新增的校验 API 返回的结构化报告能够直接被消费方（如前端页面或 CLI）解析和展示。

## Assumptions

- **解析能力已就绪**: 假设现有的 `FormulaParserService` 或类似组件能够准确地将表达式解析为 AST (抽象语法树) 或字段依赖列表，不需要重新做词法/语法分析引擎。
- **元数据可达**: 假设系统在校验时能够获取到完整且最新的对象和字段元数据字典，用于比对存在的合法性。
- **校验为离线/独立动作**: 假设该校验并不是在每一次请求实时进行，而是作为开发时或系统启动时的辅助诊断工具，因此不需要苛求毫秒级的性能。
