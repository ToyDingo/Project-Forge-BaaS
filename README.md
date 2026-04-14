# Forge Backend

Cloud-hosted game backend (MVP): Steam-based authentication, Forge API keys per game, and RS256 JWT access tokens.

This repository contains:

- `db/` — hand-applied SQL migrations (also mirrored under `server/src/main/resources/db/migration` for Flyway).
- `server/` — Spring Boot 3 application (Java 21 toolchain via Gradle; auto-provisioned if missing).

Cloud deployment notes: see [CloudMigration.md](CloudMigration.md).

## Prerequisites

- **JDK 21+** (Gradle may download JDK 21 via the Foojay toolchain resolver on first build).
- **PostgreSQL** for local runs (or point `FORGE_DATASOURCE_URL` at your instance).

## Database setup

1. Create a database and user (example: `forge` / `forge`).
2. Apply root SQL scripts in order, **or** run the app once and let **Flyway** apply `server/src/main/resources/db/migration` automatically.

Root scripts:

- [db/001_create_games_and_players.sql](db/001_create_games_and_players.sql)
- [db/002_games_steam_and_api_key_lookup.sql](db/002_games_steam_and_api_key_lookup.sql)

If you applied `001` manually before adding Flyway, baseline Flyway or continue with manual `002` only—do not duplicate migrations.

### Seeding a dev `games` row

Each game needs:

- `name`
- `api_key_lookup_hash` — **SHA-256 (hex, lowercase)** of the raw Forge API key (see `ForgeApiKeyHasher` in the server).
- `api_key_hash` — **BCrypt** hash of the same raw Forge API key (Spring `BCryptPasswordEncoder`).
- `steam_app_id` — Steamworks App ID.
- `steam_web_api_key` — that title’s Steam Web API key (server-side only; never ship to clients).

You can compute hashes in a REPL or a small Java `main` using the same algorithms as production.

## Run the server

```bash
cd server
./gradlew.bat bootRun   # Windows
# ./gradlew bootRun     # macOS / Linux
```

Environment variables (optional overrides):

| Variable | Purpose |
|----------|---------|
| `FORGE_DATASOURCE_URL` | JDBC URL (default `jdbc:postgresql://localhost:5432/app`) |
| `FORGE_DATASOURCE_USERNAME` | DB user |
| `FORGE_DATASOURCE_PASSWORD` | DB password |
| `FORGE_JWT_ISSUER` | JWT `iss` claim |
| `FORGE_JWT_AUDIENCE` | JWT `aud` claim |
| `FORGE_JWT_PRIVATE_KEY_PEM` | Path or `classpath:` to PEM **private** key |
| `FORGE_JWT_PUBLIC_KEY_PEM` | Path or `classpath:` to PEM **public** key |
| `FORGE_STEAM_DEV_STUB_ENABLED` | Set to `true` **for local testing only** — skips real Steam HTTP calls and uses `DevOnlySteamClientStub` (see class Javadoc). **Do not enable in production.** |

### DEV ONLY: Steam stub (Postman / local)

Set `FORGE_STEAM_DEV_STUB_ENABLED=true` (or `forge.steam.dev-stub-enabled=true` in YAML) so the app uses `DevOnlySteamClientStub` instead of `SteamWebApiClient`. The stub returns a **successful** `SteamTicketValidationResult` when `steam_ticket` is a **hex string** of at least **16** characters; otherwise it returns an invalid ticket. A fixed SteamID64 is used on success. **Remove reliance on this before shipping real Steam validation.** Logs emit a warning on startup when the stub is active.

Dev defaults point at `classpath:keys/dev-private.pem` and `classpath:keys/dev-public.pem`. Regenerate locally if needed:

```bash
cd server
java scripts/GenerateJwtKeys.java
```

## Authenticate (Steam ticket exchange)

Request:

- Header: `X-Forge-Api-Key: <your raw Forge API key>`
- Body JSON: `{ "steam_ticket": "<hex or opaque ticket from Steamworks client>" }`

```bash
curl -sS -X POST "http://localhost:8080/v1/auth/steam" ^
  -H "Content-Type: application/json" ^
  -H "X-Forge-Api-Key: YOUR_RAW_FORGE_API_KEY" ^
  -d "{\"steam_ticket\":\"deadbeef\"}"
```

Success (`200`):

```json
{
  "access_token": "...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

### Error codes (subset)

| HTTP | `error.code` | When |
|------|----------------|------|
| 400 | `FORGE_INVALID_REQUEST` | Bad JSON or missing `steam_ticket` |
| 401 | `FORGE_GAME_NOT_FOUND` | Unknown or invalid Forge API key |
| 401 | `STEAM_VALIDATION_FAILED` | Steam rejected the ticket |
| 401 | `FORGE_INVALID_TOKEN` | Bad/expired bearer JWT on protected routes |
| 422 | `FORGE_GAME_MISCONFIGURED` | Game row missing Steam App ID / Web API key |
| 503 | `STEAM_UNAVAILABLE` | Network/HTTP failure talking to Steam |

## Protected routes (JWT)

After login, call APIs with:

`Authorization: Bearer <access_token>`

Example:

```bash
curl -sS "http://localhost:8080/v1/me" -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

## Tests

```bash
cd server
./gradlew.bat test
```

Integration tests use an in-memory H2 database (`application-test.yml`) and mock the Steam client.

## License

Proprietary / not for distribution unless you add a license.
