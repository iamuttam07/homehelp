# HomeHelp Backend

[![CI](https://github.com/iamuttam07/homehelp/actions/workflows/ci.yml/badge.svg)](https://github.com/iamuttam07/homehelp/actions/workflows/ci.yml)

The API for **HomeHelp**, an open-source, learning-focused home services marketplace. Customers book services such as cleaning, plumbing, or salon-at-home, and verified professionals accept and complete the jobs.

> **Status:** early development. Not production-ready.
> HomeHelp is an independent educational project. It is not affiliated with any commercial service marketplace.
> The web frontend lives in a separate repository (coming later).

## Tech stack

- Java 21, Spring Boot 4, Maven (wrapper included)
- PostgreSQL + PostGIS, Redis
- Docker Compose for local infrastructure

## Planned features

- Customer booking flow (browse, choose a slot, book, track)
- Provider matching and dispatch (geo-based)
- Payments and payouts (test mode only)
- Ratings, reviews, and notifications

## Getting started

### Prerequisites

- Java 21
- Docker (Desktop) running — used for local services and by the tests

### 1. Start local infrastructure

```bash
cp .env.example .env
docker compose up -d
```

| Service | Purpose | Default address |
|---|---|---|
| PostgreSQL + PostGIS | Main database | `localhost:5432` |
| Redis | Slot holds, caching | `localhost:6379` |
| MailHog | Catches outgoing emails | SMTP `localhost:1025`, UI http://localhost:8025 |

Stop with `docker compose down` (add `-v` to wipe data).

### 2. Run the API

```bash
./mvnw spring-boot:run      # http://localhost:8080
./mvnw test                 # run tests
```

The app connects to the Postgres started by `docker compose` and Flyway applies any pending migrations on startup.
Check it is running: http://localhost:8080/actuator/health should return `{"status":"UP"}`.

Tests don't need `docker compose`: they start their own throwaway PostGIS container via Testcontainers.

### Continuous integration

GitHub Actions (`.github/workflows/ci.yml`) runs `./mvnw verify` on every push to `main` and on every pull request, using Java 21. Test reports are uploaded as an artifact when a run fails.

### Database migrations

Schema changes live in `src/main/resources/db/migration` as versioned SQL files (`V1__...sql`, `V2__...sql`).
Never edit a migration after it has been committed; add a new one instead.

## Project layout

```
src/main/java/com/uel/homehelp/   application code (feature modules will live here)
src/main/resources/               configuration
src/main/resources/db/migration/  Flyway SQL migrations
src/test/java/com/uel/homehelp/   tests
compose.yaml                      local Postgres, Redis, MailHog
.github/workflows/ci.yml          CI build and tests
```

## License

[MIT](LICENSE)
