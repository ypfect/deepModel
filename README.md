# 模型字段影响范围服务

Java 8 / Spring Boot 2.7 / MyBatis / PostgreSQL

## 启动
1. 准备数据库表（最小列）：
```sql
create table if not exists baseapp_object_field (
  id bigserial primary key,
  object_type varchar(128) not null,
  name varchar(128) not null,
  api_name varchar(128),
  title varchar(256),
  type varchar(64),
  biz_type varchar(64),
  expression text,
  trigger_expr text,
  virtual_expr text,
  write_back_expr jsonb
);
create index if not exists idx_bof_object_type on baseapp_object_field(object_type);
```
2. 配置 `src/main/resources/application.yml` 的数据源。
3. 启动：
```bash
./mvnw spring-boot:run
# or
mvn -q -DskipTests spring-boot:run
```

## API
- GET `/api/impact?objectType=ArReceipt&field=originAmount&depth=3`
- GET `/api/impact/mermaid?objectType=ArReceipt&field=originAmount&depth=3`
- GET `/api/impact/dot?objectType=ArReceipt&field=originAmount&depth=3`

## 说明
- 同对象内依赖：解析目标字段的 trigger/expression/virtual 表达式中对起点字段的引用。
- 跨对象依赖：writeBackExpr 命中当前对象，且 expression 引用了起点字段。
- 递归：BFS 扩展 3 层，去重避免环。
