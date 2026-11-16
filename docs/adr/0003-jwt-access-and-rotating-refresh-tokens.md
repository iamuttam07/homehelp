# 3. JWT access tokens with rotating refresh tokens

- **Status:** Accepted
- **Date:** 2026-11-16

## Context

The API is used by a separate web frontend (and later possibly mobile apps), so it needs stateless authentication that works across origins. Sessions and cookies would tie the API to one frontend and require CSRF protection. Users should stay logged in for days, but a stolen credential should be useful only briefly.

## Decision

- **Access tokens** are JWTs signed with HS256 using a shared secret (`JWT_SECRET`), valid for 15 minutes. Claims: `sub` (user id), `email`, `roles`, `iss`, `iat`, `exp`, `jti`. Clients send them as `Authorization: Bearer <token>`.
- **Refresh tokens** are random 256-bit opaque strings, valid for 14 days. Only their SHA-256 hash is stored, in the `refresh_tokens` table.
- **Rotation:** each refresh token can be used once. `POST /api/auth/refresh` revokes it and returns a new pair. Presenting a revoked token again is treated as theft, and all of that user's refresh tokens are revoked.
- **Logout** revokes the given refresh token. Access tokens can't be revoked; they simply expire.
- **Implementation:** Spring Security's OAuth2 resource server with Nimbus (`NimbusJwtEncoder` / `NimbusJwtDecoder`), rather than a separate JWT library. Login, refresh, and logout are plain controllers in the `auth` package.
- **Roles** come from the `roles` claim and map to `ROLE_*` authorities. Coarse rules are URL-based (`/api/admin/**` for admins, `/api/provider/**` for providers, everything else under `/api` for any logged-in user). `@PreAuthorize` is enabled for finer, per-method rules.

## Consequences

- No server-side session lookup per request; any API instance can verify a token with the shared secret.
- A leaked access token stays valid for up to 15 minutes. Changing a user's role takes effect at their next refresh.
- HS256 means every service that verifies tokens holds the signing secret. If other services need to verify tokens later, switch to an asymmetric key (RS256/ES256) in a new ADR.
- Revoked and expired refresh tokens accumulate in the table; a scheduled clean-up job will be needed later.
- The frontend must store the refresh token safely and refresh before the access token expires.
