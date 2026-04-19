# Freeze Now / Defer Safely
## Forge Backend MVP - Decision Snapshot (v0.2.1 follow-up)

This document captures what we are freezing now versus what we are intentionally deferring, so implementation can proceed without analysis churn.

## Scope Reminder

- Local MVP first
- Cloud target is GCP
- Matchmaking is the final MVP vertical slice in progress
- Significant post-MVP refinement is expected and planned

## Freeze Now (Implementation Decisions)

### 1) Cloud and Platform Direction
- **Freeze:** GCP-first deployment path
- **Why:** team familiarity, lower execution risk, faster delivery
- **Impact:** prioritize GCP-native integration choices now, avoid AWS abstraction work in MVP

### 2) Eventing Backbone
- **Freeze:** Pub/Sub-aligned architecture (with local dev adapter where needed)
- **Why:** team familiarity and fit for async matchmaking notifications and retries
- **Implementation note:** keep event bus behind a small interface to preserve replaceability

### 3) Orchestration Approach
- **Freeze:** Camel-friendly orchestration path for retry/timing/event routing
- **Why:** aligns with Pub/Sub and team comfort
- **Guardrail:** avoid over-engineering; keep route design minimal and focused on matchmaking needs

### 4) Match Integrity for MVP
- **Freeze:** dual-client reconciliation is sufficient for MVP
- **Why:** speed to market over anti-cheat depth in this phase
- **Known limitation:** trust model is weaker than server-authoritative results

### 5) Matchmaking Core Defaults (MVP)
- Queue timeout: 60 seconds
- Heartbeat interval: 10 seconds
- Stale removal: 2 missed heartbeats
- Notification retries: 3
- Retry spacing: 5 seconds
- Duplicate join: 409 conflict
- Leave when not queued: 200 idempotent no-op

### 6) Cloud Portability Strategy
- **Freeze:** GCP-first implementation now, portability later
- **Why:** minimizes MVP complexity and avoids premature abstraction

## Defer Safely (Track, Do Not Block MVP)

### A) Auth Lifecycle Depth
- Refresh token policy
- Token renewal UX while queued or mid-session
- Revocation and forced logout strategy

**MVP posture:** short-lived access tokens only; re-auth on expiry.

### B) Advanced Idempotency Policy
- Idempotency-Key standard for queue/report endpoints
- Key scope, TTL, and storage design
- Retry contract documentation for SDK/client

**MVP posture:** rely on DB constraints and endpoint semantics first.

### C) Anti-Cheat and Result Trust Hardening
- Server-authoritative or cryptographically stronger result verification
- Tamper resistance and abuse controls
- Integrity audit paths for disputes

**MVP posture:** deferred; revisit before public launch/beta scale.

### D) Multi-Region and Cross-Cloud Concerns
- Multi-region queue behavior
- Region failover and consistency strategy
- AWS parity design

**MVP posture:** single-region GCP-first.

## Implementation Status (MVP Matchmaking)

The matchmaking vertical slice is implemented under these freezes. Summary:

- Event bus is behind `ForgeEventBus`; the MVP `InProcessEventBusAdapter` pushes via Spring STOMP and can be swapped for a GCP Pub/Sub adapter with no call-site changes.
- Async orchestration uses Apache Camel via `MatchmakingCamelRoutes` for matchmaker scans, queue eviction, and notification retries. All database state transitions run inside `MatchmakingOrchestrator` so Camel stays transport-only.
- MVP defaults (60s timeout, 10s heartbeat, 2 missed heartbeats, 3 retries at 5s) are sourced from `forge.matchmaking.*` and documented in [WebConfigurables.md](WebConfigurables.md) for Phase 2 per-game configuration.
- Dual-client reconciliation remains the match integrity posture for MVP.
- Short-lived access tokens with re-auth on expiry remain the auth posture for MVP.

See [WhatWasImplemented.md](../foundations/WhatWasImplemented.md) and [MatchmakingVerticalSlice.md](../slices/MatchmakingVerticalSlice.md) for full shipped behavior.

## Implementation Status (Layer 1 GDScript SDK)

The Godot 4.3 addon at `client/godot/addons/forge_sdk/` is implemented. It exposes `ForgeSDK` with chained `auth()`, `matchmaking()`, and `leaderboard()` services, reads `res://forge_config.json`, manages JWT in memory with transparent re-auth on `FORGE_INVALID_TOKEN`, and delivers `match_found` and `queue_timeout` via Godot signals with `match_id` deduplication.

- Specification: [Layer1GdScriptSdk.md](../slices/Layer1GdScriptSdk.md)
- Quickstart: [client/godot/addons/forge_sdk/README.md](../../client/godot/addons/forge_sdk/README.md)
- Automated tests: `client/godot/tests/run_all.gd` (run with Godot headless; see [WhatWasImplemented.md](../foundations/WhatWasImplemented.md#layer-1-gdscript-sdk))
- Manual cockpit: `client/godot/test_harness/cockpit.tscn`

HTTP/WebSocket retry policy and STOMP auto-reconnect remain deferred per this file and the SDK design doc.

## Next Hurdle (Immediate Priority)

With the matchmaking MVP and the GDScript SDK implemented, the next highest-risk item is operational reliability under real client behavior (including a real Godot build against a running backend).

Focus this next on:
- End-to-end queue correctness during disconnect/reconnect paths.
- WebSocket transport edge cases, including missed `match_found` events and retry exhaustion behavior.
- State integrity under concurrent joins, leaves, and heartbeats.
- Baseline observability for queue depth, timeout rate, stale removals, and delivery retries/failures.

Exit criteria for this hurdle:
- Client reconnection and status recovery flow is validated with deterministic test scenarios.
- No duplicate or orphaned queue/match state after concurrent operations.
- Match delivery semantics are validated with at-least-once dedupe by `match_id`.
- Operational metrics and logs are sufficient to debug live queue incidents quickly.

## Immediate Build Guidance

Proceed with matchmaking implementation using:
- GCP-first assumptions
- Pub/Sub-compatible async design
- Camel-based orchestration where retries/timers/events are needed
- Current MVP defaults from design docs
- Deferred items tracked here, not solved inline during slice build

## Revisit Trigger Points

Re-open deferred decisions when any of the following happen:
- First cloud push readiness review
- SDK/public integration hardening
- Beta onboarding planning
- Security review before production traffic

## Ownership Notes

- This file is a working decision record, not a final architecture contract.
- Update it when decisions materially change so implementation and docs stay aligned.