# Bug 修复质量检查清单: 主界面缺少决策表功能入口

**Purpose**: 验证 bug 报告完整性和修复就绪度
**Created**: 2026-05-09
**Bug Report**: [spec.md](file:///Users/pengfyu/advance/deepModel/specs/002-metadata-services/bugs/001-fix-missing-decision-table/spec.md)

## 报告完整性

- [x] 复现步骤可执行（非模糊描述）
- [x] 期望行为和实际行为都有具体描述
- [x] 严重级别评估合理
- [x] 影响范围已评估

## 修复就绪度

- [x] 验收标准可测试
- [x] 验收标准包含回归验证
- [x] 无未解决的 [NEEDS CLARIFICATION] 标记
- [x] 环境信息足够定位问题

## Notes

- 这是一个简单 bug：根因明确（`index.html` 菜单遗漏），修复只需新增 1 个菜单项
- 建议跳过 `/speckit.plan`，直接修复
