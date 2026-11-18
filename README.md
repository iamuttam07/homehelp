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

## API

Errors are returned as [problem details](https://www.rfc-editor.org/rfc/rfc9457) (`application/problem+json`).

### Register

`POST /api/auth/register`

```json
{
  "email": "asha.patel@example.com",
  "password": "correct-horse-1",
  "fullName": "Asha Patel",
  "phone": "+447700900123",
  "role": "CUSTOMER"
}
```

- `role` is `CUSTOMER` or `PROVIDER`. Admin accounts can't be self-registered.
- `password` is 8 to 72 characters and is stored as a BCrypt hash.
- `phone` is optional, in international format.
- Emails are case-insensitive and stored in lower case.

| Response | When |
|---|---|
| `201 Created` with the new user (no password) | Success |
| `400 Bad Request` | Missing or invalid fields |
| `409 Conflict` | An account with this email already exists |

### Log in

`POST /api/auth/login` with `{"email": "...", "password": "..."}` returns:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "refreshToken": "q3Jt...",
  "refreshExpiresIn": 1209600
}
```

Send the access token on every other request:

```
Authorization: Bearer <accessToken>
```

Wrong email or password returns `401` (the response doesn't say which).

### Refresh and log out

| Endpoint | Body | Result |
|---|---|---|
| `POST /api/auth/refresh` | `{"refreshToken": "..."}` | `200` with a new access **and** refresh token. The old refresh token stops working. |
| `POST /api/auth/logout` | `{"refreshToken": "..."}` | `204`. The refresh token is revoked. |

Refresh tokens are single-use. Reusing an old one revokes all of that user's sessions, so they must log in again.

### Current user

| Endpoint | Body | Result |
|---|---|---|
| `GET /api/users/me` | | The logged-in user |
| `PUT /api/users/me` | `{"fullName": "...", "phone": "+44..."}` | Updated user. Omitting `phone` removes it. Email and role can't be changed here. |

### Addresses

Any logged-in user can manage their own addresses, up to **10** per user (adding an 11th returns `409 Address limit reached`). Other users' addresses always return `404`.

| Endpoint | Result |
|---|---|
| `GET /api/users/me/addresses` | All addresses, default first |
| `POST /api/users/me/addresses` | `201` with the new address. The first address becomes the default. |
| `GET /api/users/me/addresses/{id}` | One address |
| `PUT /api/users/me/addresses/{id}` | Replaces the address fields |
| `PUT /api/users/me/addresses/{id}/default` | Makes it the default (the previous default stops being one) |
| `DELETE /api/users/me/addresses/{id}` | `204`. If it was the default, the oldest remaining address becomes the default. |

```json
{
  "label": "Home",
  "line1": "12 Brick Lane",
  "line2": "Flat 3",
  "city": "London",
  "postcode": "E1 6AN",
  "countryCode": "GB",
  "latitude": 51.5202,
  "longitude": -0.0716
}
```

`label` and `line2` are optional. The client supplies the coordinates (for example from a map). The database derives a PostGIS `location` from them for distance queries (see [ADR 0004](docs/adr/0004-store-coordinates-with-generated-postgis-location.md)).

### Provider profile

Providers only (`/api/provider/**`). A profile is created automatically when someone registers as a provider.

| Endpoint | Body | Result |
|---|---|---|
| `GET /api/provider/profile` | | The provider's profile |
| `PUT /api/provider/profile` | `{"bio": "...", "yearsOfExperience": 12}` | Updated profile. `bio` up to 1000 characters, experience 0 to 60. |

### Access rules

| Path | Who |
|---|---|
| `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`, `/actuator/health`, `/actuator/info` | Anyone |
| `/api/admin/**` | `ADMIN` only |
| `/api/provider/**` | `PROVIDER` only |
| Everything else | Any logged-in user |

Without a token you get `401`; with a token but the wrong role, `403`.
Access tokens last 15 minutes and refresh tokens 14 days (see [ADR 0003](docs/adr/0003-jwt-access-and-rotating-refresh-tokens.md)).

### Configuration

| Variable | Default | Notes |
|---|---|---|
| `JWT_SECRET` | a development-only value | **Set this outside local development.** At least 32 bytes, e.g. `openssl rand -base64 48`. |
| `JWT_ACCESS_TOKEN_TTL` | `15m` | |
| `JWT_REFRESH_TOKEN_TTL` | `14d` | |

## Project layout

```
src/main/java/com/uel/homehelp/   application code, one package per feature
  auth/                           login, tokens, security rules
  user/                           accounts, registration, current user
  address/                        user addresses
  provider/                       provider profiles
  common/                         small shared helpers
src/main/resources/               configuration
src/main/resources/db/migration/  Flyway SQL migrations
src/test/java/com/uel/homehelp/   tests
compose.yaml                      local Postgres, Redis, MailHog
.github/workflows/ci.yml          CI build and tests
.github/ISSUE_TEMPLATE/           issue templates (plus PR template in .github/)
docs/adr/                         architecture decision records
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for setup, branch naming, and the migration rules.
Design decisions are recorded in [docs/adr](docs/adr).

## License

[MIT](LICENSE)
