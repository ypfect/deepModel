# API Contract: GET /api/skills/resolve

## 请求参数（无变更）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| query | String | 是 | 自然语言查询（如 "应收合同的金额字段"） |
| maxResults | Integer | 否 | 最大返回对象数（默认 5） |
| includeFields | Boolean | 否 | 是否包含字段匹配（默认 true） |

## 响应结构（扩展）

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "query": "审批状态",
    "objectMatches": [
      {
        "objectType": "ArContract",
        "title": "应收合同",
        "description": "...",
        "type": "bill",
        "isDisabled": false,
        "isTree": false,
        "isDetail": false,
        "isSupportChangeLog": true,
        "isCustomizedEntity": false,
        "isMultiDataVersion": false,
        "appName": "contract",
        "score": 600.0,
        "matchSource": "TITLE_CONTAINS",
        "detailEntities": ["ArContractSubjectMatterItem"],
        "parentEntity": null,
        "fieldMatches": [
          {
            "field": "approveStatusId",
            "title": "审批状态",
            "description": "单据审批状态",
            "enumType": "ApproveStatus",
            "isDisabled": false,
            "score": 800.0,
            "matchSource": "TITLE_EXACT",
            "bizType": null,
            "category": "BASE",
            "hasWriteBack": false,
            "hasTrigger": false,
            "refPath": null,
            "matchType": "DIRECT",
            "dependedByCount": 2,
            "dependedByFields": ["canApprove", "statusLabel"],
            "writeBackSource": null,
            "enumValues": [
              {"value": "1", "title": "待审批", "ordinal": 1, "isDisabled": false},
              {"value": "2", "title": "审批中", "ordinal": 2, "isDisabled": false},
              {"value": "3", "title": "已审批", "ordinal": 3, "isDisabled": false}
            ]
          }
        ]
      }
    ],
    "enumMatches": [
      {
        "enumType": "ApproveStatus",
        "title": "审批状态",
        "description": "单据审批流程状态",
        "score": 1000.0,
        "matchSource": "TITLE_EXACT",
        "values": [
          {"value": "1", "title": "待审批", "ordinal": 1, "isDisabled": false},
          {"value": "2", "title": "审批中", "ordinal": 2, "isDisabled": false},
          {"value": "3", "title": "已审批", "ordinal": 3, "isDisabled": false}
        ],
        "usedByFields": [
          "ArContract.approveStatusId",
          "ApContract.approveStatusId",
          "Reimburse.approveStatusId"
        ]
      }
    ]
  }
}
```

## 向后兼容性

- `objectMatches` 结构中新增的字段（isTree/isDetail/dependedByCount 等）均为可选字段，旧客户端忽略
- `enumMatches` 是新增的顶级字段，旧客户端不解析该 key，不受影响
- 接口入参签名不变
