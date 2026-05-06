# Quickstart: 元数据服务能力提取

## 概述

本特性从 platform MetadataService 中提取 3 个核心元数据查询能力到 DeepModel，复用现有的 DB 直查模式。

## 新增服务

| 服务类 | 功能 | API 入口 |
|--------|------|----------|
| `WriteBackRelationService` | 回写触发关系全景 + 级联回写 + 字段变量 | `/api/metadata/writeback-*` |
| `ExpressionFieldService` | 表达式字段依赖 + 层级排序 + 反向映射 | `/api/metadata/expression-fields/*` |
| `EntityReferenceService` | 对象引用反向索引 | `/api/metadata/refer-relations/*` |

## 快速验证

```bash
# 启动 DeepModel
mvn spring-boot:run

# 查询 ArInvoiceItem 的回写触发关系
curl http://localhost:18080/api/metadata/writeback-relations/ArInvoiceItem | jq

# 查询 SalesOrder 的表达式字段层级
curl http://localhost:18080/api/metadata/expression-fields/SalesOrder | jq

# 查询 ArContract 被谁引用
curl http://localhost:18080/api/metadata/refer-relations/ArContract | jq
```

## 依赖关系

```
ImpactAnalyzerService.loadCache()
    └─→ WriteBackRelationService.buildIndex()
    └─→ ExpressionFieldService.buildIndex()
    └─→ EntityReferenceService.buildIndex()
```

所有索引在 `@PostConstruct` 时与现有缓存同步构建，共享 `allRows` 数据源。
