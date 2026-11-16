# Contributing to HomeHelp

Thanks for your interest in HomeHelp. This is a learning-focused project, so questions, small fixes, and documentation improvements are just as welcome as features.

This repository is the **backend API** only. The web frontend lives in its own repository.

## Getting set up

You need:

- Java 21
- Docker (Desktop) running

```bash
git clone https://github.com/iamuttam07/homehelp.git
cd homehelp
cp .env.example .env
docker compose up -d        # Postgres + PostGIS, Redis, MailHog
./mvnw spring-boot:run      # API on http://localhost:8080
./mvnw verify               # build and run all tests
```

Tests don't use the `docker compose` database. They start their own throwaway PostGIS container with Testcontainers, so Docker must be running.

> On Apple Silicon, the `postgis/postgis` image runs under emulation (no arm64 build). It works, just a bit slower.

## Workflow

1. **Open or pick an issue** first for anything bigger than a typo, so we can agree on the approach.
2. **Create a branch** from `main`:
   - `feat/<short-description>` for new features
   - `fix/<short-description>` for bug fixes
   - `docs/`, `chore/`, `test/`, `refactor/` for everything else
3. **Make your change** with tests.
4. **Run `./mvnw verify`** locally before pushing.
5. **Open a pull request** against `main` and fill in the template.
6. **CI must pass** and the PR needs a review before it is merged.

Keep pull requests small and focused on one thing. Several small PRs are easier to review than one large one.

## Commit messages

Write commit messages in the imperative mood, with a short summary line (about 72 characters or less):

```
Add registration endpoint with email validation

Optional longer explanation of what changed and why.
```

## Code guidelines

- **Package by feature**, not by layer: `com.uel.homehelp.booking`, `com.uel.homehelp.catalog`, and so on.
- **Validate input** at the API boundary with Bean Validation (`@Valid`, `@NotBlank`, ...).
- **Never log secrets** or personal data (passwords, tokens, full addresses).
- **Tests:** every new feature or bug fix comes with tests. Integration tests import `TestcontainersConfiguration`.

## Database migrations

Flyway owns the database schema. Hibernate only validates against it (`ddl-auto=validate`).

- Add migrations to `src/main/resources/db/migration` as `V<next number>__<description>.sql`, for example `V2__create_users.sql`.
- **Never edit a migration that has been merged to `main`.** Fix mistakes with a new migration.
- Check for a version-number clash with other open PRs before merging.

## Architecture decisions

Significant design decisions are recorded as ADRs in [`docs/adr`](docs/adr). If your change introduces a new technology, pattern, or trade-off, add an ADR in the same PR. See [ADR 0001](docs/adr/0001-record-architecture-decisions.md) for the format.

## Reporting bugs and security issues

- **Bugs and feature ideas:** open a GitHub issue using one of the templates.
- **Security problems:** please don't open a public issue. Contact the maintainer privately through their GitHub profile.

## License

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE).
