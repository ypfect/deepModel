# deepModel Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-05-29

## Active Technologies
- Java 8, Spring Boot 2.7.x + MyBatis, Jackson, JSqlParser（已有） (002-metadata-services)
- PostgreSQL（`baseapp_object_field` + `baseapp_object_type` 表） (002-metadata-services)
- Java 8, Spring Boot 2.7.x + Spring Web (REST Controller), Guava Cache, ImpactAnalyzerService（已有内存索引） (004-agent-metadata-matching)
- N/A（纯内存匹配，复用已加载的元数据） (004-agent-metadata-matching)
- Java 8, Spring Boot 2.7.x, MyBatis + Jieba（已引入 com.huaban.analysis.jieba）、Guava Cache、Jackson (005-enhance-metadata-resolve)
- PostgreSQL（baseapp_object_field / baseapp_object_type / baseapp_system_metadata） (005-enhance-metadata-resolve)
- Java 8, Spring Boot 2.7.x, MyBatis + Jackson (JSON 解析), Guava (Cache), JSqlParser (006-enhance-metadata-resolve)
- PostgreSQL（baseapp_system_metadata / baseapp_object_type / baseapp_object_field） (006-enhance-metadata-resolve)
- Java 8, Spring Boot 2.7.x + Spring Web, Jackson, GraphQL HTTP Client（现有） (009-remove-unused-features)
- 元数据仅经 GraphQL 拉取；移除本地 PostgreSQL 直连路径 (009-remove-unused-features)

- Java 8 / Spring Boot 2.7 / MyBatis + JSqlParser (已有), OkHttp3 (待消除对回写SQL的依赖), Jackson, Guava Cache (001-native-expression-engine)

## Project Structure

```text
src/
tests/
```

## Commands

# Add commands for Java 8 / Spring Boot 2.7 / MyBatis

## Code Style

Java 8 / Spring Boot 2.7 / MyBatis: Follow standard conventions

## Recent Changes
- 009-remove-unused-features: Added Java 8, Spring Boot 2.7.x + Spring Web, Jackson, GraphQL HTTP Client（现有）
- 006-enhance-metadata-resolve: Added Java 8, Spring Boot 2.7.x, MyBatis + Jackson (JSON 解析), Guava (Cache), JSqlParser
- 005-enhance-metadata-resolve: Added Java 8, Spring Boot 2.7.x, MyBatis + Jieba（已引入 com.huaban.analysis.jieba）、Guava Cache、Jackson


<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
