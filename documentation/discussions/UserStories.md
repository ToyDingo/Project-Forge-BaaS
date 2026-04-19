# Forge Backend -- User Stories & Acceptance Criteria

**Document type:** Product requirements  
**Version:** v0.2  
**April 2026**  
**Aligned with:** MVP Technical Design Document v0.2.2.1  
**Persona:** Game Developer (the Forge Backend customer)

---

## How to Read This Document

Each story follows the format:

> *As a game developer, I want to [do something], so that [I get some value].*

Acceptance criteria are written as **Given / When / Then** conditions that define when the story is complete. Stories are organised by layer and map directly to implemented or designed behaviour in the v0.2.2.1 design document.

**Status tags:**
- `✓ Implemented` -- behaviour is in place and tested
- `Designed` -- fully specified; implementation not yet started
- `Phase 2` -- out of MVP scope; tracked for later

**Story ID format:** `US-[LAYER]-[FEATURE]-[NUMBER]`

---

## Layer 4 -- Data and Infrastructure

These stories describe what the data layer must guarantee. They are not stories a developer interacts with directly, but every story in Layers 1-3 depends on them being true.

---

### US-L4-INFRA-01 -- Game Data Is Isolated at the Database Level `✓ Implemented`

> *As a game developer, I want my game's players, matches, and leaderboard data to be completely isolated from every other game on the platform, so that I can trust that no other developer's game can read or affect my data.*

**Acceptance Criteria**

- **Given** two games are registered on the platform,  
  **When** any query runs against players, match_reports, player_stats, completed_matches, or matchmaking tables,  
  **Then** every query is scoped by `game_id` -- no cross-game data can be returned even though the underlying tables are shared.

- **Given** a player row is being created,  
  **When** the `(game_id, platform, platform_user_id)` combination already exists,  
  **Then** the insert is rejected by the database unique constraint -- the same platform identity cannot be registered twice under the same game.

- **Given** a match result is submitted,  
  **When** both participants are validated,  
  **Then** the service confirms both `winner_id` and `loser_id` belong to the authenticated `game_id` before proceeding -- players from other games cannot be referenced.

---

### US-L4-INFRA-02 -- Schema Migrations Apply Automatically and Safely `✓ Implemented`

> *As a game developer running Forge locally, I want the database schema to be created and updated automatically when I start the server, so that I never have to run SQL manually to get a working environment.*

**Acceptance Criteria**

- **Given** a fresh PostgreSQL instance with no schema,  
  **When** the Spring Boot application starts,  
  **Then** Flyway applies all migrations in order (V1 through V5) and the full schema is ready before the first request is served.

- **Given** migrations V1 through V4 have already been applied,  
  **When** the application starts on a version that includes V5,  
  **Then** Flyway applies only V5 -- already-applied migrations are not re-run.

- **Given** a migration has been applied and data exists in the affected tables,  
  **When** the application restarts,  
  **Then** existing data is preserved -- Flyway never drops or truncates tables during an upgrade migration.

---

### US-L4-INFRA-03 -- API Keys Are Never Stored in Plain Text `✓ Implemented`

> *As a game developer, I want to know that if the Forge database were ever compromised, my API key could not be extracted from it, so that my game account is not put at immediate risk.*

**Acceptance Criteria**

- **Given** a game is registered and a Forge API key is issued,  
  **When** the key is persisted to the `games` table,  
  **Then** only two derived values are stored: a SHA-256 hex lookup hash (`api_key_lookup_hash`) and a BCrypt verification hash (`api_key_hash`) -- the raw key is never written to the database.

- **Given** an inbound request carries a raw API key in `X-Forge-Api-Key`,  
  **When** the gateway resolves it,  
  **Then** the SHA-256 hash is used to find the matching game row, and BCrypt is used to verify the key -- the raw value is never compared against a stored plaintext value.

- **Given** the database is inspected directly,  
  **When** the `games` table is read,  
  **Then** no column contains a recoverable raw API key -- `api_key_lookup_hash` is a one-way hash and `api_key_hash` is a BCrypt digest.

---

### US-L4-INFRA-04 -- No Player Passwords Are Stored `✓ Implemented`

> *As a game developer, I want to know that Forge never stores my players' passwords, so that a database breach cannot expose player credentials.*

**Acceptance Criteria**

- **Given** a player authenticates via Steam,  
  **When** their player row is created or upserted,  
  **Then** no password column is written -- the `players` table has no password field because Steam is the identity provider.

- **Given** the database schema is inspected,  
  **When** the `players` table definition is reviewed,  
  **Then** there is no `password`, `password_hash`, or equivalent column anywhere in the schema.

---

### US-L4-INFRA-05 -- Environment Configuration Drives All Runtime Behaviour `✓ Implemented`

> *As a game developer running Forge locally, I want to configure the server entirely through environment variables, so that I can switch between local and cloud targets without changing any code.*

**Acceptance Criteria**

- **Given** a `.env` file or shell environment with `FORGE_DATASOURCE_URL`, `FORGE_DATASOURCE_USERNAME`, and `FORGE_DATASOURCE_PASSWORD` set,  
  **When** the application starts,  
  **Then** it connects to the configured database -- no hardcoded connection string exists in the codebase.

- **Given** `FORGE_JWT_PRIVATE_KEY_PEM` and `FORGE_JWT_PUBLIC_KEY_PEM` are set via environment,  
  **When** the JWT service initialises,  
  **Then** it uses the injected PEM keys -- the dev classpath keys are not used.

- **Given** no environment override is present,  
  **When** the application starts in local dev mode,  
  **Then** it falls back to sane local defaults (classpath dev keys, localhost DB) -- the application does not crash on startup without a full production config.

---

## Layer 3 -- Core Services

These stories describe the backend service behaviour that game developers rely on through the API.

---

### US-L3-AUTH-01 -- Register a Game and Receive an API Key `✓ Implemented`

> *As a game developer, I want to register my game with Forge Backend and receive a unique API key, so that my game can authenticate players against my specific game instance.*

**Acceptance Criteria**

- **Given** a developer has registered a game in the Forge system,  
  **When** the `games` row is created,  
  **Then** a unique Forge API key is generated for that game and made available to the developer for placement in their client configuration.

- **Given** a developer has multiple games registered,  
  **When** API keys are issued,  
  **Then** each game has its own independent key -- one game's key cannot be used to authenticate players for another game.

- **Given** a game row exists,  
  **When** its Steam credentials are configured (`steam_app_id`, `steam_web_api_key`),  
  **Then** those credentials are stored per game row -- multiple games on the platform can each have independent Steam App IDs and Web API keys.

---

### US-L3-AUTH-02 -- Authenticate a Player via Steam `✓ Implemented`

> *As a game developer, I want my game to exchange a Steam session ticket for a Forge JWT, so that my players are authenticated without me building or maintaining any auth infrastructure.*

**Acceptance Criteria**

- **Given** the Forge API key is sent as `X-Forge-Api-Key` and a valid Steam session ticket is in the request body,  
  **When** `POST /v1/auth/steam` is called,  
  **Then** Steam's `ISteamUserAuth/AuthenticateUserTicket` endpoint is called, and on success a signed RS256 JWT is returned containing `player_id`, `game_id`, and `platform` claims.

- **Given** a player is authenticating for the first time,  
  **When** Steam validation succeeds,  
  **Then** a new player row is created scoped to `(game_id, platform, platform_user_id)` -- the player identity is created automatically with no additional developer action required.

- **Given** a player has authenticated before,  
  **When** they authenticate again with the same Steam identity,  
  **Then** the existing player row is upserted -- no duplicate records are created.

- **Given** an invalid or expired Steam session ticket,  
  **When** `POST /v1/auth/steam` is called,  
  **Then** the response is `401 STEAM_VALIDATION_FAILED` and no JWT is issued.

- **Given** an invalid or unknown Forge API key in the header,  
  **When** `POST /v1/auth/steam` is called,  
  **Then** the response is `401 FORGE_GAME_NOT_FOUND` and the request halts before Steam is called.

- **Given** the Steam Web API is unavailable,  
  **When** `POST /v1/auth/steam` is called,  
  **Then** the response is `503 STEAM_UNAVAILABLE` and a structured error body is returned.

- **Given** the game row is missing `steam_app_id` or `steam_web_api_key`,  
  **When** `POST /v1/auth/steam` is called for that game,  
  **Then** the response is `422 FORGE_GAME_MISCONFIGURED` -- the developer is told their game setup is incomplete rather than receiving a cryptic failure.

---

### US-L3-AUTH-03 -- Test Authentication Locally Without a Real Steam Account `✓ Implemented`

> *As a game developer, I want to test the authentication flow locally without a live Steam API connection, so that I can develop and iterate quickly without Steam credentials blocking me.*

**Acceptance Criteria**

- **Given** `FORGE_STEAM_DEV_STUB_ENABLED=true` is set,  
  **When** `POST /v1/auth/steam` is called with any ticket string,  
  **Then** the stub deterministically generates a mock `SteamID64` from the ticket string and returns a valid JWT -- no outbound call to Steam is made.

- **Given** the dev stub is enabled and two different ticket strings are used,  
  **When** both authenticate,  
  **Then** each receives a distinct `player_id` -- multi-player leaderboard and matchmaking scenarios can be tested locally.

- **Given** the same ticket string is used twice with the dev stub enabled,  
  **When** both calls complete,  
  **Then** both return the same `player_id` -- the stub is deterministic, not random.

- **Given** `FORGE_STEAM_DEV_STUB_ENABLED` is not set or is `false`,  
  **When** `POST /v1/auth/steam` is called,  
  **Then** the real Steam Web API client is always used -- the stub cannot activate without the flag.

---

### US-L3-AUTH-04 -- Use the Forge JWT to Call Protected Endpoints `✓ Implemented`

> *As a game developer, I want all API calls after login to use a bearer token, so that player identity and game scope are automatic on every request.*

**Acceptance Criteria**

- **Given** a valid Forge JWT,  
  **When** it is submitted as a `Bearer` token on any protected endpoint,  
  **Then** the `game_id` and `player_id` claims are automatically injected into the request context -- the developer does not pass these manually.

- **Given** a missing, malformed, or expired JWT,  
  **When** any protected endpoint is called,  
  **Then** the response is `401 FORGE_INVALID_TOKEN`.

- **Given** a JWT issued for Game A,  
  **When** it is used against an endpoint that queries data,  
  **Then** the `game_id` claim scopes all queries to Game A -- the developer cannot accidentally read another game's data.

---

### US-L3-LB-01 -- Report a Match Result `✓ Implemented`

> *As a game developer, I want both players in a completed match to independently submit the result, so that match outcomes are verified by consensus before affecting the leaderboard.*

**Acceptance Criteria**

- **Given** an authenticated player submits `POST /v1/leaderboard/results` with `match_id`, `winner_id`, and `loser_id`,  
  **When** it is the first report for that match,  
  **Then** the report is saved and the response status is `pending` -- the leaderboard is not yet updated.

- **Given** both players have submitted reports for the same `match_id` with consistent outcome,  
  **When** the second report arrives,  
  **Then** the winner's `wins` and loser's `losses` are incremented atomically, a `completed_matches` tombstone is inserted, the transient `match_reports` rows are deleted, and the response status is `completed`.

- **Given** both players submit reports but disagree on who won,  
  **When** reconciliation runs,  
  **Then** the first report's outcome is used, the conflict is logged, and the leaderboard updates -- the developer does not need to resolve conflicts manually.

- **Given** a report where `winner_id == loser_id`,  
  **When** the payload is validated,  
  **Then** the response is `400 LEADERBOARD_INVALID_RESULT`.

- **Given** a report where the submitting player is not listed as either participant,  
  **When** the payload is validated,  
  **Then** the response is `400 LEADERBOARD_INVALID_RESULT` -- a player cannot report on a match they were not part of.

---

### US-L3-LB-02 -- Prevent Duplicate and Replayed Match Reports `✓ Implemented`

> *As a game developer, I want the backend to automatically prevent the same match result from being submitted twice, so that network retries and client bugs cannot inflate player scores.*

**Acceptance Criteria**

- **Given** a player has already submitted a report for a given `match_id`,  
  **When** they submit a second report for the same match,  
  **Then** the response is `409 LEADERBOARD_DUPLICATE_REPORT` and no additional row is created.

- **Given** a match has already been finalised (tombstone row exists in `completed_matches`),  
  **When** any new report for the same `(game_id, match_id)` is submitted,  
  **Then** the request is immediately rejected -- a finalised match result is permanent.

---

### US-L3-LB-03 -- View the Leaderboard `✓ Implemented`

> *As a game developer, I want to retrieve a ranked leaderboard for my game, so that I can display it to players in-game without writing any ranking logic myself.*

**Acceptance Criteria**

- **Given** an authenticated player calls `GET /v1/leaderboard/top`,  
  **When** the request is processed,  
  **Then** a paginated list of players is returned ranked by: wins descending, then losses ascending, then earliest win date, then `player_id` as a deterministic final tie-break.

- **Given** two players have the same number of wins,  
  **When** the leaderboard is returned,  
  **Then** both players share the same rank and the next rank is not skipped (dense rank).

- **Given** `page` and `size` parameters are provided,  
  **When** the leaderboard is fetched,  
  **Then** the correct page slice is returned and `size` is capped at 10 regardless of the requested value.

- **Given** a player is authenticated to Game A,  
  **When** they fetch the leaderboard,  
  **Then** only players from Game A appear -- leaderboards are fully isolated by `game_id`.

---

### US-L3-LB-04 -- Look Up an Individual Player's Rank `✓ Implemented`

> *As a game developer, I want to retrieve a specific player's rank and stats, so that I can show a personalised "your rank" UI without fetching the entire leaderboard.*

**Acceptance Criteria**

- **Given** a `player_id` with recorded stats,  
  **When** `GET /v1/leaderboard/rank/{playerId}` is called,  
  **Then** the response includes the player's current dense rank, win count, and loss count within the caller's game.

- **Given** a `player_id` that has no recorded match results,  
  **When** `GET /v1/leaderboard/rank/{playerId}` is called,  
  **Then** the response is `404 LEADERBOARD_PLAYER_NOT_FOUND`.

- **Given** a `player_id` that belongs to a different game,  
  **When** the endpoint is called,  
  **Then** the player is not found -- cross-game rank lookups are not permitted.

---

### US-L3-MM-01 -- Join the Matchmaking Queue `✓ Implemented`

> *As a game developer, I want my game to place a player into a matchmaking queue with a single API call, so that I don't have to implement any queuing, player-pairing, or timeout logic myself.*

**Acceptance Criteria**

- **Given** an authenticated player calls `POST /v1/matchmaking/queue` with `mode`, `client_version`, `region`, and optional latency hints,  
  **When** the player has no active queue entry for that game and mode,  
  **Then** a queue ticket is created with status `queued` and the response includes `queue_ticket_id`, `joined_at`, and `timeout_at`.

- **Given** a player already has an active queue entry for the same game and mode,  
  **When** they call `POST /v1/matchmaking/queue` again,  
  **Then** the response is `409 MATCHMAKING_ALREADY_QUEUED` -- duplicate active entries are not permitted.

- **Given** a queue ticket is created,  
  **When** the Camel matchmaker route runs (every 2 seconds),  
  **Then** compatible queued players are evaluated and paired -- the developer does not trigger matching manually.

- **Given** two players are queued but have different `mode`, `client_version`, or `platform` values,  
  **When** the matchmaker evaluates them,  
  **Then** they are not paired -- eligibility constraints are enforced automatically.

---

### US-L3-MM-02 -- Maintain Queue Presence with Heartbeats `✓ Implemented`

> *As a game developer, I want the backend to automatically evict players who have disconnected without explicitly leaving, so that stale queue entries don't block matches from forming.*

**Acceptance Criteria**

- **Given** a player is queued,  
  **When** they call `POST /v1/matchmaking/heartbeat`,  
  **Then** `last_heartbeat_at` is updated and the response includes the queue status and the next expected heartbeat interval (10 seconds).

- **Given** a player stops sending heartbeats,  
  **When** 2 consecutive heartbeat intervals pass without a heartbeat,  
  **Then** the Camel eviction route transitions the queue entry to `stale_removed` -- the slot is freed for other players.

- **Given** a player's entry is `stale_removed`,  
  **When** they attempt to re-join the queue,  
  **Then** the join is accepted -- `stale_removed` does not permanently block a player.

---

### US-L3-MM-03 -- Leave the Queue `✓ Implemented`

> *As a game developer, I want leaving the queue to always succeed regardless of the player's current state, so that my client-side leave logic never needs to handle edge cases.*

**Acceptance Criteria**

- **Given** a player is actively queued,  
  **When** they call `POST /v1/matchmaking/leave`,  
  **Then** their entry transitions to `left_queue` and the response is `200 {"status": "left_queue"}`.

- **Given** a player is not currently queued,  
  **When** they call `POST /v1/matchmaking/leave`,  
  **Then** the response is still `200 {"status": "left_queue"}` -- leave is always a no-op success, never an error.

---

### US-L3-MM-04 -- Recover Queue State After Disconnection `✓ Implemented`

> *As a game developer, I want my game to check a player's current queue status at any time, so that clients can recover gracefully after a disconnection without losing their place.*

**Acceptance Criteria**

- **Given** a player is actively queued,  
  **When** they call `GET /v1/matchmaking/status`,  
  **Then** the response includes their active ticket details: `queue_ticket_id`, `status`, `joined_at`, and `timeout_at`.

- **Given** a player is not currently queued,  
  **When** they call `GET /v1/matchmaking/status`,  
  **Then** the response is `200 {"status": "not_queued"}` -- the endpoint never errors on a missing entry.

- **Given** a player missed a `match_found` WebSocket event due to a disconnection,  
  **When** they call `GET /v1/matchmaking/status` after reconnecting,  
  **Then** the response reflects current queue state -- this endpoint is a valid polling fallback when push delivery cannot be guaranteed.

---

### US-L3-MM-05 -- Receive a Match Notification in Real Time `✓ Implemented`

> *As a game developer, I want my game client to receive a push notification the moment a match is found, so that players transition into the multiplayer scene immediately without polling.*

**Acceptance Criteria**

- **Given** two compatible players are in the queue,  
  **When** the Camel matchmaker route pairs them,  
  **Then** both players receive a `match_found` event on their authenticated STOMP WebSocket connection containing `match_id`, `mode`, the player list, `created_at`, `expires_at`, and `connect_hint`.

- **Given** the backend attempts to deliver `match_found` and the delivery fails,  
  **When** up to 3 retry attempts run at 5-second intervals,  
  **Then** delivery succeeds on a subsequent attempt and both players receive the event.

- **Given** all 3 delivery retries are exhausted for a 1v1 match,  
  **When** the match is cancelled,  
  **Then** both players are automatically returned to `queued` status -- the developer does not handle match cancellation on the client; players are simply re-matched.

- **Given** a `match_found` notification may be delivered more than once for the same `match_id` (at-least-once semantics on the wire),  
  **When** duplicates arrive at the client,  
  **Then** the integration is responsible for deduplicating by `match_id` -- the Forge GDScript SDK will enforce this when implemented (US-L1-SDK-03).

---

### US-L3-MM-06 -- Have the Queue Time Out Gracefully `✓ Implemented`

> *As a game developer, I want players who wait too long to be automatically removed and notified, so that players are never silently stuck waiting forever.*

**Acceptance Criteria**

- **Given** a player has been queued for 60 seconds without being matched,  
  **When** the Camel eviction route runs,  
  **Then** the queue entry transitions to `timed_out` and a `queue_timeout` event is pushed to the player's WebSocket connection.

- **Given** a `queue_timeout` event is pushed to the player's STOMP subscription,  
  **When** the payload is inspected,  
  **Then** it includes `event_type`, `queue_ticket_id`, and a human-readable `message` suitable for display in a client UI.

- **Given** a player's queue entry has timed out,  
  **When** they attempt to re-join the queue,  
  **Then** the join is accepted -- timeout does not permanently block re-queuing.

---

## Layer 2 -- API Gateway

These stories describe the gateway behaviour that sits between the client and the core services. Developers interact with this layer through correct credential usage.

---

### US-L2-GW-01 -- Product API Surface Is Authenticated `✓ Implemented`

> *As a game developer, I want every Forge product API call that reads or writes my game's data to require the right credential, so that anonymous callers cannot access player or game state.*

**Acceptance Criteria**

- **Given** a request targets a protected URL (everything except the explicit allowlist below),  
  **When** no valid credential is presented,  
  **Then** the Spring Security filter chain rejects it with `401` -- no unauthenticated access to `/v1/**` or other authenticated routes.

- **Given** a request to `GET /health`, `GET /actuator/health`, or the HTTP WebSocket upgrade for `GET /ws` (handshake only),  
  **When** the filter chain runs,  
  **Then** the HTTP layer allows the request without a JWT or API key -- these are operational or transport endpoints, not game data APIs.

- **Given** `POST /v1/auth/steam` is called,  
  **When** the gateway evaluates the credential,  
  **Then** it requires `X-Forge-Api-Key` -- a player JWT is not valid on this endpoint.

- **Given** any other protected endpoint is called with `X-Forge-Api-Key` instead of a Bearer JWT,  
  **When** the gateway evaluates the credential,  
  **Then** the request is rejected -- the API key is only valid for the Steam auth exchange.

- **Given** a client opens a STOMP session over `/ws`,  
  **When** the STOMP `CONNECT` frame is processed,  
  **Then** JWT validation applies there (see US-L2-GW-04) -- the HTTP handshake is anonymous, but the realtime session is not.

---

### US-L2-GW-02 -- Documented Errors Return a Structured Response `✓ Implemented`

> *As a game developer, I want API failures that Forge handles explicitly to return a consistent JSON envelope with a machine-readable error code, so that my game can branch on `error.code` without parsing free-form message strings.*

**Acceptance Criteria**

- **Given** the server raises a `ForgeApiException` (domain errors: auth, validation, Steam, leaderboard, matchmaking),  
  **When** the response is returned,  
  **Then** the body matches `ErrorResponse`: `{ "error": { "code": "<ForgeErrorCode name>", "message": "<human-readable text>" } }`.

- **Given** request body JSON is malformed or bean validation fails in a way mapped by `GlobalExceptionHandler`,  
  **When** the handler runs,  
  **Then** the response is `400` with `error.code` `FORGE_INVALID_REQUEST` and a structured body as above.

- **Given** an auth failure on a protected REST call,  
  **When** the response is returned,  
  **Then** `error.code` is one of the relevant `ForgeErrorCode` values (for example `FORGE_GAME_NOT_FOUND`, `FORGE_INVALID_TOKEN`, `STEAM_VALIDATION_FAILED`) so the client can switch on it.

- **Given** an error occurs that is **not** covered by `ForgeApiException` or the handler's validation/mapping cases,  
  **When** the request fails,  
  **Then** Spring may still return a generic `5xx` -- the MVP goal is structured errors for all **expected** failure paths; expanding catch-all handling is optional hardening.

---

### US-L2-GW-03 -- Game Context Is Injected Automatically `✓ Implemented`

> *As a game developer, I want the gateway to extract my game and player identity from the JWT automatically, so that I never have to pass `game_id` or `player_id` as explicit parameters.*

**Acceptance Criteria**

- **Given** a valid Bearer JWT is presented on any protected endpoint,  
  **When** the request reaches the service layer,  
  **Then** `game_id` and `player_id` are already in the security context -- the controller never reads them from query parameters or the request body.

- **Given** a JWT with a `game_id` claim for Game A is used,  
  **When** any leaderboard or matchmaking query runs,  
  **Then** the query is automatically scoped to Game A -- the developer cannot query the wrong game by accident.

---

### US-L2-GW-04 -- Realtime Sessions Authenticate on STOMP CONNECT `✓ Implemented`

> *As a game developer, I want matchmaking push events to require the same player JWT as the REST API, so that real-time notifications are only routed to the correct player.*

**Acceptance Criteria**

- **Given** the HTTP WebSocket upgrade to `/ws` is allowed without a JWT (see US-L2-GW-01),  
  **When** the client sends the STOMP `CONNECT` frame,  
  **Then** the `ChannelInterceptor` requires `Authorization: Bearer <Forge JWT>` and binds the session principal to that player's `player_id`.

- **Given** a player presents a valid Forge JWT on STOMP `CONNECT`,  
  **When** validation succeeds,  
  **Then** per-user STOMP destinations resolve to that session so `match_found` and `queue_timeout` reach only that player.

- **Given** the `CONNECT` frame omits a Bearer token, or the token is invalid or expired,  
  **When** the interceptor runs,  
  **Then** the connection attempt fails with a structured `ForgeApiException` path (`FORGE_INVALID_TOKEN`) -- unauthenticated STOMP sessions are not established.

- **Given** a player's WebSocket connection drops and they open a new socket,  
  **When** they send a new STOMP `CONNECT` with a valid JWT,  
  **Then** a new session is established -- the client may call `GET /v1/matchmaking/status` to recover queue state missed during the outage.

---

## Layer 1 -- GDScript SDK

These stories describe the developer experience of integrating Forge into a Godot game. All stories in this section are `✓ Implemented` -- behavior matches [Layer1GdScriptSdk.md](../slices/Layer1GdScriptSdk.md) and is validated by the headless runner at `client/godot/tests/run_all.gd` plus the manual cockpit at `client/godot/test_harness/cockpit.tscn`.

---

### US-L1-SDK-01 -- Add Forge to a Godot Project in Under 15 Minutes `✓ Implemented`

> *As a game developer, I want to add the Forge SDK to my Godot project quickly and with minimal friction, so that I can start integrating backend features without spending a day on setup.*

**Acceptance Criteria**

- **Given** a developer copies `addons/forge_sdk/` into their Godot project and enables it in Project Settings,  
  **When** the addon activates,  
  **Then** `ForgeSDK` is accessible from any script -- no additional installation steps are required.

- **Given** the addon is enabled,  
  **When** the developer places `forge_config.json` in the project root with `forge_base_url` and `forge_api_key`,  
  **Then** the SDK reads the config automatically on initialisation -- the developer writes no setup code beyond constructing `ForgeSDK`.

- **Given** a developer follows the quickstart documentation from an empty Godot project,  
  **When** they complete setup and make a successful `login_steam()` call,  
  **Then** the elapsed time is under 15 minutes on a documented Godot 4.x version.

---

### US-L1-SDK-02 -- Authenticate a Player via Steam from GDScript `✓ Implemented`

> *As a game developer, I want to authenticate a player with a single `await` call in GDScript, so that I don't have to understand HTTP headers, JWTs, or the Steam ticket exchange.*

**Acceptance Criteria**

- **Given** a developer calls `await forge_sdk.auth().login_steam(steam_ticket)`,  
  **When** authentication succeeds,  
  **Then** a success result is returned -- the developer never touches the JWT, Bearer header, or `X-Forge-Api-Key` directly; the SDK handles all of that internally.

- **Given** `login_steam()` succeeds,  
  **When** any subsequent SDK method is called,  
  **Then** the JWT is attached to every request automatically -- the developer calls no "set token" method.

- **Given** the JWT expires while the player is mid-session,  
  **When** the SDK detects the expiry,  
  **Then** it re-authenticates in the background and the player experience is uninterrupted -- no error surfaces to the game UI unless re-auth itself fails.

- **Given** a developer calls a leaderboard or matchmaking method without first calling `login_steam()`,  
  **When** the call is made,  
  **Then** the SDK returns a clear, readable error indicating auth is required -- no cryptic network error is surfaced.

---

### US-L1-SDK-03 -- Join the Matchmaking Queue from GDScript `✓ Implemented`

> *As a game developer, I want to enter matchmaking with a single `await` call and receive the match result via a Godot signal, so that I don't have to wire up WebSocket connections, STOMP frames, or polling loops.*

**Acceptance Criteria**

- **Given** a developer calls `forge_sdk.matchmaking().connect_realtime()` and connects a callback to the `match_found` signal,  
  **When** `await forge_sdk.matchmaking().join_queue(attributes)` is called,  
  **Then** the player is queued and the response includes the ticket details -- the developer never interacts with STOMP or WebSocket directly.

- **Given** a match is found,  
  **When** the server delivers the `match_found` event,  
  **Then** the `match_found` signal fires in GDScript with a dictionary containing `match_id`, `mode`, and `players` -- the developer's callback receives the data directly.

- **Given** the server delivers the same `match_found` event twice (at-least-once delivery),  
  **When** the SDK receives the duplicate,  
  **Then** the `match_found` signal fires only once -- deduplication by `match_id` is internal and invisible to the developer.

- **Given** a queue timeout occurs,  
  **When** the server delivers the `queue_timeout` event,  
  **Then** the `queue_timeout` signal fires in GDScript with `queue_ticket_id` and a human-readable message the developer can display directly.

---

### US-L1-SDK-04 -- Send Heartbeats to Maintain Queue Presence `✓ Implemented`

> *As a game developer, I want a simple heartbeat call that keeps a player's queue slot alive, so that I don't have to build timer management logic into my game just to prevent eviction.*

**Acceptance Criteria**

- **Given** a player is queued,  
  **When** the developer calls `await forge_sdk.matchmaking().heartbeat()` on a 10-second interval,  
  **Then** the server acknowledges the heartbeat and the queue entry stays active.

- **Given** the developer calls `heartbeat()` on a player who is no longer queued,  
  **When** the response is received,  
  **Then** the SDK returns a clear status indicating the player is not queued -- no crash or unhandled exception occurs.

---

### US-L1-SDK-05 -- Report a Match Result from GDScript `✓ Implemented`

> *As a game developer, I want to report a match result with a single `await` call, so that the leaderboard updates without me writing any reconciliation or scoring logic.*

**Acceptance Criteria**

- **Given** a developer calls `await forge_sdk.leaderboard().report_result(match_id, winner_id, loser_id)`,  
  **When** the request is processed,  
  **Then** a result object is returned with a `status` of `pending` or `completed` -- the developer does not need to understand the dual-report reconciliation process.

- **Given** the result status is `completed`,  
  **When** the developer reads the response,  
  **Then** they can confirm the leaderboard has been updated without making a separate query.

---

### US-L1-SDK-06 -- Read the Leaderboard from GDScript `✓ Implemented`

> *As a game developer, I want to fetch ranked leaderboard data and an individual player's rank with simple `await` calls, so that I can build leaderboard UI without writing any sorting or ranking logic.*

**Acceptance Criteria**

- **Given** a developer calls `await forge_sdk.leaderboard().top(page, size)`,  
  **When** the request is processed,  
  **Then** a ranked list of players is returned in correct dense-rank order -- the developer displays the data directly without further sorting.

- **Given** a developer calls `await forge_sdk.leaderboard().rank(player_id)`,  
  **When** the request is processed,  
  **Then** the response contains the player's rank, win count, and loss count -- rank does not need to be calculated from the full leaderboard.

---

### US-L1-SDK-07 -- The SDK Never Exposes Transport Internals `✓ Implemented`

> *As a game developer, I want to use Forge without ever knowing what STOMP, JWTs, or Bearer tokens are, so that I can integrate a multiplayer backend as easily as any other Godot plugin.*

**Acceptance Criteria**

- **Given** a developer uses every SDK feature (auth, matchmaking, leaderboard),  
  **When** they review their own GDScript code,  
  **Then** there is no mention of JWT, Bearer, STOMP, HTTP headers, or WebSocket frames -- all transport is encapsulated by the SDK.

- **Given** a developer reads the public SDK API,  
  **When** they inspect available methods and signals,  
  **Then** the surface is: `auth()`, `matchmaking()`, `leaderboard()`, and their sub-methods -- no internal helpers, raw clients, or transport types are exposed.

- **Given** the SDK is at a published version,  
  **When** a subsequent backend version changes an internal implementation detail,  
  **Then** the GDScript method signatures do not change -- backward compatibility is a hard constraint once a version is released.

---

### US-L1-SDK-08 -- Understand and Accept the API Key Responsibility `✓ Implemented`

> *As a game developer, I want clear documentation about where my Forge API key lives and what happens if it leaks, so that I can make an informed decision about how to distribute my game.*

**Acceptance Criteria**

- **Given** a developer reads the Forge quickstart,  
  **When** they follow the setup steps,  
  **Then** they are told plainly that the API key in `forge_config.json` must not be committed to public source control and that a leak ties directly to their Forge account.

- **Given** the developer ships a game build with the key in config,  
  **When** the game calls `POST /v1/auth/steam`,  
  **Then** the API key is sent only on this one endpoint -- all other calls use the player JWT issued after login.

- **Given** a developer suspects their API key has been leaked,  
  **When** they rotate the key through the Forge dashboard,  
  **Then** the old key stops working and the new key takes effect immediately -- a rotation path exists before public launch.

---

## Cross-Cutting Constraints

The following constraints apply to all stories across all layers.

- **Game isolation** -- All data operations are scoped to the `game_id` in the authenticated JWT or API key. A developer cannot read or modify data belonging to another game, even accidentally.

- **Stateless API** -- No server-side session state is maintained between requests. Every request is independently authenticated via JWT or API key.

- **Structured errors** -- Expected failures mapped by `GlobalExceptionHandler` or raised as `ForgeApiException` return `{ "error": { "code", "message" } }`. Generic `5xx` responses are still possible for unmapped server faults until catch-all handling is added.

- **API key posture** -- The Forge API key lives in client configuration (`forge_config.json`) so the game can contact Forge at all. The raw key is sent only on `POST /v1/auth/steam`; all other calls use the player JWT. The server stores only hashes -- the raw key is never persisted. Protecting the key from leaks is the developer's responsibility.

- **SDK backward compatibility** -- Once a GDScript method signature or API endpoint is published in a versioned release, its signature must not change. Breaking changes in the backend require a new versioned API path.

- **Happy path length** -- The end-to-end path from SDK initialisation to receiving a `match_found` signal must remain 4 to 6 developer operations. Internal complexity may grow; external complexity must not.