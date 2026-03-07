# AGENTS.md

## Cursor Cloud specific instructions

### Project overview

DeepModel Insight — a field-level data lineage and impact analysis service for ERP/business object models. Single Spring Boot 2.7 application (Java 8, MyBatis, PostgreSQL, Neo4j).

### Required services

| Service | Port | Notes |
|---------|------|-------|
| PostgreSQL | 5432 | Database `testapp`, user `postgres`, password `123`. Tables must exist (see README). |
| Neo4j 4.4 | 7687 (bolt), 7474 (http) | User `neo4j`, password `ypfect93`. Requires JDK 11 (`JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 neo4j start`). |
| Spring Boot app | 18080 | `JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn -q -DskipTests spring-boot:run` |

### Startup caveats

- **JDK version conflict**: The app requires JDK 8 (`JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64`), but Neo4j 4.4 requires JDK 11. Keep JDK 8 as the system default and start Neo4j explicitly with `sudo JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 neo4j start`.
- **PostgreSQL must be started first**: `sudo pg_ctlcluster 16 main start`
- **Neo4j driver bean is unconditional**: The `Neo4jConfig` creates a `Driver` bean at startup. Neo4j must be reachable or the app may fail on graph-search endpoints.
- **No automated tests exist** in the repository. `mvn test` passes (zero tests).
- **No linter/checkstyle** is configured.
- **Static HTML UI** is served at `http://localhost:18080/` — no frontend build step.

### Build & run (see also README)

```bash
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
mvn -q -DskipTests spring-boot:run
```

### Key API endpoints

- `GET /api/impact?objectType=ArReceipt&field=originAmount&depth=3` — JSON dependency graph
- `GET /api/impact/mermaid?objectType=...&field=...&depth=3` — Mermaid diagram
- `GET /api/impact/dot?objectType=...&field=...&depth=3` — DOT graph
