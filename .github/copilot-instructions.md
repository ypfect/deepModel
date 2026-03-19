# Project Guidelines

## Code Style
- Use Java 8 compatible syntax and APIs. Keep Spring Boot at 2.7.x conventions.
- Keep layering strict: controllers handle HTTP and validation, services hold business logic, DAO/Mapper handles persistence.
- Prefer updating existing patterns in `com.deepmodel.relation` instead of introducing new frameworks.
- Respect existing MyBatis naming strategy (`map-underscore-to-camel-case: true`) and keep model fields camelCase.

## Architecture
- Primary truth source for field lineage is PostgreSQL metadata tables (especially `baseapp_object_field`).
- Neo4j is an auxiliary graph/search capability, not the primary source of dependency truth.
- Main modules:
  - `controller`: REST endpoints (`/api/impact`, `/api/skills`, projection/debug endpoints)
  - `service`: graph traversal, parsing, profile/health/projection logic
  - `dao` + `resources/mapper/*.xml`: SQL access
  - `resources/static`: lightweight analysis UIs (`modern.html`, `neo4j.html`, `projection.html`)

## Build and Test
- Run app: `mvn -q -DskipTests spring-boot:run` (default port 18080)
- Build artifact: `mvn clean package -DskipTests`
- Run tests: `mvn test`
- After metadata changes in DB, refresh in-memory index via `GET /api/reload` before validating impact-analysis behavior.

## Conventions
- Always identify a field with both object type and field name in analysis workflows.
- Be explicit with traversal filters: relation type (`intra` / `writeBack` / `view`) and direction (`upstream` / `downstream`).
- Keep traversal depth conservative by default (`depth=3`) to avoid graph explosion on large models.
- For expression parsing, preserve existing fallback behavior (SQL parse first, regex fallback when needed).
- Avoid editing generated/build outputs under `target/`; edit sources in `src/` only.

## Security and Environment
- Treat `src/main/resources/application.yml` as environment-specific. Do not introduce new hardcoded credentials.
- Prefer environment variables or profile-based overrides for new connection settings.
- Avoid logging secrets or full credential-bearing URLs.

## Key References
- `README.md`: local bootstrap and minimal API usage.
- `docs/SKILLS_SUMMARY.md`: Skills API and analysis capability details.
- `docs/task.md.resolved`: Neo4j integration and implementation notes.
