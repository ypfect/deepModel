# UI Component Contracts

## 1. StatisticsPanel (`<StatisticsPanel />`)
负责顶部三个数据大卡的展示（扫描数，严重错误数，警告数）。
**Props:**
- `scannedCount` (Number)
- `errorCount` (Number)
- `warningCount` (Number)
- `isLoading` (Boolean) - 是否启用骨架动画状态。

## 2. ControlPanel (`<ControlPanel />`)
负责条件输入与执行按钮。
**Props:**
- `isLoading` (Boolean) - 防止二次点击
**Emits:**
- `@scan-app` (appName: String) - 触发根据模块的扫描请求。
- `@scan-object` (objectName: String) - 触发单对象靶向扫描。

## 3. ErrorTable (`<ErrorTable />`)
负责主体数据清单的展现。
**Props:**
- `data` (Array<ValidationItemVM>)
- `isLoading` (Boolean) - 表格区域内的 Loading 骨架
