# Implementation Plan: 增强 Resolve 元数据匹配接口

**Branch**: `006-enhance-metadata-resolve` | **Date**: 2026-05-11 | **Spec**: [spec.md](file:///Users/pengfyu/advance/deepModel/specs/006-enhance-metadata-resolve/spec.md)
**Input**: Feature specification from `/specs/006-enhance-metadata-resolve/spec.md`

## Summary

增强 `/api/skills/resolve` 自然语言元数据匹配接口，新增 5 个维度：对象特性感知、bizType 维度字段匹配、枚举类型搜索、表达式语义联动（附加摘要）、关系网络导航。核心技术路线是扩展现有数据加载流程（复用 `selectEntityMetadataContents` 的 content JSON），在 `SkillsService.resolve` 中增加匹配路径和响应结构。

## Technical Context

**Language/Version**: Java 8, Spring Boot 2.7.x, MyBatis  
**Primary Dependencies**: Jackson (JSON 解析), Guava (Cache), JSqlParser  
**Storage**: PostgreSQL（baseapp_system_metadata / baseapp_object_type / baseapp_object_field）  
**Testing**: JUnit 5 + H2 内嵌数据库，集成测试标记 `@Tag("integration")`  
**Target Platform**: Linux server (JVM)  
**Project Type**: Web service (REST API)  
**Performance Goals**: 首次查询 < 200ms，缓存命中 < 10ms（与现有 resolve 持平）  
**Constraints**: JVM 堆 512MB，所有新增查询走索引  
**Scale/Scope**: ~500 对象类型，~50000 字段，~300 枚举类型

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 状态 | 说明 |
|------|------|------|
| I. 代码质量 | ✅ Pass | Java 8 兼容；扩展现有 Service 类；构造器注入 |
| II. 测试标准 | ✅ Pass | 每个新增匹配路径有对应单元测试 |
| III. UX 一致性 | ✅ Pass | 响应结构向后兼容，新增 enumMatches 顶级字段 |
| IV. 性能要求 | ✅ Pass | 复用启动时加载，运行时走缓存 |

## Project Structure

### Documentation (this feature)

```text
specs/006-enhance-metadata-resolve/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
src/main/java/com/deepmodel/relation/
├── model/
│   ├── ObjectTypeMeta.java          # [修改] 扩展特性字段
│   ├── EnumTypeMeta.java            # [新增] 枚举类型元信息
│   ├── EnumValueMeta.java           # [新增] 枚举值元信息
│   ├── EnumMatch.java               # [新增] 枚举匹配结果
│   ├── ResolveModels.java           # [修改] ResolveResult 新增 enumMatches
│   └── BaseappObjectField.java      # [修改] FieldMatch 新增摘要字段
├── service/
│   ├── ImpactAnalyzerService.java   # [修改] enrichFieldMetadata 扩展对象特性提取 + 枚举索引扩展
│   └── SkillsService.java           # [修改] resolve 新增匹配路径
└── controller/
    └── SkillsController.java        # [无修改] 接口签名不变

src/test/java/com/deepmodel/relation/
└── service/
    ├── SkillsServiceResolveTest.java  # [新增/扩展] resolve 新增维度测试
    └── ImpactAnalyzerServiceTest.java # [扩展] 特性提取 + 枚举索引测试
```

**Structure Decision**: 在现有代码结构内扩展，不新增模块。模型类放 `model/`，业务逻辑在 `service/` 中的已有类内扩展。

## Complexity Tracking

无 Constitution 违反项。
