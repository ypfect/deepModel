# Bug 报告: 枚举字段索引为空 + 依赖摘要未填充

**Feature Branch**: `007-fix-enum-field-index`  
**Created**: 2026-05-11  
**Status**: Draft  
**严重级别**: P2-一般  
**Input**: 006-enhance-metadata-resolve 迭代的自动化 API 测试中，TC-15b（枚举关联字段 usedByFields 为空）和 TC-18b（字段依赖摘要 dependedByCount 为 null）两项未通过

## 环境信息 *(mandatory)*

- **版本/分支**: `006-enhance-metadata-resolve` 迭代完成后
- **运行环境**: 本地开发环境 localhost:18080
- **相关配置**: 连接远程 PostgreSQL 数据库，Q7Link 标准元数据

## 复现步骤 *(mandatory)*

### Bug 1: enumFieldIndex 为空

1. 启动 DeepModel 服务
2. 调用 `GET /api/skills/resolve?query=收款状态&maxResults=5`
3. 查看 `enumMatches[0].usedByFields`，发现为 `null` 或空数组
4. 调用 `GET /api/skills/searchFields?namePattern=receiptStatusId&objectType=ArContract&limit=1` 查看字段 `enumType` 属性，发现为 null

### Bug 2: dependedByCount 未填充

1. 启动 DeepModel 服务
2. 调用 `GET /api/skills/resolve?query=ArContractSubjectMatterItem的数量&maxResults=1`
3. 查看 fieldMatches 中 `quantity` 字段的 `dependedByCount`，发现为 null

**复现概率**: 必现

## 期望行为 vs 实际行为 *(mandatory)*

### 期望行为

1. **enumFieldIndex**: 枚举搜索结果中应包含 `usedByFields` 列表，显示哪些 `对象.字段` 使用了该枚举（如 `"ArContract.receiptStatusId"`）。字段匹配结果中的 `enumType` 应有值（如 `"ReceiptStatusEnum"`），且 `enumValues` 应展开
2. **dependedByCount**: 字段匹配结果中应附带被依赖计数和依赖字段列表（如 `dependedByCount=3, dependedByFields=["amount","amountWithoutTax"]`）

### 实际行为

1. **enumFieldIndex**: 所有字段的 `enumType` 均为 null，导致 `enumFieldIndex` 为空，`usedByFields` 无数据
2. **dependedByCount**: 所有字段的 `dependedByCount` 和 `dependedByFields` 均为 null

## 影响范围 *(mandatory)*

- **受影响的功能**: US3 枚举搜索的 `usedByFields` 关联功能、US4 表达式依赖摘要功能
- **受影响的用户范围**: 所有使用 resolve API 的 AI Agent
- **数据影响**: 无数据损坏，仅返回不完整
- **是否有临时绕过方案**: 有。枚举搜索本身可用（能搜到枚举、展开枚举值），只是缺少字段关联信息。依赖信息可通过专用的 `/api/skills/impact` 接口获取

## 根因假设 *(mandatory)*

### Bug 1: enumType 未从元数据 JSON 正确提取

- **假设**: `enrichFieldMetadata()` 从元数据 JSON 的 `fields[].enumType` 路径提取 `enumType`，但 Q7Link 实际元数据中，枚举类型信息可能存储在 `fields[].properties.enumType` 路径下
- **可疑代码位置**: [ImpactAnalyzerService.java#enrichFieldMetadata](file:///Users/pengfyu/advance/deepModel/src/main/java/com/deepmodel/relation/service/ImpactAnalyzerService.java) L595-598
- **验证方式**: 从 `selectEntityMetadataContents` 查一条实际的元数据 JSON，检查 `enumType` 的实际路径

### Bug 2: dependedByCount 未在字段匹配结果中填充

- **假设**: `buildFieldMatchObj()` 构建字段匹配对象时，没有查询 `ExpressionFieldService` 的反向依赖索引来填充 `dependedByCount` 和 `dependedByFields`
- **可疑代码位置**: [SkillsService.java#buildFieldMatchObj](file:///Users/pengfyu/advance/deepModel/src/main/java/com/deepmodel/relation/service/SkillsService.java) L1262-1295

## 修复约束

- 不改变 resolve API 的入参签名和响应结构
- 不影响已有的枚举搜索功能（按名称/标题搜索枚举、展开枚举值）
- 不改变 ExpressionFieldService 的内部数据结构

## 验收标准 *(mandatory)*

- **AC-001**: 调用 `resolve?query=收款状态` 后，`enumMatches` 中至少有一个结果的 `usedByFields` 非空，包含 `"ArContract.receiptStatusId"` 等字段引用
- **AC-002**: 调用 `resolve?query=ArContract的receiptStatusId` 后，fieldMatch 中 `receiptStatusId` 的 `enumType` 不为 null
- **AC-003**: 调用 `resolve?query=ArContractSubjectMatterItem的数量` 后，`quantity` 字段的 `dependedByCount` 为数值（>= 0），如果该字段确实有依赖则 `dependedByFields` 非空
- **AC-004**: 已有的枚举搜索功能（TC-12b~TC-14b、TC-16）和其他 resolve 功能不受影响（回归验证）
- **AC-005**: 单元测试 `SkillsServiceResolveTest` 全部通过

## 关联信息

- **关联迭代**: [006-enhance-metadata-resolve](file:///Users/pengfyu/advance/deepModel/specs/006-enhance-metadata-resolve/spec.md)
- **测试报告**: TC-15b、TC-18b
- **首次发现时间**: 2026-05-11 13:53
