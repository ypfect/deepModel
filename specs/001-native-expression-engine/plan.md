# Implementation Plan: 本地化表达式解析引擎

**Branch**: `001-native-expression-engine` | **Date**: 2026-05-05 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-native-expression-engine/spec.md`

## Summary

将 DeepModel 中 trigger 表达式和回写表达式的**关系解析与 SQL 生成**从 HTTP 远程调用改为本地实现。当前 `UpgradeScriptService.callWriteBackSqlApi()` 通过 OkHttp 调用外部服务获取回写 SQL，本项目的目标是参考 platform 中 `WriteBackWorker` 和 `ExpressionFieldFunctor` 的表达式语义解析逻辑，在 DeepModel 本地生成等效的回写 SQL 和 trigger SQL，消除外部服务依赖。

## Technical Context

**Language/Version**: Java 8 / Spring Boot 2.7 / MyBatis
**Primary Dependencies**: JSqlParser (已有), OkHttp3 (待消除对回写SQL的依赖), Jackson, Guava Cache
**Storage**: PostgreSQL (通过 MyBatis Mapper 读取 `baseapp_object_field`)
**Testing**: JUnit 5, Maven Surefire, H2 内嵌数据库
**Target Platform**: Linux / macOS 服务端
**Project Type**: Web Service (元数据分析工具)
**Performance Goals**: 单对象字段影响分析 ≤ 3s (depth ≤ 5)
**Constraints**: 堆内存 ≤ 512MB, 不执行实际数据变更
**Scale/Scope**: ≤ 500 个对象类型, ≤ 10 万字段记录

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. 代码质量 | ✅ PASS | Java 8 兼容, 构造器注入, 参数化 SQL |
| II. 测试标准 | ✅ PASS | 每个新 Service 方法附带单测, 算法变更有回归测试 |
| III. 用户体验一致性 | ✅ PASS | API 响应格式不变, Feature Flag 保证兼容 |
| IV. 性能要求 | ✅ PASS | 本地生成 SQL 比 HTTP 调用更快, 符合 3s 指标 |

## Project Structure

### Documentation (this feature)

```text
specs/001-native-expression-engine/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── checklists/
│   └── requirements.md  # Quality checklist
└── tasks.md             # Phase 2 output (by /speckit.tasks)
```

### Source Code (repository root)

```text
src/main/java/com/deepmodel/relation/
├── model/
│   ├── BaseappObjectField.java    # 已有 - 字段元数据模型
│   ├── WriteBackExpr.java         # 已有 - 回写表达式模型(已含executingMoment)
│   └── GraphModels.java           # 已有 - 图模型
├── service/
│   ├── ImpactAnalyzerService.java # 已有 - 增加 MetadataService 适配方法
│   ├── FormulaParserService.java  # 已有 - triggerExpr SQL 解析(复用)
│   ├── UpgradeScriptService.java  # 已有 - 替换 callWriteBackSqlApi() HTTP 调用
│   ├── ExpressionValidatorService.java # 已有 - 表达式校验
│   ├── WriteBackSqlGenerator.java # 新增 - 本地回写 SQL 生成器
│   └── TriggerSqlGenerator.java   # 新增 - 本地 trigger SQL 生成器(若需要)
├── config/
│   └── ExpressionEngineConfig.java # 新增 - Feature Flag 配置
└── util/
    └── ExprUtils.java             # 已有 - 表达式工具类(可能需增强)

src/test/java/com/deepmodel/relation/service/
├── WriteBackSqlGeneratorTest.java  # 新增
└── TriggerSqlGeneratorTest.java    # 新增(若需要)
```

**Structure Decision**: 新增核心类 `WriteBackSqlGenerator` 负责本地生成回写 SQL，`ExpressionEngineConfig` 管理 Feature Flag，修改 `UpgradeScriptService` 在 Feature Flag 控制下路由到本地或 HTTP。

## Complexity Tracking

> 无 Constitution Check 违规，此表留空。
