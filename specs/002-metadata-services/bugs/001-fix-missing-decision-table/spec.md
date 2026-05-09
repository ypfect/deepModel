# Bug 报告: 主界面缺少决策表功能入口

**Feature Branch**: `003-fix-missing-decision-table`  
**Created**: 2026-05-09  
**Status**: Draft  
**严重级别**: P2-一般  
**Input**: Bug 描述: "http://localhost:18080/ 这个界面没有决策表的功能"

## 环境信息

- **版本/分支**: 当前开发分支
- **运行环境**: 开发环境（localhost:18080）
- **相关配置**: 无特殊配置

## 复现步骤

1. 启动 DeepModel 应用（`localhost:18080`）
2. 打开浏览器访问 `http://localhost:18080/`
3. 查看左侧侧边栏菜单

**复现概率**: 必现

## 期望行为 vs 实际行为

### 期望行为

侧边栏菜单应包含"决策表"相关功能入口（如"FuncUnit 编排模拟器"），点击后可加载 `simulator.html` 页面，使用已有的决策表分析和模拟功能。

### 实际行为

侧边栏仅有以下 5 个菜单项：
1. 📈 字段影响分析 → `modern.html`
2. 🔁 跨对象来源分析 → `cross.html`
3. 🔗 引用查询 → `reference.html`
4. 🛠️ 高级管理 → `management.html`
5. 🩺 配置体检中心 → `checker.html`

缺少决策表/FuncUnit 模拟器的入口。用户只能手动访问 `/simulator.html` 才能使用该功能。

## 影响范围

- **受影响的功能**: 决策表分析、FuncUnit 编排模拟功能的可发现性
- **受影响的用户范围**: 所有通过主界面访问系统的用户
- **数据影响**: 无数据损坏风险
- **是否有临时绕过方案**: 有，手动访问 `http://localhost:18080/simulator.html`

## 根因假设

- **假设 1**: `simulator.html` 页面及后端 API（`DecisionTableController`、`DecisionTableIndexService`、`DecisionTableSimulatorService`）已全部实现，但 `index.html` 侧边栏菜单遗漏了对应的菜单项
- **可疑代码位置**: `src/main/resources/static/index.html` 第 158-180 行，`.menu` 区域中缺少指向 `simulator.html` 的菜单项

## 验收标准

- **AC-001**: 访问 `http://localhost:18080/`，侧边栏菜单中存在决策表/FuncUnit 模拟器入口
- **AC-002**: 点击该菜单项后，主内容区正确加载 `simulator.html` 页面，功能可正常使用
- **AC-003**: 现有的 5 个菜单项功能不受影响（回归验证）
