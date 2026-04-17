# Data Model: Expression Validation

为不在当前数据库落盘建表，以下模型类将被建在 `com.deepmodel.relation.model` 包下纯用于内存操作及 JSON 序列化输出。

## ValidationReport (主报告类)

- `reportTime`: `LocalDateTime` (校验生成的时间戳)
- `scannedObjectCount`: `int` (扫描对象的总数，展示进度与范围)
- `totalErrors`: `int` (所有 FATAL 和 ERROR 的总和)
- `totalWarnings`: `int` (WARNING 的总数)
- `items`: `List<ValidationErrorItem>` (异常清单)

## ValidationErrorItem (异常项类)

- `objectType`: `String` (所在对象的名称，如 ArReceipt)
- `fieldName`: `String` (归属字段，如 totalAmount)
- `expressionType`: `ExpressionType` (枚举: `WRITE_BACK`, `TRIGGER`, `EXPRESSION`)
- `errorCategory`: `ErrorCategory` (枚举: `FIELD_NOT_FOUND`, `OBJECT_NOT_FOUND`, `TYPE_MISMATCH`, `FATAL_PARSE_ERROR`)
- `severity`: `SeverityLevel` (枚举: `FATAL`, `ERROR`, `WARNING`)
- `message`: `String` (具体的提示说明文本，协助开发人员排查)

## Enum: SeverityLevel

- `FATAL`: 致命性语法错误，无 AST 树。
- `ERROR`: 阻断性引用错误，影响正常运行时和 SQL 自动生成。
- `WARNING`: 待优化或可能受其他逻辑保护的结构不符规范。
