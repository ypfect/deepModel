# Implementation Plan: 元数据服务能力提取

**Branch**: `002-metadata-services` | **Date**: 2026-05-06 | **Spec**: [spec.md](file:///Users/pengfyu/advance/deepModel/specs/002-metadata-services/spec.md)
**Input**: Feature specification from `specs/002-metadata-services/spec.md`

## Summary

从 platform 的 `MetadataService` 中提取 3 个核心元数据查询能力到 DeepModel：**回写触发关系图**（`getWriteBackExprFields` + `getCascadeWriteBackInfo`）、**表达式字段依赖层级**（`getExpressionFields` + `getLevelToExprFields`）、**对象引用关系图**（`getEntityReferedInfos`）。采用 DeepModel 现有的 DB 直查 + 内存缓存模式，不引入 platform 的 Entity/Field 模型体系。

## Technical Context

**Language/Version**: Java 8, Spring Boot 2.7.x  
**Primary Dependencies**: MyBatis, Jackson, JSqlParser（已有）  
**Storage**: PostgreSQL（`baseapp_object_field` + `baseapp_object_type` 表）  
**Testing**: JUnit 5 + Mockito（已有测试基础设施）  
**Target Platform**: Linux/macOS 服务端  
**Project Type**: 诊断/分析工具（Spring Boot Web 应用）  
**Performance Goals**: 缓存查询 <100ms，启动时索引构建 <2s 增量  
**Constraints**: JVM 堆 512MB，不增加新依赖  
**Scale/Scope**: ~500 对象类型，~10 万字段记录  

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 状态 | 说明 |
|------|:----:|------|
| **I. 代码质量** | ✅ | 构造器注入、Javadoc、单方法 ≤80 行、参数化查询 |
| **II. 测试标准** | ✅ | 每个 Service 方法有单元测试（Mock 数据），不依赖外部 DB |
| **III. UX 一致性** | ✅ | REST API 使用统一信封格式 `{code, message, data}` |
| **IV. 性能要求** | ✅ | 启动时构建索引缓存，查询走内存，支持 reload |
| **技术约束** | ✅ | Java 8 / Spring Boot 2.7 / MyBatis，无新依赖 |

## Project Structure

### Documentation (this feature)

```text
specs/002-metadata-services/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Phase 0: algorithm decisions
├── data-model.md        # Phase 1: data model definitions
├── quickstart.md        # Phase 1: quickstart guide
├── contracts/
│   └── rest-api.md      # Phase 1: REST API contracts
└── tasks.md             # Phase 2 output (by /speckit.tasks)
```

### Source Code (repository root)

```text
src/main/java/com/deepmodel/relation/
├── model/
│   ├── BaseappObjectField.java     # 已有，可能扩展 sourceInfo 字段
│   ├── WriteBackExpr.java          # 已有
│   ├── WriteBackRelationInfo.java  # 新增：回写关系索引项
│   ├── CascadeWriteBackInfo.java   # 新增：级联回写信息
│   ├── ExpressionFieldInfo.java    # 新增：表达式字段依赖视图
│   └── EntityReferenceIndex.java   # 新增：引用关系反向索引
├── service/
│   ├── WriteBackRelationService.java    # 新增：回写触发关系图 (US1)
│   ├── ExpressionFieldService.java      # 新增：表达式字段依赖 (US2)
│   ├── EntityReferenceService.java      # 新增：对象引用关系 (US3)
│   └── ImpactAnalyzerService.java       # 修改：loadCache 中注入并调用 3 个新服务
├── controller/
│   └── MetadataController.java          # 新增：REST API 入口
├── dao/
│   └── BaseappObjectFieldMapper.java    # 修改：新增 sourceInfo 查询

src/main/resources/mapper/
└── BaseappObjectFieldMapper.xml          # 修改：新增 SQL

src/test/java/com/deepmodel/relation/service/
├── WriteBackRelationServiceTest.java     # 新增
├── ExpressionFieldServiceTest.java       # 新增
└── EntityReferenceServiceTest.java       # 新增
```

**Structure Decision**: 3 个新服务各自独立，通过 `ImpactAnalyzerService.loadCache()` 统一初始化。REST API 集中在新的 `MetadataController` 中。

## Constitution Re-Check (Post Phase 1 Design)

| 原则 | 状态 | 说明 |
|------|:----:|------|
| **I. 代码质量** | ✅ | 3 个新服务均用构造器注入，核心算法拆分为可测试的私有方法 |
| **II. 测试标准** | ✅ | 每个服务 ≥5 个单测（含边界：空数据、JSON 异常、循环依赖） |
| **III. UX 一致性** | ✅ | MetadataController 遵循统一信封，objectType 不存在时返回空而非报错 |
| **IV. 性能要求** | ✅ | 索引构建在 PostConstruct 中完成，查询从 ConcurrentHashMap 读取 |
