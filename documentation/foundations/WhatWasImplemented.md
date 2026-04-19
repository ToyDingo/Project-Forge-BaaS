# What was implemented

This document summarizes what was added to the **GameBackend** repository for the implemented MVP vertical slices:

1. **Auth vertical slice** (Steam ticket exchange + Forge JWT)
2. **Leaderboard vertical slice** (dual-client report reconciliation + dense ranking)
3. **Matchmaking vertical slice** (queue + 1v1 match formation + WebSocket notifications)

It complements [README.md](../../README.md), [CodeStructure.md](CodeStructure.md), and [CloudMigration.md](../architecture/CloudMigration.md).

---

## Overview

- **Layer 2 (gateway):** single controller class, `ForgeGatewayController`, with all endpoints and no business logic.
- **Layer 3 (services):** `AuthService`, `ForgeJwtService`, `LeaderboardService`, and `MatchmakingService`.
- **Layer 4 (data):** PostgreSQL tables for `games`, `players`, `match_reports`, `player_stats`, `completed_matches`, `matchmaking_queue_entries`, `matchmaking_matches`, and `matchmaking_match_players` with Flyway-managed migrations.
- **Integration:** `steam/` for Steam Web API, `matchmaking/` for the event bus abstraction plus Camel-based async orchestration.

---

## Auth vertical slice

### API behavior

- `POST /v1/auth/steam` accepts `X-Forge-Api-Key` and `steam_ticket`, validates Steam identity, upserts player, returns RS256 JWT.
- `GET /v1/me` returns JWT claims (`player_id`, `game_id`, `platform`).

### Security model

- `ForgeApiKeyAuthenticationFilter` validates game API key for Steam auth exchange.
- `ForgeJwtAuthenticationFilter` validates JWT bearer tokens for protected routes.
- Stateless Spring Security config (`SessionCreationPolicy.STATELESS`).

### Persistence

- `games` table stores tenant/game identity and Steam/API-key metadata.
- `players` table stores per-game platform identities.

---

## Leaderboard vertical slice

### API behavior

- `POST /v1/leaderboard/results`
  - Requires JWT.
  - Accepts `match_id`, `winner_id`, `loser_id`.
  - First report returns pending.
  - Second report triggers reconciliation and stat update.
- `GET /v1/leaderboard/top?page=&size=`
  - Dense-rank leaderboard (wins desc, losses asc, first-to-win tie-break via `last_win_at`, then `player_id`).
  - Paged, size capped at 10.
- `GET /v1/leaderboard/rank/{playerId}`
  - Returns a specific player's rank and W/L stats.

### Reconciliation and integrity

- Duplicate report by same reporter for same in-flight match is rejected.
- Reconciliation waits for two reports for the same `(game_id, match_id)`.
- For conflicting pair reports in MVP, first report is used and conflict is logged.

### Cleanup + replay protection

- After successful reconciliation and stat updates:
  - Rows for that match are removed from `match_reports`.
  - A tombstone row is inserted into `completed_matches`.
- `completed_matches` enforces unique `(game_id, match_id)` to prevent double-counting if the same match is replayed later.

---

## Matchmaking vertical slice

### API behavior

- `POST /v1/matchmaking/queue`
  - Creates one active queue ticket per `(game, player, mode)`.
  - Returns `queue_ticket_id`, `status=queued`, `joined_at`, `timeout_at`.
  - Duplicate active join is rejected with `409 MATCHMAKING_ALREADY_QUEUED`.
- `POST /v1/matchmaking/leave`
  - Idempotent. Always returns `200 {"status": "left_queue"}` whether or not an active ticket exists.
- `GET /v1/matchmaking/status`
  - Recovery endpoint. Returns the most recent active ticket for the caller, or `{"status": "not_queued"}`.
- `POST /v1/matchmaking/heartbeat`
  - Stamps `last_heartbeat_at` on the caller's active ticket. Missing 2 intervals causes stale removal.

### WebSocket contract (STOMP over `/ws`)

- Authentication: STOMP `CONNECT` frame must carry `Authorization: Bearer <forge-jwt>`. Enforced by a `ChannelInterceptor` installed in `WebSocketConfig`.
- Per-user destination `/user/queue/matchmaking.match-found` delivers `MatchFoundEvent` payloads.
- Per-user destination `/user/queue/matchmaking.queue-timeout` delivers `QueueTimeoutEvent` payloads.
- Delivery is at-least-once. Clients must dedupe by `match_id`.

### Async orchestration

Three Apache Camel routes in `MatchmakingCamelRoutes` drive the asynchronous lifecycle. All database state changes they cause are performed inside `MatchmakingOrchestrator` so the Camel layer stays transport-only:

- `matchmaking.matchmaker` (timer, every 2s) - forms 1v1 matches from queued players per `(game, mode)` scope in join order, transitions entries to `matched`, and dispatches to the delivery route.
- `matchmaking.eviction` (timer, every 5s) - transitions tickets past their `timeout_at` to `timed_out` (and pushes a `queue_timeout` event), and transitions tickets past the stale heartbeat window to `stale_removed`.
- `matchmaking.deliver` (direct, with `onException` redelivery policy) - pushes `match_found` to every pending participant through `ForgeEventBus`. On transient failure it retries up to 3 times at 5 second intervals (configurable). On exhaustion, it cancels the match and returns matched participants to `queued` so they can be paired again.

### MVP defaults (hardcoded today)

Tunables live in `forge.matchmaking.*` and are tracked in [WebConfigurables.md](../decisions/WebConfigurables.md) for Phase 2 per-game configuration:

| Setting | Default |
|---------|---------|
| Queue timeout | 60 seconds |
| Heartbeat interval | 10 seconds |
| Stale heartbeat threshold | 2 missed intervals |
| Notification retry count | 3 |
| Notification retry interval | 5 seconds |

### Event bus abstraction

- `ForgeEventBus` is the only abstraction service code depends on when pushing matchmaking events to a player.
- `InProcessEventBusAdapter` (`@Primary`) implements it with Spring's `SimpMessagingTemplate` for local/MVP delivery.
- A future GCP Pub/Sub adapter replaces only this class, with no caller-side changes. This aligns with the [FreezeNowDeferSafely.md](../decisions/FreezeNowDeferSafely.md) freeze on Pub/Sub-aligned architecture.

---

## Database migrations

### Root SQL (`db/`)

- `001_create_games_and_players.sql`
- `002_games_steam_and_api_key_lookup.sql`
- `003_leaderboard_match_reports_and_player_stats.sql`
- `004_completed_matches_idempotency.sql`
- `005_matchmaking_tables.sql`

### Flyway (`server/src/main/resources/db/migration/`)

- `V1__create_games_and_players.sql`
- `V2__games_steam_and_api_key_lookup.sql`
- `V3__leaderboard_match_reports_and_player_stats.sql`
- `V4__completed_matches_idempotency.sql`
- `V5__matchmaking_tables.sql`

---

## Code organization delivered

Per the agreed structure:

- `controller/` contains one source-of-truth gateway class.
- `service/`, `repository/`, `entity/`, `dto/`, `security/`, `config/`, `exception/` are explicit top-level folders.
- Mapping and package details are documented in [CodeStructure.md](CodeStructure.md).

---

## Dev-mode testing support

- `SeedDevGame` utility remains available through the Gradle task `seedDevGame`.
- `DevOnlySteamClientStub` now generates deterministic mock SteamID64 from the provided ticket string:
  - same ticket -> same mock identity
  - different ticket -> different mock identity

This allows local multi-player leaderboard testing without Steam API calls.

---

## Validation performed

- Unit tests and integration tests compile and pass (`./gradlew test`).
- Leaderboard service tests cover:
  - pending first report,
  - completed reconciliation path,
  - duplicate detection,
  - invalid payload validation,
  - replay rejection for already finalized matches.
- `MatchmakingServiceTest` covers the nine Phase 1 scenarios (all mocked, no database):
  1. Join queue happy path.
  2. Duplicate join returns `MATCHMAKING_ALREADY_QUEUED`.
  3. Leave is idempotent with and without an active ticket.
  4. Heartbeat updates `last_heartbeat_at`.
  5. Stale eviction transitions tickets to `stale_removed`.
  6. Timeout eviction transitions tickets to `timed_out` and pushes `queue_timeout`.
  7. Compatible 1v1 pair is matched, a match row is created, and entries flip to `matched`.
  8. Notification delivery failure surfaces as `EventDeliveryException` so the Camel retry policy engages (3 retries at 5s intervals per `ForgeMatchmakingProperties`).
  9. Retry exhaustion cancels the match and returns both participants to `queued`.

---

*Last updated to match the auth, leaderboard, and matchmaking vertical slice implementation in this repository.*
