# Matchmaking Vertical Slice (MVP) - Requirements and Design

This document defines the implementation plan for the next backend vertical slice: matchmaking.
It captures product requirements, API contracts, architecture, and test acceptance criteria.

## Implementation Status

The matchmaking vertical slice described in this document is now implemented and merged.

- All four HTTP endpoints in section 6 are live on `ForgeGatewayController`, delegating to `MatchmakingService`.
- The WebSocket contract in section 7 is implemented via Spring STOMP on `/ws` with JWT-authenticated `CONNECT` frames and per-user destinations `/user/queue/matchmaking.match-found` and `/user/queue/matchmaking.queue-timeout`.
- The `V5__matchmaking_tables.sql` Flyway migration creates the three tables in section 10 (`matchmaking_queue_entries`, `matchmaking_matches`, `matchmaking_match_players`) with the proposed partial unique index for active queue entries.
- Error codes in section 12 are added to `ForgeErrorCode`: `MATCHMAKING_ALREADY_QUEUED`, `MATCHMAKING_QUEUE_TIMEOUT`, `MATCHMAKING_MATCH_CANCELLED`.
- Async orchestration uses Apache Camel (`MatchmakingCamelRoutes`) with three routes: matchmaker scan (2s), eviction scan (5s), and notification delivery with 3 retries at 5 second intervals. The internal state transitions run inside `MatchmakingOrchestrator` so the Camel layer stays transport-only.
- The event bus abstraction (`ForgeEventBus` + `InProcessEventBusAdapter`) isolates WebSocket delivery so the cloud migration can swap to GCP Pub/Sub without call-site changes. This aligns with the freeze in [FreezeNowDeferSafely.md](../decisions/FreezeNowDeferSafely.md).
- Phase 1 tests in section 14 are covered by `MatchmakingServiceTest` (nine mocked scenarios). Integration tests listed in the same section remain as Phase 2 work.

See [WhatWasImplemented.md](../foundations/WhatWasImplemented.md) and the quickstart in [README.md](../../README.md) for the usage-facing summary.

The sections below are preserved as the original design record.


## 1. Objective

The matchmaking MVP must let authenticated players enter a queue and receive a match quickly enough to start game sessions.

Primary success condition:

- sessions can find and connect enough players to start
- players typically tolerate 30 to 60 seconds of waiting

## 2. MVP Scope

In scope:

1. Queue join for authenticated players
2. One active queue entry per player per game mode
3. Basic compatibility checks:
   - same game
   - same mode
   - same client version
   - same platform
   - latency-aware candidate selection
4. Match found push notifications over WebSocket
5. Queue timeout handling at 60 seconds
6. Manual queue leave flow
7. Queue status endpoint for client recovery and polling fallback
8. Heartbeat endpoint with stale-entry eviction

Out of scope for MVP:

- skill-based matchmaking (phase 2)
- dynamic widening strategy over time (phase 2 configurable policy)
- per-game web UI configuration surface (phase 2)
- Idempotency-Key request handling (phase 2)
- advanced anti-cheat / integrity verification (phase 2)

## 3. Non-Functional Requirements

- Match notifications use at-least-once delivery semantics.
- Server retries failed `match_found` delivery 3 times, 5 seconds apart.
- Queue wait limit is 60 seconds in MVP.
- Heartbeat interval is 10 seconds in MVP.
- Player is removed after 2 missed heartbeats.
- API and event payloads must be machine-readable so game clients can handle state transitions cleanly.

## 4. High-Level Flow

1. Client authenticates and obtains Forge JWT.
2. Client opens authenticated WebSocket connection.
3. Client calls `POST /v1/matchmaking/queue`.
4. Backend validates queue eligibility and creates queue ticket.
5. Matchmaker scans compatible queued players and forms a match (MVP optimized for 1v1).
6. Backend pushes `match_found` to involved players over WebSocket.
7. If push fails, backend retries up to 3 times (5s interval).
8. If still unreachable:
   - 1v1: cancel match
   - many-v-many (future): continue with remaining players if mode policy allows
9. If queue reaches 60 seconds without a match, remove and push timeout message.

## 5. Queue Lifecycle

States:

- `not_queued`
- `queued`
- `matched`
- `timed_out`
- `left_queue`
- `stale_removed`

Key transitions:

- `not_queued` -> `queued`: join accepted
- `queued` -> `matched`: match found and reserved
- `queued` -> `timed_out`: reached 60 second limit
- `queued` -> `left_queue`: client leaves
- `queued` -> `stale_removed`: missed 2 heartbeats

Terminal states for a queue ticket:

- `matched`
- `timed_out`
- `left_queue`
- `stale_removed`

## 6. API Contract (Layer 2)

All endpoints below require:

- `Authorization: Bearer <access_token>`

Game and player identity are taken from JWT claims.

### 6.1 Join Queue

- Method/Path: `POST /v1/matchmaking/queue`

Request body (MVP):

```json
{
  "mode": "ranked_1v1",
  "client_version": "1.0.3",
  "region": "us-central1",
  "latency_by_region_ms": {
    "us-central1": 24,
    "us-east1": 52
  }
}
```

Response `200`:

```json
{
  "queue_ticket_id": "uuid",
  "status": "queued",
  "joined_at": "2026-04-15T12:00:00Z",
  "timeout_at": "2026-04-15T12:01:00Z"
}
```

Duplicate active join response `409`:

```json
{
  "error": {
    "code": "MATCHMAKING_ALREADY_QUEUED",
    "message": "Player is already queued for this game mode."
  }
}
```

### 6.2 Leave Queue

- Method/Path: `POST /v1/matchmaking/leave`

Behavior:

- idempotent success
- if queued: remove from queue
- if not queued: no-op

Response `200`:

```json
{
  "status": "left_queue"
}
```

### 6.3 Queue Status

- Method/Path: `GET /v1/matchmaking/status`

Purpose:

- recovery path if WebSocket event was missed
- polling fallback for clients

Response `200` examples:

```json
{
  "status": "queued",
  "queue_ticket_id": "uuid",
  "joined_at": "2026-04-15T12:00:00Z",
  "timeout_at": "2026-04-15T12:01:00Z"
}
```

```json
{
  "status": "not_queued"
}
```

### 6.4 Heartbeat

- Method/Path: `POST /v1/matchmaking/heartbeat`

Purpose:

- indicate queued client is still active
- prevent stale queue occupancy

Response `200`:

```json
{
  "status": "queued",
  "next_heartbeat_due_in_seconds": 10
}
```

## 7. WebSocket Contract

WebSocket is used for server-to-client async notifications.

### 7.1 Authentication

- JWT-authenticated WebSocket session
- handshake must resolve player identity and game context

### 7.2 Event: `match_found`

Minimum payload for MVP:

```json
{
  "event_type": "match_found",
  "match_id": "uuid",
  "mode": "ranked_1v1",
  "players": [
    { "player_id": "uuid" },
    { "player_id": "uuid" }
  ],
  "created_at": "2026-04-15T12:00:14Z",
  "expires_at": "2026-04-15T12:00:29Z",
  "connect_hint": null
}
```

Delivery semantics:

- at-least-once
- client must dedupe by `match_id`

Retry policy:

- 3 retries, 5 seconds apart

### 7.3 Event: `queue_timeout`

When 60 second queue timeout is reached:

```json
{
  "event_type": "queue_timeout",
  "queue_ticket_id": "uuid",
  "message": "No matches were available. Please try again."
}
```

## 8. Matching Rules (MVP)

Required compatibility checks:

1. same `game_id`
2. same `mode`
3. same `client_version`
4. same `platform`
5. latency within configured limit (MVP default can be hardcoded until per-game config exists)

MVP focus:

- optimize for 1v1 matching first
- keep internal model extensible for many-v-many in later phases

## 9. Failure Handling

### 9.1 Missed Heartbeats

- expected heartbeat every 10 seconds
- after 2 missed heartbeats, remove player from queue

### 9.2 Notification Failures

- on `match_found` push failure, retry 3 times with 5 second delay
- if still unreachable:
  - 1v1: cancel match
  - many-v-many: continue with remaining players only if mode policy allows (phase 2 configuration)

### 9.3 Timeout

- hard timeout at 60 seconds in queue
- remove player and notify client
- client must manually re-enter queue

## 10. Data Model Proposal (Layer 4)

MVP can use a persistent queue table plus match reservation records.
Table names can be adjusted during implementation.

Proposed migration `V5` additions:

1. `matchmaking_queue_entries`
   - `id` (queue_ticket_id, UUID, PK)
   - `game_id` (UUID, indexed)
   - `player_id` (UUID, indexed)
   - `mode` (text)
   - `client_version` (text)
   - `platform` (text)
   - `region` (text nullable)
   - `latency_by_region_json` (jsonb nullable)
   - `status` (text)
   - `joined_at` (timestamp)
   - `timeout_at` (timestamp)
   - `last_heartbeat_at` (timestamp)
   - unique partial index for active queue entry by `(game_id, player_id, mode)` where status is active

2. `matchmaking_matches`
   - `id` (match_id, UUID, PK)
   - `game_id` (UUID)
   - `mode` (text)
   - `status` (text: pending_notify, ready, cancelled)
   - `created_at` (timestamp)
   - `expires_at` (timestamp)

3. `matchmaking_match_players`
   - `match_id` (UUID FK)
   - `player_id` (UUID)
   - `delivery_status` (text: pending, delivered, failed)
   - PK `(match_id, player_id)`

## 11. Layered Package Plan

Follow current project package boundaries:

- `controller/`
  - add matchmaking endpoints to `ForgeGatewayController`
- `service/`
  - add `MatchmakingService`
- `repository/`
  - queue and match repositories
- `entity/`
  - queue and match entities
- `dto/`
  - queue request and response DTOs
- `exception/`
  - add matchmaking error codes
- `config/`
  - WebSocket and matchmaking property configuration

Controller remains zero business logic.
All orchestration and rules belong in service layer.

## 12. Error Code Additions (Proposed)

- `MATCHMAKING_ALREADY_QUEUED` -> 409
- `MATCHMAKING_QUEUE_TIMEOUT` -> 200 event/terminal status semantics
- `MATCHMAKING_NOT_IN_QUEUE` -> optional internal use (leave remains 200)
- `MATCHMAKING_HEARTBEAT_MISSED` -> internal/observability
- `MATCHMAKING_MATCH_CANCELLED` -> event/status for cancellation paths

## 13. Observability Requirements (MVP)

Metrics to emit:

- queue depth by game and mode
- time-to-match distribution
- timeout count
- stale-removal count
- notification retry count
- notification final failure count

Logs:

- include `game_id`, `player_id`, `queue_ticket_id`, `match_id`, and reason codes

## 14. Testing and Acceptance Criteria

Unit tests (service layer):

1. join succeeds for eligible player
2. duplicate join returns conflict
3. heartbeat updates liveness
4. stale player is evicted after missed heartbeats
5. timeout path removes player and emits timeout event
6. 1v1 compatible players get matched
7. notification retry policy executes exactly 3 retries
8. unreachable client in 1v1 cancels match
9. leave is idempotent success when not queued

Integration tests:

1. full auth -> join -> status flow
2. WebSocket authenticated session receives `match_found`
3. status endpoint recovers state if client missed push
4. timeout and stale eviction integration behavior

MVP done criteria:

- all required endpoints and events implemented
- queue lifecycle rules enforced
- retry and timeout policies enforced
- tests pass in CI

## 15. Phase 2 Notes

- move hardcoded matchmaking knobs to per-game web-configurable settings
- introduce skill-based matching options
- introduce queue widening policy over time
- add Idempotency-Key support
- expand many-v-many mode policies
