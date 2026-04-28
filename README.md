# Forge Backend

Cloud-hosted game backend (MVP): Steam-based authentication, Forge API keys per game, RS256 JWT access tokens, and competitive leaderboards with dual-client match reconciliation.

This repository contains:

- `db/` — hand-applied SQL migrations (also mirrored under `server/src/main/resources/db/migration` for Flyway).
- `server/` — Spring Boot 3 application (Java 21 toolchain via Gradle; auto-provisioned if missing).
- `client/godot/` — Godot 4.3 host project for the Forge GDScript SDK addon (`addons/forge_sdk/`), a headless test runner, and the manual cockpit harness.

Code layout: see [CodeStructure.md](documentation/foundations/CodeStructure.md).
Leaderboard implementation details: see [LeaderboardVerticalSlice.md](documentation/slices/LeaderboardVerticalSlice.md).
Matchmaking implementation details: see [MatchmakingVerticalSlice.md](documentation/slices/MatchmakingVerticalSlice.md).
GDScript SDK quickstart (Godot 4.3 addon): see [client/godot/addons/forge_sdk/README.md](client/godot/addons/forge_sdk/README.md).
Summary of everything shipped so far: see [WhatWasImplemented.md](documentation/foundations/WhatWasImplemented.md).
Current decision snapshot: see [FreezeNowDeferSafely.md](documentation/decisions/FreezeNowDeferSafely.md).
Cloud deployment notes: see [CloudMigration.md](documentation/architecture/CloudMigration.md).
Local end-to-end validation guide: see [FORGE_LOCAL_VALIDATION_GUIDE.md](documentation/local-testing/FORGE_LOCAL_VALIDATION_GUIDE.md). Local validation completed and passed on 2026-04-28; cloud handoff is the next phase.

## Prerequisites

- **JDK 21+** (Gradle may download JDK 21 via the Foojay toolchain resolver on first build).
- **PostgreSQL** for local runs (or point `FORGE_DATASOURCE_URL` at your instance).

## Database setup

1. Create a database and user (example: `forge` / `forge`).
2. Apply root SQL scripts in order, **or** run the app once and let **Flyway** apply `server/src/main/resources/db/migration` automatically.

Root scripts:

- [db/001_create_games_and_players.sql](db/001_create_games_and_players.sql)
- [db/002_games_steam_and_api_key_lookup.sql](db/002_games_steam_and_api_key_lookup.sql)
- [db/003_leaderboard_match_reports_and_player_stats.sql](db/003_leaderboard_match_reports_and_player_stats.sql)
- [db/004_completed_matches_idempotency.sql](db/004_completed_matches_idempotency.sql)
- [db/005_matchmaking_tables.sql](db/005_matchmaking_tables.sql)

If you applied earlier scripts manually before adding Flyway, baseline Flyway or continue with manual scripts only—do not duplicate migrations.

### Seeding a dev `games` row

Each game needs:

- `name`
- `api_key_lookup_hash` — **SHA-256 (hex, lowercase)** of the raw Forge API key (see `ForgeApiKeyHasher` in the server).
- `api_key_hash` — **BCrypt** hash of the same raw Forge API key (Spring `BCryptPasswordEncoder`).
- `steam_app_id` — Steamworks App ID.
- `steam_web_api_key` — that title’s Steam Web API key (server-side only; never ship to clients).

You can compute hashes in a REPL or a small Java `main` using the same algorithms as production.

Or use the built-in Gradle task:

```bash
cd server
./gradlew.bat seedDevGame --args="my-raw-key"
```

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

Set `FORGE_STEAM_DEV_STUB_ENABLED=true` (or `forge.steam.dev-stub-enabled=true` in YAML) so the app uses `DevOnlySteamClientStub` instead of `SteamWebApiClient`. The stub returns a **successful** `SteamTicketValidationResult` when `steam_ticket` is a **hex string** of at least **16** characters; otherwise it returns an invalid ticket. In dev mode, the stub deterministically generates a mock SteamID64 from the ticket value (same ticket -> same SteamID, different ticket -> different SteamID) so local leaderboard testing can create multiple players. **Remove reliance on this before shipping real Steam validation.** Logs emit a warning on startup when the stub is active.

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

### Error codes

| HTTP | `error.code` | When |
|------|----------------|------|
| 400 | `FORGE_INVALID_REQUEST` | Bad JSON or missing `steam_ticket` |
| 400 | `LEADERBOARD_INVALID_RESULT` | Match result payload invalid (same winner/loser, reporter not a participant) |
| 401 | `FORGE_GAME_NOT_FOUND` | Unknown or invalid Forge API key |
| 401 | `STEAM_VALIDATION_FAILED` | Steam rejected the ticket |
| 401 | `FORGE_INVALID_TOKEN` | Bad/expired bearer JWT on protected routes |
| 404 | `LEADERBOARD_PLAYER_NOT_FOUND` | No leaderboard entry for this player |
| 409 | `LEADERBOARD_DUPLICATE_REPORT` | Player already reported for this match |
| 409 | `MATCHMAKING_ALREADY_QUEUED` | Player already has an active queue ticket for this mode |
| 422 | `FORGE_GAME_MISCONFIGURED` | Game row missing Steam App ID / Web API key |
| 503 | `STEAM_UNAVAILABLE` | Network/HTTP failure talking to Steam |

Terminal matchmaking states are delivered as WebSocket events (not HTTP errors):

- `MATCHMAKING_QUEUE_TIMEOUT` - queue timeout reached, delivered in the `queue_timeout` event
- `MATCHMAKING_MATCH_CANCELLED` - match cancelled after notification retries, returned via the status endpoint

## Protected routes (JWT)

After login, call APIs with:

`Authorization: Bearer <access_token>`

Example:

```bash
curl -sS "http://localhost:8080/v1/me" -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

## Leaderboard API

All leaderboard endpoints require a valid JWT (`Authorization: Bearer <access_token>`).
The game context is derived from the JWT claims.

### Report a match result

Each match has two participants. Both must report independently. Stats are only
updated after both reports arrive.

After reconciliation, transient rows in `match_reports` are deleted. Replay prevention is enforced by a `completed_matches` tombstone table with unique `(game_id, match_id)` so the same match cannot be scored twice.

```bash
curl -sS -X POST "http://localhost:8080/v1/leaderboard/results" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -d "{\"match_id\":\"<uuid>\",\"winner_id\":\"<uuid>\",\"loser_id\":\"<uuid>\"}"
```

First report response (`200`):

```json
{
  "status": "pending",
  "message": "Match report recorded; waiting for the other player's report."
}
```

Second report (reconciles stats, `200`):

```json
{
  "status": "completed",
  "message": "Match reconciled and stats updated"
}
```

### Get leaderboard (paginated)

Returns top players ranked by wins (dense rank). Page size capped at 10.

```bash
curl -sS "http://localhost:8080/v1/leaderboard/top?page=1&size=10" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

Response (`200`):

```json
{
  "page": 1,
  "size": 10,
  "total_players": 42,
  "total_pages": 5,
  "items": [
    { "rank": 1, "player_id": "...", "display_name": null, "wins": 15, "losses": 2 },
    { "rank": 2, "player_id": "...", "display_name": null, "wins": 12, "losses": 3 }
  ]
}
```

### Get a player's rank

```bash
curl -sS "http://localhost:8080/v1/leaderboard/rank/<player-uuid>" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

Response (`200`):

```json
{
  "rank": 5,
  "player_id": "...",
  "display_name": null,
  "wins": 8,
  "losses": 4
}
```

## Matchmaking API

All matchmaking endpoints require a valid JWT (`Authorization: Bearer <access_token>`).
Game and player identity are taken from JWT claims.

The happy path for a client is 4 operations total:

1. `POST /v1/matchmaking/queue` - join the queue.
2. `POST /v1/matchmaking/heartbeat` every 10 seconds while waiting.
3. Receive a `match_found` WebSocket event on the STOMP destination `/user/queue/matchmaking.match-found`.
4. (Optional) `POST /v1/matchmaking/leave` to cancel, or do nothing and let the 60 second timeout expire.

### Join the queue

```bash
curl -sS -X POST "http://localhost:8080/v1/matchmaking/queue" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -d "{\"mode\":\"ranked_1v1\",\"client_version\":\"1.0.0\",\"region\":\"us-central1\"}"
```

Response (`200`):

```json
{
  "queue_ticket_id": "uuid",
  "status": "queued",
  "joined_at": "2026-04-15T12:00:00Z",
  "timeout_at": "2026-04-15T12:01:00Z"
}
```

If the player is already queued for this mode, the endpoint returns `409` with code `MATCHMAKING_ALREADY_QUEUED`.

### Heartbeat

```bash
curl -sS -X POST "http://localhost:8080/v1/matchmaking/heartbeat" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

Response (`200`):

```json
{
  "status": "queued",
  "next_heartbeat_due_in_seconds": 10
}
```

Missing 2 heartbeats in a row causes the ticket to be stale-removed by the server.

### Leave the queue (idempotent)

```bash
curl -sS -X POST "http://localhost:8080/v1/matchmaking/leave" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

Response (`200`):

```json
{ "status": "left_queue" }
```

### Queue status (recovery path)

```bash
curl -sS "http://localhost:8080/v1/matchmaking/status" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

Response when queued (`200`):

```json
{
  "status": "queued",
  "queue_ticket_id": "uuid",
  "joined_at": "2026-04-15T12:00:00Z",
  "timeout_at": "2026-04-15T12:01:00Z"
}
```

Response when not queued (`200`):

```json
{ "status": "not_queued" }
```

### WebSocket: match_found and queue_timeout events

Match lifecycle events are pushed over STOMP-over-WebSocket. The handshake endpoint is:

```
ws://localhost:8080/ws
```

Authenticate the STOMP session by sending the same Bearer JWT on the `CONNECT` frame:

```
CONNECT
Authorization: Bearer YOUR_ACCESS_TOKEN

```

Subscribe to the per-user destinations (the server addresses them with `convertAndSendToUser` so only the current session receives events):

- `/user/queue/matchmaking.match-found` - payload shape:

```json
{
  "event_type": "match_found",
  "match_id": "uuid",
  "mode": "ranked_1v1",
  "players": [{ "player_id": "uuid" }, { "player_id": "uuid" }],
  "created_at": "2026-04-15T12:00:14Z",
  "expires_at": "2026-04-15T12:01:14Z",
  "connect_hint": null
}
```

- `/user/queue/matchmaking.queue-timeout` - payload shape:

```json
{
  "event_type": "queue_timeout",
  "queue_ticket_id": "uuid",
  "message": "Queue timeout reached after 60 seconds"
}
```

Delivery is at-least-once; clients must deduplicate by `match_id`. If delivery fails, the server retries up to 3 times at 5 second intervals. On exhaustion, the match is cancelled and both players are returned to the queue automatically.

### MVP matchmaking defaults

These values are hardcoded for MVP and sourced from `application.yml` under `forge.matchmaking.*`:

| Setting | Default | Override env var |
|---------|---------|------------------|
| Queue timeout | 60 seconds | `FORGE_MATCHMAKING_QUEUE_TIMEOUT_SECONDS` |
| Heartbeat interval | 10 seconds | `FORGE_MATCHMAKING_HEARTBEAT_INTERVAL_SECONDS` |
| Stale heartbeat threshold | 2 missed intervals | `FORGE_MATCHMAKING_STALE_HEARTBEAT_THRESHOLD` |
| Notification retry count | 3 | `FORGE_MATCHMAKING_NOTIFICATION_RETRY_COUNT` |
| Notification retry interval | 5 seconds | `FORGE_MATCHMAKING_NOTIFICATION_RETRY_INTERVAL_SECONDS` |

See [WebConfigurables.md](documentation/decisions/WebConfigurables.md) for the list of settings intended to become per-game configurable in Phase 2.

## Tests

### Backend (Java)

```bash
cd server
./gradlew.bat test
```

Integration tests use an in-memory H2 database (`application-test.yml`) and mock the Steam client.

### Godot SDK (GDScript)

With [Godot 4.3](https://godotengine.org/) installed and on your `PATH`:

```bash
cd client/godot
godot --headless --quit --script res://tests/run_all.gd
```

This runs the headless suite in `tests/run_all.gd` against stubbed HTTP and realtime layers. For manual end-to-end testing against a local backend, open `client/godot/project.godot` in the Godot editor and run the main scene (`test_harness/cockpit.tscn`), or use **Project -> Run** after setting `FORGE_STEAM_DEV_STUB_ENABLED=true` on the server per the Steam stub section above.

## License

Proprietary / not for distribution unless you add a license.
