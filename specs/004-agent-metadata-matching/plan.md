# Implementation Plan: Agent 自然语言元数据匹配

**Branch**: `004-agent-metadata-matching` | **Date**: 2026-05-09 | **Spec**: [spec.md](file:///Users/pengfyu/advance/deepModel/specs/004-agent-metadata-matching/spec.md)
**Input**: Feature specification from `specs/004-agent-metadata-matching/spec.md`

## Summary

在现有 DeepModel 元数据服务中新增一个 REST API 端点 `/api/skills/resolve`，接收自然语言文本输入，通过多路匹配策略（精确英文名 → 同义词 → 中文标题 → 模糊包含）返回分层结构的匹配结果（对象匹配 → 嵌套字段匹配），每项带置信度评分和业务上下文信息。全部基于 `ImpactAnalyzerService` 已有的内存索引，无需额外 DB 查询。

## Technical Context

**Language/Version**: Java 8, Spring Boot 2.7.x  
**Primary Dependencies**: Spring Web (REST Controller), Guava Cache, ImpactAnalyzerService（已有内存索引）  
**Storage**: N/A（纯内存匹配，复用已加载的元数据）  
**Testing**: JUnit 5 + Mockito, H2 内嵌数据库  
**Target Platform**: Linux/Mac JVM 服务  
**Project Type**: Web Service（REST API 扩展）  
**Performance Goals**: 匹配 API 响应 ≤ 50ms（内存索引查询）  
**Constraints**: JVM 堆 512MB 内，不引入外部 NLP 依赖  
**Scale/Scope**: ≤ 500 个对象类型，≤ 10 万字段记录

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 状态 | 说明 |
|------|------|------|
| I. 代码质量 | ✅ 通过 | 新增代码遵循现有 Spring Boot / 构造器注入风格 |
| II. 测试标准 | ✅ 通过 | 计划为新增 Service 方法编写单元测试 |
| III. UX 一致性 | ✅ 通过 | API 响应使用与 SkillsController 一致的 JSON 格式 |
| IV. 性能要求 | ✅ 通过 | 纯内存索引查询，≤ 50ms |

## Project Structure

### Documentation (this feature)

```text
specs/004-agent-metadata-matching/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── resolve-api.md   # REST API contract
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
src/main/java/com/deepmodel/relation/
├── controller/
│   └── SkillsController.java       # 新增 /api/skills/resolve 端点
├── service/
│   ├── SkillsService.java          # 新增 resolve() 方法
│   └── ImpactAnalyzerService.java  # 复用已有内存索引（不修改）
└── model/
    └── ResolveModels.java          # 新增匹配结果模型类

src/test/java/com/deepmodel/relation/
└── service/
    └── SkillsServiceResolveTest.java  # 新增单元测试
```

**Structure Decision**: 在现有 SkillsController/SkillsService 中扩展，新增 1 个模型文件和 1 个测试文件，保持最小变更。
