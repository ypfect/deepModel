# API Contract: /api/skills/resolve

## Endpoint

```
GET /api/skills/resolve?query={text}&maxResults={n}&includeFields={bool}
```

## Parameters

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| query | String | ✅ | - | 自然语言输入文本 |
| maxResults | int | ❌ | 5 | 最多返回对象匹配数 |
| includeFields | boolean | ❌ | true | 是否同时返回字段匹配 |

## Response

### 200 OK

```json
{
  "query": "应收合同的原始金额",
  "objectMatches": [
    {
      "objectType": "ArContract",
      "title": "应收合同",
      "score": 0.9,
      "matchSource": "SYNONYM",
      "detailEntities": ["ArContractSubjectMatterItem", "ArContractSettlementItem"],
      "parentEntity": null,
      "fieldMatches": [
        {
          "field": "originAmount",
          "title": "原始金额",
          "score": 0.8,
          "matchSource": "TITLE_EXACT",
          "bizType": "Amount",
          "category": "AMOUNT",
          "hasWriteBack": true,
          "hasTrigger": false
        }
      ]
    }
  ]
}
```

### 400 Bad Request

query 参数为空时返回。

### 匹配来源枚举

| 值 | 说明 | 置信度 |
|------|------|--------|
| EXACT_NAME | 精确英文名匹配 | 1.0 |
| SYNONYM | 同义词精确匹配 | 0.9 |
| TITLE_EXACT | 中文标题精确匹配 | 0.8 |
| TITLE_CONTAINS | 中文标题包含匹配 | 0.6 |
