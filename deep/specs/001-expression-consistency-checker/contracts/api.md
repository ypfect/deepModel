# Expression Validation API Contract

## 1. 结构一致性单对象检查
针对某指定的对象类型，立即扫描并给回报告。

**GET** `/api/validation/check?objectType={objectType}`

### Response: `200 OK`
```json
{
  "reportTime": "2026-04-14T10:00:00",
  "scannedObjectCount": 1,
  "totalErrors": 1,
  "totalWarnings": 0,
  "items": [
    {
      "objectType": "ArReceipt",
      "fieldName": "totalAmount",
      "expressionType": "WRITE_BACK",
      "errorCategory": "FIELD_NOT_FOUND",
      "severity": "ERROR",
      "message": "WriteBack 目标对象 ArReceiptList 的 originAmount 字段不存在。"
    }
  ]
}
```

## 2. 模块级全量扫描报告
扫描指定的大模块（例如 `arap`）。

**GET** `/api/validation/report?appName={appName}`

### Response: `200 OK`
*(返回结构同上，但会包含该 appName 下数百个对象的汇总和详细 items)*
