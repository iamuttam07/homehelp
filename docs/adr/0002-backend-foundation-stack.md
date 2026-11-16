# 2. Backend foundation stack

- **Status:** Accepted
- **Date:** 2026-11-16

## Context

HomeHelp is a home services marketplace: customers book services, and nearby verified providers accept and complete the jobs. The backend needs a relational model (users, bookings, payments), location-based matching, short-lived slot holds, and email notifications. It is a learning project, so the stack should be mainstream, well documented, and easy to run on a laptop.

## Decision

- **Language and framework:** Java 21 (LTS) with Spring Boot 4, built with Maven through the included wrapper (`./mvnw`).
- **Repository layout:** this repository contains the backend API only. The web frontend lives in its own repository and talks to the API over HTTP.
- **Database:** PostgreSQL with the PostGIS extension, for relational data plus distance queries in provider matching.
- **Schema management:** Flyway owns the schema through versioned SQL migrations in `src/main/resources/db/migration`. Hibernate runs with `ddl-auto=validate` and never changes the schema. Merged migrations are never edited.
- **Cache and short-lived state:** Redis, for slot holds with a timeout and caching.
- **Local infrastructure:** Docker Compose (`compose.yaml`) runs Postgres + PostGIS, Redis, and MailHog (which catches outgoing email).
- **Testing:** integration tests start a throwaway PostGIS container with Testcontainers and `@ServiceConnection`, so tests run against the same database engine as production.
- **Continuous integration:** GitHub Actions runs `./mvnw verify` on Java 21 for every push to `main` and every pull request.

## Consequences

- Spring Boot and PostgreSQL are widely used, so documentation and examples are easy to find.
- PostGIS keeps location queries inside the database, without a separate search service.
- Flyway makes schema changes explicit and reviewable, but every change needs a new migration file.
- Testcontainers makes tests realistic, but requires Docker to run them. On Apple Silicon, the `postgis/postgis` image runs under emulation and is slower.
- Spring Boot 4 is recent, so some third-party libraries and guides may still target Boot 3, and APIs may need adapting.
- Splitting frontend and backend into separate repositories keeps each one simple, but API changes must be coordinated across the two.
