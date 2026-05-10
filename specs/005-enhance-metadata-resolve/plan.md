# Implementation Plan: 增强自然语言元数据匹配（Resolve）

**Branch**: `005-enhance-metadata-resolve` | **Date**: 2026-05-10 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/005-enhance-metadata-resolve/spec.md`

## Summary

增强 DeepModel 的 `SkillsService.resolve()` 自然语言元数据匹配功能，使其覆盖 trek grep 的全部匹配能力。核心改动包括：数据源补充 5 个字段维度、引入 Jieba 分词预处理、自动构建同义词索引、修复子表查询 bug、实现级联字段搜索、移植 5 档评分+紧凑度修正算法。

## Technical Context

**Language/Version**: Java 8, Spring Boot 2.7.x, MyBatis
**Primary Dependencies**: Jieba（已引入 com.huaban.analysis.jieba）、Guava Cache、Jackson
**Storage**: PostgreSQL（baseapp_object_field / baseapp_object_type / baseapp_system_metadata）
**Testing**: JUnit 5 + H2 内嵌数据库，`@Tag("integration")` 标记集成测试
**Target Platform**: Linux 服务端（Docker 部署）
**Project Type**: Web Service（Spring Boot REST API）
**Performance Goals**: resolve API 响应时间增幅 ≤ 50%（基线约 10-50ms）
**Constraints**: JVM 堆 512MB，不引入新第三方依赖
**Scale/Scope**: 835+ 对象类型，19000+ 字段，单租户 DB

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 状态 | 说明 |
|------|------|------|
| I. 代码质量 | ✅ 通过 | Java 8 兼容、构造器注入、参数化 SQL、单方法≤80 行 |
| II. 测试标准 | ✅ 通过 | 每个新增/修改的 Service 方法附带单元测试 |
| III. UX 一致性 | ✅ 通过 | 保持现有 `ResponseEntity<ResolveResult>` 格式，score 归一化 0~1.0 保 API 兼容 |
| IV. 性能要求 | ✅ 通过 | 无新增全表扫描，Guava Cache 保持 objectType 粒度失效 |

## Project Structure

### Documentation (this feature)

```text
specs/005-enhance-metadata-resolve/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
src/main/java/com/deepmodel/relation/
├── model/
│   ├── BaseappObjectField.java     # 扩展：+description, enumType, isDisabled, isMasterField
│   ├── ResolveModels.java          # 扩展：FieldMatch +description, enumType, isDisabled
│   └── ObjectTypeMeta.java         # 新增：对象类型元信息（name, title, description, type, isDisabled）
├── service/
│   ├── ImpactAnalyzerService.java  # 修改：objectTitles → objectTypeMetas, 构建反向索引
│   └── SkillsService.java          # 核心修改：resolve 全流程重构
├── dao/
│   └── BaseappObjectFieldMapper.java  # 新增 mapper 方法签名（如需）
└── util/
    └── JiebaUtils.java             # 现有，无需修改

src/main/resources/
└── mapper/
    └── BaseappObjectFieldMapper.xml  # 修改：selectAll 补列, selectObjectTitles 补列

src/test/java/com/deepmodel/relation/service/
├── SkillsServiceResolveTest.java   # 新增：resolve 核心逻辑单元测试
└── ImpactAnalyzerServiceTest.java  # 扩展：数据加载相关测试
```

**Structure Decision**: 单项目结构，所有改动在现有 `com.deepmodel.relation` 包内，不引入新模块。

## Complexity Tracking

无 Constitution 违规需要 justify。
