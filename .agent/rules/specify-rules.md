# deepModel Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-05-09

## Active Technologies
- Java 8, Spring Boot 2.7.x + MyBatis, Jackson, JSqlParser（已有） (002-metadata-services)
- PostgreSQL（`baseapp_object_field` + `baseapp_object_type` 表） (002-metadata-services)
- Java 8, Spring Boot 2.7.x + Spring Web (REST Controller), Guava Cache, ImpactAnalyzerService（已有内存索引） (004-agent-metadata-matching)
- N/A（纯内存匹配，复用已加载的元数据） (004-agent-metadata-matching)

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
- 004-agent-metadata-matching: Added Java 8, Spring Boot 2.7.x + Spring Web (REST Controller), Guava Cache, ImpactAnalyzerService（已有内存索引）
- 002-metadata-services: Added Java 8, Spring Boot 2.7.x + MyBatis, Jackson, JSqlParser（已有）

- 001-native-expression-engine: Added Java 8 / Spring Boot 2.7 / MyBatis + JSqlParser (已有), OkHttp3 (待消除对回写SQL的依赖), Jackson, Guava Cache

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
