# Quickstart: 增强自然语言元数据匹配

## 变更概要

增强 `/api/skills/resolve` API 的匹配能力，输入中文自然语言，返回精准匹配的对象+子表+字段结果。

## API 使用示例

### 基础对象查询

```bash
curl "http://localhost:8080/api/skills/resolve?query=应收合同"
```

返回 ArContract 对象匹配（score ≈ 1.0）。

### 子表字段查询

```bash
curl "http://localhost:8080/api/skills/resolve?query=收入确认单子表的收款金额"
```

返回 RevenueConfirmation → 子表 RevenueConfirmationItem 下的收款金额字段匹配。

### 多层导航查询

```bash
curl "http://localhost:8080/api/skills/resolve?query=应收合同的标的子表里面的收款金额"
```

返回 ArContract → ArContractSubjectMatterItem → receiptAmount 字段匹配。

## 返回结构（不变，仅扩展字段）

```json
{
  "objectMatches": [
    {
      "objectType": "ArContractSubjectMatterItem",
      "title": "应收合同标的明细",
      "description": "应收合同标的物行项目明细",
      "type": "bill",
      "isDisabled": false,
      "score": 0.85,
      "fields": [
        {
          "field": "receiptAmount",
          "title": "收款金额",
          "description": "已收款金额合计",
          "enumType": null,
          "isDisabled": false,
          "score": 1.0,
          "bizType": "Amount",
          "category": "AMOUNT"
        }
      ]
    }
  ]
}
```

## 开发验证

```bash
# 编译
mvn compile -pl . -q

# 运行测试
mvn test -pl . -Dtest=SkillsServiceResolveTest

# 启动服务
mvn spring-boot:run
```
