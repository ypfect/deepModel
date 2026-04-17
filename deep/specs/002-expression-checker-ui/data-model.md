# ViewModel: Expression Checker

前端需要一个本地包装的数据格式供 UI Table 使用的绑定源。

### `ValidationReportVO` (View Model Object)
- `scannedCount`: Number
- `totalErrors`: Number
- `totalWarnings`: Number
- `lastCheckTime`: String (Formatted `YYYY-MM-DD HH:mm:ss`)
- `items`: Array<ValidationItemVM>

### `ValidationItemVM` (Table Row)
- `id`: String (计算出来的 uuid 用于 table key 绑定)
- `objectType`: String
- `fieldName`: String
- `category`: String (例如 `FIELD_NOT_FOUND`)
- `severity`: String (例如 `ERROR`)
- `severityTagColor`: String (例如 `danger`, `warning` —— 供 UI 组件按颜色绑定)
- `message`: String
