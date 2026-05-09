# Quickstart: Agent 自然语言元数据匹配

## 使用方式

### 查询对象

```bash
curl "http://localhost:18080/api/skills/resolve?query=应收合同"
```

### 查询对象+字段

```bash
curl "http://localhost:18080/api/skills/resolve?query=应收合同的原始金额"
```

### 查询子表

```bash
curl "http://localhost:18080/api/skills/resolve?query=应收合同的子表"
```

### 精确英文名匹配

```bash
curl "http://localhost:18080/api/skills/resolve?query=ArContract"
```

### 只查对象不查字段

```bash
curl "http://localhost:18080/api/skills/resolve?query=采购订单&includeFields=false"
```

## 响应结构

返回分层结构：`objectMatches` → 每个对象下嵌套 `fieldMatches`。

每个匹配项带 `score`（0.0~1.0）和 `matchSource` 说明匹配来源。
