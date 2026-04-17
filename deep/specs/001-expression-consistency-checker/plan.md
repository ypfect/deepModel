# Implementation Plan: Expression Consistency Checker

**Branch**: `001-expression-consistency-checker` | **Date**: 2026-04-14 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-expression-consistency-checker/spec.md`

## Summary

实现一个新的表达式一致性校验引擎，用于静态检查元数据中配置的 `writeBackExpr`, `triggerExpr` 和 `expression`。其验证范围包括被引用的字段是否真实存在，以及聚合函数与其作用的字段类型是否匹配。引擎应当具备应对解析异常的健壮性（记录为 FATAL 而非中断），并根据配置问题的严重程度区分 ERROR 和 WARNING。对外将新独立的 `ValidationController` 暴漏给使用端。

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 4.0.3, JSqlParser, Guava
**Storage**: N/A (Read from memory structs managed by other services)
**Testing**: JUnit 5
**Target Platform**: Linux Server / Java Runtime
**Project Type**: Web Service API
**Performance Goals**: 针对单应用模块（如数百个对象）全量扫描不超过 5 秒。
**Constraints**: 必须尽可能复用 `FormulaParserService` 内核；不直接持久化报告，以 API response 返回结果。
**Scale/Scope**: 涉及核心域所有实体配置；API 无状态设计。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Data Integrity**: 校验引擎只作读操作，不改写数据库或内存状态，不存在完整性破坏风险。
- **Core Principles**: 符合 "Simplicity" 原则（独立 Controller 管理，不揉合 `ImpactController`）以及 "Testability" 原则，要求编写 JUnit 的 AST 验证单测。
**状态**: 校验通过 ✅。

## Project Structure

### Documentation (this feature)

```text
specs/001-expression-consistency-checker/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── api.md           # API endpoints defined
└── tasks.md             # Phase 2 output (to be created next)
```

### Source Code (repository root)

```text
src/main/java/com/deepmodel/relation/
├── controller/
│   └── ValidationController.java
├── service/
│   └── ExpressionValidatorService.java
├── model/
│   ├── ValidationReport.java
│   └── ValidationErrorItem.java
└── enums/
    ├── ExpressionType.java
    ├── ErrorCategory.java
    └── SeverityLevel.java
```

**Structure Decision**: 遵循 Java 项目标准的 MVC 分层模式并在现有 `com.deepmodel.relation` 包下进行模块扩容。新增了独立的枚举目录（若不存在）或合入 `model` 包（取决于项目习惯，计划放于 `model` 包下合并定义）。

## Complexity Tracking

本项目无严重的框架变更和特异设计，无需复杂性追踪。
