# Quickstart: 本地化表达式解析引擎

## 前提条件

- Java 8+, Maven
- PostgreSQL 数据库 (含 `baseapp_object_field` 表)

## 快速验证

### 1. 配置本地回写模式

在 `application.yml` 中添加:

```yaml
expression-engine:
  local-writeback-sql: true   # 启用本地回写 SQL 生成
```

### 2. 验证升级脚本生成

调用升级脚本生成 API，确认回写字段的 SQL 不再通过 HTTP 获取:

```bash
# 请求升级脚本 (含回写字段)
curl -X POST http://localhost:8080/api/upgrade/generate \
  -H "Content-Type: application/json" \
  -d '{"rootObject": "ArContract", "rootField": "invoicedAmount"}'
```

### 3. 验证本地 vs HTTP 结果一致性

```bash
# 本地模式
curl 'http://localhost:8080/api/upgrade/generate?mode=local' ...

# HTTP 模式 (需配置 writeback-sql.api-url)
curl 'http://localhost:8080/api/upgrade/generate?mode=http' ...

# 对比两个结果应完全一致
```

### 4. 运行单元测试

```bash
mvn test -pl . -Dtest="WriteBackSqlGeneratorTest"
```
