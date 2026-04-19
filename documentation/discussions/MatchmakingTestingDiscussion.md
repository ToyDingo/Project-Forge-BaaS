# Matchmaking Testing Discussion

This document captures the testing strategy discussion for the matchmaking vertical slice.
It reflects an MVP-first, local-first approach with a planned cloud migration path.

## Context and Constraints

- Team size is one developer.
- There is no hard deadline.
- This is an MVP with a narrow goal: connect two players reliably.
- Development and validation are local first on a laptop.
- The solution will later move to cloud infrastructure.
- Local scaffolding should be removable or replaceable during cloud migration.

## Testing Goal

Prove that matchmaking can reliably queue, match, notify, and clean up players under normal and failure conditions, while keeping implementation overhead appropriate for a solo MVP build.

## Five Core Testing Strategies

## 1) Deterministic Service-Layer State Tests

Test the core `MatchmakingService` logic using controlled dependencies.

What to validate:

- join queue happy path
- duplicate join conflict
- leave idempotency
- heartbeat updates
- stale eviction after missed heartbeats
- timeout eviction at 60 seconds
- match formation rules for 1v1

Pros:

- very fast feedback
- low maintenance cost
- precise control over edge cases

Cons:

- does not prove transport behavior
- can miss database and transaction integration issues

## 2) Database Integration Tests

Run integration tests against a real database setup (preferably Postgres container) to validate persistence correctness.

What to validate:

- uniqueness and active queue constraints
- concurrent join behavior
- state transitions persisted correctly
- cleanup behavior after timeout, leave, and stale removal

Pros:

- catches schema and query errors early
- validates concurrency safety

Cons:

- slower than unit tests
- more setup and CI cost

## 3) WebSocket Contract Tests

Test authenticated WebSocket sessions and event delivery contracts end to end.

What to validate:

- authenticated WebSocket connect
- `match_found` event payload shape
- `queue_timeout` event payload shape
- expected client-side dedupe behavior by `match_id`

Pros:

- validates core real-time user experience
- confirms event contracts are stable

Cons:

- asynchronous tests can be flaky without careful timing controls
- more complex harness than REST-only tests

## 4) Reliability and Retry Fault Injection Tests

Deliberately force notification failures and validate retry and cancellation behavior.

What to validate:

- retries happen exactly 3 times
- retries are 5 seconds apart
- 1v1 match cancels after retry exhaustion
- queue and match state remain consistent after failure

Pros:

- validates critical failure-path behavior
- increases confidence before cloud rollout

Cons:

- more complex to implement
- may require controllable clocks and fake transports

## 5) Local Load and Soak Tests

Run sustained local traffic patterns to expose resource leaks and queue stability issues.

What to validate:

- queue depth remains bounded
- stale entries are eventually cleaned
- time-to-match remains reasonable under local load
- system returns to clean baseline after test run

Pros:

- exposes operational issues not seen in unit tests
- builds confidence for first cloud push

Cons:

- needs useful metrics to interpret outcomes
- may produce noisy results on laptop hardware

## Why Use All Five, But in Phases

All five strategies are recommended, but not all at full depth on day one.

Primary risk of doing everything at once:

- slower progress to first working MVP
- increased test maintenance burden while APIs are still changing
- longer and flakier local and CI runs

Recommended sequence:

1. deterministic service tests
2. database integration tests
3. WebSocket contract tests
4. retry and failure tests
5. local load and soak tests

## Local-First Test Stack

Use local components first, while keeping interfaces compatible with cloud adapters.

Suggested local stack:

- Spring Boot app locally
- local Postgres or Testcontainers Postgres
- authenticated WebSocket endpoint locally
- in-memory or local adapter for async event bus
- scheduler or worker loop for matchmaking processing

Cloud migration principle:

- keep business logic independent from transport and broker implementation
- replace local adapters with cloud adapters later

## Local Scaffolding Policy for Cloud Transition

The local scaffolding should be:

- isolated behind interfaces
- easy to replace when cloud adapters are ready
- removable without changing core matchmaking rules

When moving to cloud, replace:

- local event bus adapter with GCP Pub/Sub adapter
- local environment wiring with cloud profile wiring

Keep:

- business logic
- API and event contracts
- most tests, especially service and contract tests

## MVP Defaults Confirmed in Discussion

- queue timeout: 60 seconds
- heartbeat cadence: every 10 seconds
- stale removal: after 2 missed heartbeats
- notification delivery: at-least-once
- notification retry policy: 3 retries, 5 seconds apart
- duplicate join: conflict
- leave when not queued: idempotent 200 no-op

## Suggested Exit Criteria Before First Cloud Push

1. service tests pass for all queue lifecycle transitions
2. database integration tests pass for constraints and concurrency
3. WebSocket contract tests pass for required events
4. retry and cancellation behavior is verified
5. local soak test shows no queue leakage or stuck state
6. manual two-player local test works repeatedly

## Final Guidance

Do not push to cloud just to discover basic logic bugs.
Use local testing to harden behavior first, then run targeted cloud validation for cloud-specific concerns such as Pub/Sub delivery, IAM, TLS, and load balancer WebSocket behavior.
