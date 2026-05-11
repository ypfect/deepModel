# Quickstart: 增强 Resolve 元数据匹配接口

## 构建

```bash
cd /Users/pengfyu/advance/deepModel
mvn compile -pl . -q
```

## 运行

```bash
mvn spring-boot:run
```

## 验证新功能

### 1. 对象特性筛选

```bash
# 查找树型对象
curl -s "http://localhost:8080/api/skills/resolve?query=树型对象&maxResults=10" | jq '.data.objectMatches[] | {objectType, title, isTree}'

# 查找支持变更单的单据
curl -s "http://localhost:8080/api/skills/resolve?query=支持变更单的单据" | jq '.data.objectMatches[] | {objectType, title, isSupportChangeLog}'
```

### 2. bizType 维度匹配

```bash
# 查找金额字段
curl -s "http://localhost:8080/api/skills/resolve?query=应收合同的金额字段" | jq '.data.objectMatches[0].fieldMatches[] | select(.bizType == "amount") | {field, title, bizType}'

# 查找邮箱字段
curl -s "http://localhost:8080/api/skills/resolve?query=邮箱字段" | jq '.data.objectMatches[].fieldMatches[] | select(.bizType == "email") | {field, title}'
```

### 3. 枚举搜索

```bash
# 搜索审批状态枚举
curl -s "http://localhost:8080/api/skills/resolve?query=审批状态" | jq '.data.enumMatches'

# 查看枚举值和使用该枚举的字段
curl -s "http://localhost:8080/api/skills/resolve?query=ApproveStatus" | jq '.data.enumMatches[0] | {enumType, values, usedByFields}'
```

### 4. 表达式依赖摘要

```bash
# 查看字段的依赖摘要
curl -s "http://localhost:8080/api/skills/resolve?query=ArContractSubjectMatterItem quantity" | jq '.data.objectMatches[0].fieldMatches[] | select(.field == "quantity") | {field, dependedByCount, dependedByFields, writeBackSource}'
```

### 5. 运行测试

```bash
mvn test -pl . -q
```
