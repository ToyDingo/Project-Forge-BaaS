# Web Configurables Backlog

This file tracks settings that should become configurable per game via web UI or config file in later phases.

## Matchmaking Rules

1. Maximum queue wait time
2. Heartbeat interval
3. Missed heartbeat threshold before stale removal
4. Notification retry count
5. Notification retry interval
6. Region policy (strict region, preferred region, fallback regions)
7. Maximum accepted latency threshold
8. Latency strategy (lowest average latency vs threshold-only)
9. Queue widening policy over time (if enabled)
10. Single-queue policy settings by mode

## Mode and Session Behavior

1. Allowed game modes for matchmaking
2. Team size per mode (1v1, 2v2, many-v-many)
3. Minimum players required to start a session
4. Behavior when a player leaves before match start
   - cancel session
   - continue with remaining players
   - wait for backfill
5. Match start flow
   - immediate start
   - accept/decline window
   - countdown start
6. Requeue policy after cancellation or timeout
   - manual only
   - optional auto requeue

## Payload and Integration

1. Match-found payload extensions
2. Connect hint format (server endpoint, lobby token, metadata)
3. Client version compatibility policy
4. Platform compatibility policy

## Reliability and Safety

1. Delivery mode for match notifications
   - at-most-once
   - at-least-once
2. Queue timeout message template
3. Duplicate queue join behavior and error messaging
4. Idempotency-Key enforcement settings for queue APIs
5. Rate limiting thresholds for queue endpoints

## Ranking and Fairness (Phase 2+)

1. Skill-based matchmaking enabled/disabled
2. Skill model choice (simple rating, ELO, Glicko, etc.)
3. Skill tolerance range by queue wait duration
4. Fairness vs speed preference tuning

## Operational and Observability

1. Metrics granularity per game and mode
2. Log verbosity for matchmaking lifecycle events
3. Alert thresholds for:
   - queue depth
   - timeout rates
   - notification failure rates
4. Regional deployment preferences

## Current MVP Defaults (Hardcoded Today)

- queue timeout: 60 seconds
- heartbeat interval: 10 seconds
- stale removal: after 2 missed heartbeats
- match notification retries: 3
- retry spacing: 5 seconds
- duplicate join: conflict error
- leave when not queued: idempotent 200 no-op
