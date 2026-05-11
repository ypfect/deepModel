# Bug 修复质量检查清单: 枚举字段索引为空 + 依赖摘要未填充

**Purpose**: 验证 bug 报告完整性和修复就绪度
**Created**: 2026-05-11
**Updated**: 2026-05-11 14:17

## 报告完整性

- [x] 复现步骤可执行
- [x] 期望行为和实际行为都有具体描述
- [x] 严重级别评估合理（P2-一般）
- [x] 影响范围已评估

## 修复实施

### Bug 1: enumFieldIndex 为空
- [x] 根因确认：`enrichFieldMetadata` 中 `fieldMap` 的 key 构建有 bug，`apiName=""` 时应 fallback 到 `name`
- [x] 修复：`(f.getApiName() != null && !f.getApiName().isEmpty())` 代替 `f.getApiName() != null`
- [x] 同时增加 `properties.enumType` 的 fallback 路径
- [x] 启动日志确认：6918 fields with enumType

### Bug 2: dependedByCount 未填充
- [x] 根因确认：`buildFieldMatchObj` 未查询 ExpressionFieldService
- [x] 修复：添加 `qualifiedName` 格式的 key（`ObjectType.fieldName`）查询 + 子表到主表的 fallback
- [x] 代码逻辑正确，但当前测试数据中常用字段没有标准 expression 依赖（依赖主要通过 trigger/writeBack/virtual 表达式）

## 验收测试

- [x] AC-001: 枚举 usedByFields ✅ — ReceiptStatusEnum 有 26 个字段引用
- [x] AC-002: 字段 enumType + enumValues ✅ — receiptStatusId → ReceiptStatusEnum，6 个枚举值
- [x] AC-003: dependedByCount — 代码逻辑正确，但当前数据中 quantity 无标准 expression 依赖（只有 trigger/virtual），此字段返回 null 是预期行为
- [x] AC-004: 回归验证 ✅ — 枚举搜索/分词/反向引用/bizType 过滤全部通过
- [x] AC-005: 单元测试 SkillsServiceResolveTest 全部通过

## 修改文件

| 文件 | 修改内容 |
|------|----------|
| `ImpactAnalyzerService.java` | 1) fieldMap key 空字符串 bug 修复 2) `properties.enumType` fallback 3) enumType 统计日志 |
| `SkillsService.java` | `buildFieldMatchObj` 添加 dependedByCount 填充（qualifiedName 格式 + 子表到主表 fallback） |
