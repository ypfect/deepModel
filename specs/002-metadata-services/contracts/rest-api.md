# REST API Contracts: 元数据服务

## 1. 回写触发关系查询

### GET /api/metadata/writeback-relations/{objectType}

查询指定源对象触发的所有回写关系。

**Path Parameters**:
- `objectType` (String, required) — 源对象类型名（如 `ArInvoiceItem`）

**Response** `200 OK`:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "srcObjectType": "ArInvoiceItem",
    "targets": {
      "ArContract": [
        {
          "targetFieldName": "invoicedAmount",
          "expression": "sum(invoice_amount)",
          "idField": "contractId",
          "condition": "bill_status_id = 'BillStatus.effective'",
          "sourceVars": ["invoiceAmount", "billStatusId", "contractId"]
        }
      ]
    }
  }
}
```

### GET /api/metadata/writeback-field-vars/{objectType}

查询指定目标对象被回写字段涉及的源变量。

**Path Parameters**:
- `objectType` (String, required) — 目标对象类型名（如 `ArContract`）

**Response** `200 OK`:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "invoicedAmount": ["invoiceAmount", "billStatusId", "contractId"],
    "receiptedAmount": ["receiptAmount", "billStatusId"]
  }
}
```

### GET /api/metadata/writeback-cascade/{objectType}

查询指定源对象的级联回写链路。

**Response** `200 OK`:
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "srcObjectType": "ArInvoiceItem",
      "targetObjectType": "ArContract",
      "targetFieldName": "invoicedAmount",
      "cascadeTargetObjectType": "ArFrameContract",
      "cascadeTargetFieldName": "totalInvoicedAmount"
    }
  ]
}
```

## 2. 表达式字段依赖层级

### GET /api/metadata/expression-fields/{objectType}

查询指定对象内表达式字段的变量依赖。

**Path Parameters**:
- `objectType` (String, required) — 对象类型名

**Response** `200 OK`:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "objectType": "SalesOrder",
    "exprFieldToVars": {
      "SalesOrder.amount": ["orderItems.price", "orderItems.qty"],
      "SalesOrder.discountAmount": ["amount", "orderItems.discountAmount"]
    },
    "noVarExprFields": ["SalesOrder.orderItemsCount"],
    "fieldToExprFields": {
      "SalesOrderDetail.price": ["SalesOrderDetail.discountAmount", "SalesOrder.amount"],
      "SalesOrder.amount": ["SalesOrder.discountAmount"]
    },
    "levelToFields": {
      "-1": ["SalesOrder.discount", "SalesOrderDetail.qty", "SalesOrderDetail.price"],
      "0": ["SalesOrder.amount", "SalesOrderDetail.discountAmount"],
      "1": ["SalesOrder.discountAmount"]
    }
  }
}
```

## 3. 对象引用关系

### GET /api/metadata/refer-relations/{objectType}

查询指定对象被谁引用。

**Path Parameters**:
- `objectType` (String, required) — 被引用的对象类型名

**Response** `200 OK`:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "ArInvoiceItem": {
      "contractId": false
    },
    "ContractSubjectMatterItem": {
      "contractId": true
    }
  }
}
```

### GET /api/metadata/refer-relations

查询全量引用关系（全景图）。

**Response** `200 OK`:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "ArContract": {
      "ArInvoiceItem": {"contractId": false},
      "ContractSubjectMatterItem": {"contractId": true}
    },
    "ALL": {
      "SomeEntity": {"polymorphicFkId": false}
    }
  }
}
```

## 通用错误响应

```json
{
  "code": 404,
  "message": "Object type 'Unknown' not found in metadata",
  "data": null
}
```
