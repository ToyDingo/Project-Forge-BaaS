# Leaderboard Vertical Slice Implementation

This document describes the implemented leaderboard MVP slice end-to-end: API contract, business logic, persistence model, and test coverage.

---

## Scope

Implemented capabilities:

1. Authenticated players can submit match outcomes.
2. Server waits for both client reports per match.
3. Server reconciles result and updates aggregated wins/losses.
4. Clients can query paginated dense-rank leaderboard and individual rank.
5. In-flight report rows are cleaned up after completion.
6. Replay is blocked to prevent double-counting.

Out of scope for this MVP slice:

- Real-time push delivery of leaderboard updates.
- Advanced fraud/anti-cheat reconciliation.
- Historical match browsing API.
- Redis-backed ranking cache.

---

## API Endpoints

All endpoints are exposed from:

- `server/src/main/java/com/forgebackend/controller/ForgeGatewayController.java`

All leaderboard endpoints require bearer JWT.

### 1) Report result

- **Method/Path:** `POST /v1/leaderboard/results`
- **Auth:** `Authorization: Bearer <access_token>`
- **Body:**

```json
{
  "match_id": "uuid",
  "winner_id": "uuid",
  "loser_id": "uuid"
}
```

- **Response (`pending`):**

```json
{
  "status": "pending",
  "message": "Match report recorded; waiting for the other player's report."
}
```

- **Response (`completed`):**

```json
{
  "status": "completed",
  "message": "Match reconciled and stats updated"
}
```

### 2) Top leaderboard

- **Method/Path:** `GET /v1/leaderboard/top?page=1&size=10`
- **Auth:** JWT
- **Behavior:** page size capped at 10

### 3) Individual rank

- **Method/Path:** `GET /v1/leaderboard/rank/{playerId}`
- **Auth:** JWT
- **Behavior:** returns rank + wins/losses for the requested player in caller's game context

---

## Service Logic (Layer 3)

Main orchestration is in:

- `server/src/main/java/com/forgebackend/service/LeaderboardService.java`

### reportMatchResult flow

1. Reject if `(game_id, match_id)` already exists in `completed_matches` (replay prevention).
2. Validate payload:
   - `winner_id != loser_id`
   - reporter is one of the participants
   - both participants belong to authenticated game
3. Reject duplicate in-flight report from same reporter for same match.
4. Save report row to `match_reports`.
5. If only one report exists, return `pending`.
6. When two reports exist:
   - reconcile winner/loser (MVP: if mismatch, use first report and log warning)
   - increment winner wins and loser losses in `player_stats` (upsert style)
   - insert finalization tombstone into `completed_matches`
   - delete transient rows from `match_reports` for that `(game_id, match_id)`
   - return `completed`

### Ranking logic

Dense rank ordering is implemented in SQL:

1. `wins DESC`
2. `losses ASC`
3. `last_win_at ASC NULLS LAST` (player reaching win total earlier ranks higher)
4. `player_id ASC` deterministic final tie-break

---

## Data Model (Layer 4)

### Tables used

1. `match_reports` (transient)
   - per-client report rows for in-flight matches
   - unique key prevents duplicate reporter submissions per match
2. `player_stats` (aggregated)
   - per-game, per-player wins/losses and tie-break timestamp
3. `completed_matches` (tombstone/idempotency)
   - unique `(game_id, match_id)` lock to prevent replay double-counting

### Migrations

- `server/src/main/resources/db/migration/V3__leaderboard_match_reports_and_player_stats.sql`
- `server/src/main/resources/db/migration/V4__completed_matches_idempotency.sql`

Mirrored manual SQL:

- `db/003_leaderboard_match_reports_and_player_stats.sql`
- `db/004_completed_matches_idempotency.sql`

---

## Security and Access Rules

- Caller game context comes from JWT claim `game_id`.
- Reporter identity comes from JWT subject (`player_id`).
- Players cannot directly write their own stats.
- Stats only change through reconciled match completion path.

---

## Error Handling

Relevant error codes:

- `LEADERBOARD_INVALID_RESULT` (400)
- `LEADERBOARD_MATCH_NOT_READY` (202 message in response payload for pending state)
- `LEADERBOARD_DUPLICATE_REPORT` (409)
- `LEADERBOARD_PLAYER_NOT_FOUND` (404)

Auth and shared errors:

- `FORGE_INVALID_TOKEN`, `FORGE_INVALID_REQUEST`, etc.

---

## Testing

Unit tests for core leaderboard behavior are in:

- `server/src/test/java/com/forgebackend/service/LeaderboardServiceTest.java`

Covered scenarios:

1. first report -> pending
2. two reports -> reconciliation and stat update
3. duplicate in-flight report rejected
4. invalid payload rejected
5. already finalized match replay rejected

---

## Operational Notes

- `match_reports` is intentionally transient and cleaned on completion.
- `completed_matches` is intentionally retained to enforce idempotency.
- For future scale, leaderboard read path can be cached in Redis while preserving PostgreSQL as source of truth.
