# Matchmaking Design Discussion

This document captures the outcomes of the product and engineering discussion for the matchmaking vertical slice.

## Context

Completed slices:

- Auth vertical slice
- Leaderboard vertical slice

Next target slice:

- Matchmaking

Discussion goal:

- turn high-level product intent into concrete MVP backend behavior
- separate MVP defaults from future per-game configuration

## Final Decisions

### Product intent

- Main objective is to find and connect enough players to start game sessions.
- Fast matching is preferred.
- Players tolerate a 30 to 60 second wait.

### Scope direction

- MVP starts with 1v1.
- Architecture should be extensible to many-v-many.
- Many-v-many policies will be configurable per game later.

### Eligibility constraints for MVP

- same game
- same mode
- same client version
- same platform
- latency should be low, with developer-defined thresholds later

### Queue rules

- One active queue per player per game and mode.
- Duplicate queue attempt while active is rejected.
- Duplicate join response is conflict with machine-readable error.

### Queue timeout behavior

- Maximum queue wait is 60 seconds in MVP.
- After timeout, player is removed from queue.
- Client receives timeout message.
- Requeue is always manual.

### Queue leave behavior

- Player can explicitly leave queue.
- Leave endpoint is idempotent.
- If player is not queued, response is still `200` no-op.

### Heartbeat behavior

- Heartbeat is required for queue liveness.
- Heartbeat interval for MVP: every 10 seconds.
- Remove queue entry after 2 missed heartbeats.

### WebSocket direction

- WebSocket is used for async server push events like `match_found`.
- REST remains the command channel for join, leave, status, and heartbeat.
- Status endpoint is included as a recovery and polling fallback.

### Delivery and retries

- `match_found` delivery model is at-least-once.
- Client must dedupe using `match_id`.
- Retry policy: 3 retries, 5 seconds apart.

### Unreachable player behavior

If match notification cannot be delivered after retries:

- 1v1: cancel the match
- many-v-many: continue with remaining players if mode policy allows (future configurable policy)

### Payload expectations

Queue responses should include:

- `queue_ticket_id`
- `joined_at`
- `timeout_at`

Minimum `match_found` payload should include:

- `match_id`
- `mode`
- player list
- creation and expiry timestamps
- optional connect hint

### Deferred decisions

- Idempotency-Key request semantics are deferred to phase 2.
- Skill-based matchmaking is deferred to phase 2.
- Dynamic widening rules are deferred to phase 2.

## Engineer Notes

### Why include a status endpoint

- It allows client state recovery after disconnects.
- It provides a fallback if push delivery is delayed.
- It simplifies troubleshooting and gameplay UX resilience.

### Why at-least-once for `match_found`

- Losing a match notification is worse than receiving a duplicate.
- Duplicate handling is straightforward with `match_id` dedupe.

### Why idempotent leave

- It reduces client edge-case handling.
- It keeps behavior stable during retries and race conditions.

## Outstanding Product Topics (Non-Blocking for MVP)

- many-v-many pre-match drop policy details
- game-specific acceptance flows
- anti-cheat and leaderboard integrity hardening
- final cloud deployment constraints and quotas

## Summary

The discussion produced a clear MVP contract:

- REST commands for queue control
- WebSocket push for real-time match notifications
- strict queue lifecycle rules with timeout and heartbeat eviction
- reliable notification behavior with bounded retries
- clean extension path to per-game configurability in later phases
