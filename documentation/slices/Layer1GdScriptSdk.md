# Layer 1 - Forge GDScript SDK - Requirements and Design

This document defines the requirements, API contract, architecture, and acceptance criteria for Layer 1 of the Forge Backend platform: the GDScript SDK.

## Implementation Status

**Implemented.** The SDK ships as a Godot 4.3 addon at `client/godot/addons/forge_sdk/`. Every section below maps to live code; the public API surface in section 7 is locked under Commandment 7.

What is in place today:

- Addon scaffolding (`plugin.cfg`, `plugin.gd`, autoload `ForgeSDK`).
- `forge_config.json` loader with readable `FORGE_SDK_NOT_CONFIGURED` errors when the file is missing or incomplete; example template at `client/godot/forge_config.example.json`.
- `ForgeAuth.login_steam` and `ForgeAuth.me` with automatic JWT attach, in-memory token storage, and transparent re-auth on `FORGE_INVALID_TOKEN`.
- `ForgeMatchmaking.join_queue`, `leave_queue`, `status`, and `heartbeat`. Heartbeat scheduling is left to the developer per section 7.3.
- `ForgeMatchmaking` realtime channel (`connect_realtime`, `disconnect_realtime`) with `match_found` and `queue_timeout` Godot signals. `match_found` is deduped internally by `match_id`.
- `ForgeLeaderboard.report_result`, `top`, and `rank` with full `LEADERBOARD_*` error mapping through `ForgeResult`.
- Backend `ForgeErrorCode` names mirrored verbatim in `addons/forge_sdk/internal/forge_errors.gd` so game code can branch on the same constants the server emits.
- Headless test runner at `client/godot/tests/run_all.gd` covering every US-L1-SDK acceptance criterion plus a public-surface scan that fails if any transport term leaks into `services/*.gd`.
- Manual cockpit at `client/godot/test_harness/cockpit.tscn` with one button per public call.

What stayed deferred for this release (per section 13 and `decisions/FreezeNowDeferSafely.md`):

- HTTP and WebSocket retry/backoff policy; the SDK ships with simple, documented behavior only.
- STOMP auto-reconnect; developers call `connect_realtime()` again and use `await ForgeSDK.matchmaking().status()` to resync.
- Disk persistence of the JWT (in-memory only).

See [Layer1SdkDesignDiscussion.md](../discussions/Layer1SdkDesignDiscussion.md) for the full design discussion that produced this document. See [`WhatWasImplemented.md`](../foundations/WhatWasImplemented.md#layer-1-gdscript-sdk) for the cross-cutting summary.

---

## 1. Objective

The Forge GDScript SDK is a low-friction, drop-in client library for Godot developers who want to add multiplayer backend functionality to their game without becoming backend engineers.

Primary success condition:

- A solo developer can add Forge to a new Godot project in under 15 minutes.
- The developer never touches STOMP, JWT headers, Bearer auth, or backend wiring.
- The SDK handles all transport, auth lifecycle, and event delivery internally.

---

## 2. Scope

### In scope for MVP

1. Godot addon distribution (`addons/forge_sdk/` with `plugin.cfg`).
2. `forge_config.json` as the configuration artifact.
3. Steam authentication via `auth().login_steam(steam_ticket)`.
4. JWT lifecycle management (automatic, transparent to the developer and player).
5. Matchmaking: join queue, leave queue, status, heartbeat.
6. Realtime match events over WebSocket: `match_found`, `queue_timeout` as Godot signals.
7. At-least-once event delivery with internal `match_found` deduplication by `match_id`.
8. Leaderboard: report result, get top players, get player rank.
9. Basic structured logs from SDK internals.

### Out of scope for MVP

- C# SDK (post-MVP consideration).
- Other engines: Unity, Unreal (explicitly deferred).
- Other identity platforms: PSN, Xbox (explicitly deferred).
- Admin or management surface in the SDK (web UI only).
- In-game debug panel or exportable session trace.
- Advanced / power-user STOMP API.
- HTTP and WebSocket retry policy (dedicated design pass before implementation).

---

## 3. Non-Functional Requirements

- Setup time: under 15 minutes from addon installation to first successful API call.
- All STOMP, JWT, and transport concerns are internal. Zero exposure in the public API.
- JWT expiry while a player is queued must be transparent to the player. Re-auth happens in the background.
- Signals are used for server-pushed events; `await`-friendly methods are used for request/response calls.
- The Forge API key is a **game-level credential** the developer places in config (or equivalent). The game must send it so Forge can identify the title on `POST /v1/auth/steam`. **Protecting the key from leaks is the developer's responsibility**; a leaked key ties directly to their Forge account.
- Basic structured logging must be on by default, with verbosity configurable via `forge_config.json`.
- Once a public method or signal is versioned and shipped, its signature must not change.

---

## 4. Godot Version Support Policy

- The SDK targets modern Godot 4.x as the certified baseline.
- Backward compatibility is extended only when functionality can be tested and guaranteed for a given version.
- Any Godot version that cannot be certified is listed as unsupported, not as "may work."
- The certified version floor will be declared after the first verified test run against a pinned Godot version.

---

## 5. Distribution

- Format: **Godot addon**.
- Location in project: `addons/forge_sdk/`.
- Requires `plugin.cfg` with addon metadata.
- Distributed via the Godot Asset Library (Phase 2 launch) and as a direct download.
- Installation: add the `addons/forge_sdk/` folder to the Godot project; enable the addon in Project Settings.

---

## 6. Configuration

### Primary artifact

`res://forge_config.json` in the Godot project root.

### Sources

A developer can produce this file in two ways:

1. Write it manually using the documented JSON schema.
2. Generate it from the Forge web UI and drop it into the project.

Both paths produce the same format. The web UI option allows zero-JSON configuration for developers who prefer a visual setup.

### MVP config schema (proposed)

```json
{
  "forge_base_url": "https://api.forge-backend.com",
  "forge_api_key": "<issued when the developer registers their game on Forge>",
  "log_level": "info",
  "auth": {
    "auto_refresh_on_expiry": true,
    "proactive_refresh_before_expiry": true,
    "log_token_events": true
  }
}
```

The **Forge API key** is issued when a developer signs up and registers a game. They put it in this file (or load it from a mechanism they choose). The SDK reads it and sends it as `X-Forge-Api-Key` on the Steam auth exchange, matching the Layer 2 gateway contract. The developer is responsible for not exposing the key in public repos, streams, or builds they do not control.

---

## 7. Public API Contract (Layer 1)

### 7.1 Entry point

```gdscript
var forge = ForgeSDK.new()
```

Or as a project autoload so it is available globally:

```gdscript
# In any script after autoload is configured
ForgeSDK.auth().login_steam(steam_ticket)
```

### 7.2 Auth

```gdscript
# Login via Steam ticket
var result = await forge.auth().login_steam(steam_ticket)

# Get current player info
var player = await forge.auth().me()
```

- Auth must succeed before any other service call is made.
- JWT is stored and attached automatically on every subsequent call.
- On expiry, the SDK re-authenticates transparently using the same Steam ticket or a refresh path (design TBD).

### 7.3 Matchmaking

```gdscript
# Connect to realtime event channel (call before joining queue)
forge.matchmaking().connect_realtime()

# Subscribe to match events
forge.matchmaking().match_found.connect(_on_match_found)
forge.matchmaking().queue_timeout.connect(_on_queue_timeout)

# Join queue
var ticket = await forge.matchmaking().join_queue({
    "mode": "ranked_1v1",
    "client_version": "1.0.0",
    "region": "us-central1"
})

# Send heartbeat (every 10 seconds while queued)
await forge.matchmaking().heartbeat()

# Check queue status (recovery path)
var status = await forge.matchmaking().status()

# Leave queue (idempotent)
await forge.matchmaking().leave_queue()

# Disconnect realtime channel
forge.matchmaking().disconnect_realtime()
```

### 7.4 Match events (signals)

```gdscript
# match_found signal payload
func _on_match_found(event: Dictionary) -> void:
    # event contains: event_type, match_id, mode, players, created_at, expires_at, connect_hint
    pass

# queue_timeout signal payload
func _on_queue_timeout(event: Dictionary) -> void:
    # event contains: event_type, queue_ticket_id, message
    pass
```

- `match_found` is deduped by `match_id` internally. Duplicate events from at-least-once delivery are silently dropped.
- Signals are Godot-native and follow standard `connect()` / `disconnect()` patterns.

### 7.5 Leaderboard

```gdscript
# Report a match result
var report = await forge.leaderboard().report_result(match_id, winner_id, loser_id)

# Get top players (paginated)
var top = await forge.leaderboard().top(page, size)

# Get a specific player's rank
var rank = await forge.leaderboard().rank(player_id)
```

---

## 8. Happy Path (4-6 Operations)

Per the project commandments, the happy path must be 4 to 6 client operations. For a player joining a match:

1. `await forge.auth().login_steam(steam_ticket)` - authenticate.
2. `forge.matchmaking().connect_realtime()` + subscribe to `match_found` signal - open event channel.
3. `await forge.matchmaking().join_queue(attrs)` - enter matchmaking.
4. `await forge.matchmaking().heartbeat()` every 10 seconds while waiting.
5. Receive `match_found` signal - match is found. Session begins.

That is 4 to 5 operations for the game developer to wire up. Heartbeat is a recurring call, not a one-time setup step.

---

## 9. Error Handling

- SDK surfaces errors at three levels:
  - **Raw backend error codes:** available in the response object for developers who need them (for example `MATCHMAKING_ALREADY_QUEUED`).
  - **Mapped SDK-level errors:** readable constants or enums the game can switch on.
  - **Log output:** all errors emit a structured log entry by default.
- Fatal errors (for example, auth failure, network unreachable after retries) emit an error signal in addition to logging.
- Non-fatal errors (for example, duplicate queue join) are returned as result objects, not exceptions.

---

## 10. API Key and Security Model

The Forge API key is a **game-level credential** issued when a developer registers with Forge. **It lives in the client configuration** so the game can call Forge at all. The SDK uses it only where the backend requires it today, primarily the Steam ticket exchange (`POST /v1/auth/steam` with header `X-Forge-Api-Key`).

**Developer responsibility:** Any leak of the key is effectively access to that developer's Forge project for that game. The SDK docs and quickstart should state this plainly and recommend practices such as: keep keys out of public version control, use export presets or environment-specific config for release builds, and rotate the key through Forge if a leak is suspected.

**Note:** This model differs from a strict "keys never in shipped clients" rule. That tradeoff is an explicit product choice for low-friction indie integration. Stricter patterns (dedicated auth proxy, server-side exchange only) can be revisited post-MVP if customers need them.

---

## 11. Internal Architecture Notes

The following are internal concerns. They do not appear in the public API.

- STOMP session is managed by an internal `_ForgeStompClient` (or equivalent) hidden from the public API.
- JWT is stored in memory only (not persisted to disk) and attached to every outbound HTTP request automatically.
- `match_id` seen in previous `match_found` signals is tracked in a `Set` to implement deduplication.
- Log output is routed through a single internal logger that respects the `log_level` config value.
- HTTP requests use Godot's `HTTPRequest` node or `HTTPClient` (exact choice deferred to implementation based on tested patterns in the supported Godot version).

---

## 12. Acceptance Criteria (Done Definition)

A developer can:

1. Add the Forge addon to a Godot project.
2. Place `forge_config.json` with at minimum `forge_base_url` and `forge_api_key`.
3. Construct or reference `ForgeSDK`.
4. Call `login_steam` and receive a success result.
5. Call `connect_realtime` and `join_queue`.
6. Receive `match_found` via signal with a valid `match_id`.
7. Confirm duplicate `match_found` with the same `match_id` does not fire the signal twice.
8. Call leaderboard endpoints and receive valid ranked results.

Without touching STOMP, JWT, Bearer headers, or HTTP wiring directly. Total time from empty Godot project to step 8: under 15 minutes on a documented Godot 4.x version.

---

## 13. Phase 2 Notes

- Expand Godot version support matrix as certifications are completed.
- Retry and backoff policies for HTTP and WebSocket: subject of a dedicated design pass before implementation.
- C# SDK: post-MVP, likely after GDScript SDK is stable.
- Platform expansion: PSN, Xbox deferred.
- Per-game web UI configuration: web UI generates `forge_config.json`; SDK reads the same format.
- Observability expansion: pluggable logger hooks, exportable session trace.

---

## 14. Reference

- Design doc section: v0.2.1, Section 4 (Layer 1 Client).
- Design discussion: [Layer1SdkDesignDiscussion.md](../discussions/Layer1SdkDesignDiscussion.md).
- Commandments: [Commandments.md](../foundations/Commandments.md).
- Backend API quickstart: [README.md](../../README.md).
- Backend error codes: [README.md](../../README.md#error-codes).
