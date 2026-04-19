# Layer 1 - Forge GDScript SDK - Design Discussion

This document captures the outcomes of the product and engineering discussion for Layer 1: the GDScript SDK.

## Context

Completed backend layers:

- Layer 4: Data and Infrastructure (PostgreSQL, Flyway migrations V1-V5)
- Layer 3: Core Services (Auth, Leaderboard, Matchmaking)
- Layer 2: API Gateway (Spring Security filter chain, JWT, STOMP/WebSocket)

Next target:

- Layer 1: Client-facing SDK

Discussion goal:

- Define what the Forge SDK is and what it is not
- Nail down architecture, API shape, and distribution before implementation
- Ensure external complexity stays low per the project commandments

---

## Clarification: What Layer 1 Is

Layer 1 is **not** a test harness or a game. It is a **low-friction GDScript SDK** that game developers drop into their Godot projects to consume Forge backend services.

A separate internal test harness game (for manual QA) is explicitly out of scope for Layer 1. That harness will be built as an internal validation tool and is not a Forge deliverable.

---

## Final Decisions

### Product identity

- Working name: **Forge SDK** (subject to change).
- First-class language: **GDScript** (native to the target indie Godot audience).
- C# SDK is a post-MVP consideration.
- Other engines (Unity, Unreal) are explicitly deferred.

### Distribution format

- Distributed as a **Godot addon** under `addons/forge_sdk/` with a `plugin.cfg`.
- This mirrors how the Godot ecosystem handles third-party libraries: add to project, enable in project settings, use.
- A plain copy-paste folder approach was considered but rejected in favor of the addon format for versioning and update ergonomics.

### Godot version support policy

- Start with **modern Godot 4.x** as the certified baseline.
- Expand backward compatibility only when functionality can be **tested and guaranteed** for that version.
- If a version cannot be guaranteed, it is **not listed as supported**. No hedging.
- No specific minor version floor is declared until the first certified test run is complete.

### Configuration

- **Primary configuration artifact:** `forge_config.json` in the game project root (`res://forge_config.json`).
- Configuration can be provided by:
  - The developer writing the file manually.
  - The Forge web UI generating the file and the developer dropping it into the project.
- Both sources ultimately write the same format and are stored in Layer 4.
- JSON was chosen over Godot-native `.tres` resources because it is engine-agnostic and easy for the web UI to generate and for developers to read and diff.

### API key posture

- When a developer signs up for Forge, they receive a **Forge API key** for their game. They **put that key in client configuration** (for example `forge_config.json`) so the game can contact Forge at all.
- The SDK reads the key and uses it where Layer 2 requires it, especially `X-Forge-Api-Key` on `POST /v1/auth/steam`. After login, the SDK uses the **player JWT** for protected routes.
- **It is on the developer not to expose the key** (public repos, streams, shared builds). A leak ties directly to their Forge account. Documentation should say this clearly.
- This is an explicit product choice: **low-friction integration** over hiding the key entirely. Alternatives such as a server-only exchange can be revisited post-MVP for teams that need stricter separation.

### Public API shape

- Single root entry point: `ForgeSDK` object (or matching top-level autoload).
- Sub-services accessed via method chaining, for example:

```gdscript
forge_sdk.matchmaking().join_queue(attributes)
forge_sdk.leaderboard().report_result(match_id, winner_id, loser_id)
```

- This mirrors a Java developer's mental model of importing a package, constructing a client, and calling service methods.

### Auth lifecycle

- **Auth is mandatory before any other service can be used** (documented contract).
- Developers may import any sub-module they want, but unauthed calls will fail with a clear error.
- **JWT is managed automatically** by the SDK (store, attach as Bearer header, detect expiry).
- Config toggles planned for v1: auto-refresh on expiry, proactive refresh before expiry, log token events.
- **If JWT expires while a player is queued**, the SDK re-authenticates in the background. The player experience must be seamless; no error should surface to the game UI unless re-auth itself fails.

### Realtime transport (WebSocket / STOMP)

- The SDK uses STOMP over WebSocket to receive server-push events, matching what the Forge backend already exposes.
- **STOMP is an internal implementation detail.** Game developers never interact with STOMP frames directly.
- Public surface:
  - `connect_realtime()` to open the STOMP session.
  - `disconnect()` to close it.
  - **Godot signals** for pushed events: `match_found`, `queue_timeout`.
- An advanced / power-user STOMP API may be exposed later, but is out of scope for MVP.

### Event delivery and deduplication

- `match_found` uses at-least-once delivery semantics (inherited from backend).
- The SDK **always deduplicates `match_found` by `match_id` internally**. Duplicate events are never surfaced to the game.
- This is not configurable in MVP. It is the only correct behavior.

### Async and signal style

- **HTTP request/response calls:** primary style is `await`, matching Godot 4 idioms.
- **Server-pushed events (WebSocket):** primary style is **Godot signals**.
- This gives developers a familiar pattern: `await` reads like sequential code for calls; signals hook up listeners for async events.
- Example:

```gdscript
# HTTP call - await style
var result = await forge_sdk.auth().login_steam(steam_ticket)

# Event - signal style
forge_sdk.matchmaking().match_found.connect(_on_match_found)
```

### Admin and developer tools

- **No admin surface in the SDK.** All game configuration and API key management happens through the Forge web UI.
- The SDK is purely a runtime client library. It does not expose management, provisioning, or dashboard functionality.

### Observability from the SDK

- **Basic structured logs** by default.
- No in-game debug panel or exportable trace for MVP.
- Log verbosity should be configurable via config.

### HTTP and WebSocket retry policy

- Intentionally **deferred** for a dedicated design discussion before implementation.
- The principle agreed is: no one-size-fits-all retry; each endpoint type will be evaluated individually.

### Backward compatibility

- **Hard constraint**: once a method is published in a versioned SDK release, its signature must not change.
- Breaking backend changes must be versioned at the API level to preserve SDK stability.
- Versions of Godot where SDK functionality cannot be guaranteed are not listed as supported.

### Documentation artifacts (deferred)

- Quickstart, API reference, error code guide, matchmaking integration recipe, and migration notes are all desired.
- Documentation scope is **not blocking** the current design phase and will be planned separately.

### Done criteria for Layer 1

A Godot developer can:

1. Add the Forge addon to their project.
2. Drop in `forge_config.json` with `forge_base_url` and `forge_api_key`.
3. Construct `ForgeSDK`.
4. Complete Steam auth.
5. Join the matchmaking queue.
6. Receive `match_found` and `queue_timeout` via signals with deduped `match_id`.
7. Call leaderboard endpoints.

Without ever touching STOMP, JWT plumbing, or raw HTTP wiring. Target setup time: 15 minutes.

---

## Open Items Tracked for Later

- HTTP and WebSocket retry policy: dedicated design pass before implementation.
- Backward compatibility testing matrix per Godot minor version: establish after first certified test run.
- Documentation artifacts: planned separately, not blocking design.

---

## Relationship to Design Document (v0.2.1)

Section 4.1 of the v0.2.1 design document describes the intended GDScript SDK at a high level. This discussion document expands and refines those decisions with concrete choices for distribution format, API shape, config, auth lifecycle, transport abstraction, and developer ergonomics.

The design document marks Layer 1 as Phase 2. This discussion formalizes the decisions needed to proceed with implementation when Phase 2 begins.
