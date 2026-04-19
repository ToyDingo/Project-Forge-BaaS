# Code Structure

Layered architecture. Every class resides in exactly one top-level package under
`server/src/main/java/com/forgebackend/`.

```
com.forgebackend
├── controller/          Layer 2 - HTTP gateway (single file, zero logic)
│   └── ForgeGatewayController.java
│
├── service/             Layer 3 - Business logic
│   ├── AuthService.java
│   ├── ForgeJwtService.java
│   ├── LeaderboardService.java
│   └── MatchmakingService.java
│
├── repository/          Layer 4 - Data access (Spring Data JPA)
│   ├── GameRepository.java
│   ├── PlayerRepository.java
│   ├── MatchReportRepository.java
│   ├── PlayerStatsRepository.java
│   ├── CompletedMatchRepository.java
│   ├── MatchmakingQueueEntryRepository.java
│   ├── MatchmakingMatchRepository.java
│   └── MatchmakingMatchPlayerRepository.java
│
├── entity/              Layer 4 - JPA entities (DB table mappings)
│   ├── Game.java
│   ├── Player.java
│   ├── MatchReport.java
│   ├── PlayerStats.java
│   ├── CompletedMatch.java
│   ├── MatchmakingQueueEntry.java
│   ├── MatchmakingMatch.java
│   └── MatchmakingMatchPlayer.java
│
├── dto/                 Data transfer objects (request/response shapes)
│   ├── SteamAuthRequest.java
│   ├── TokenResponse.java
│   ├── ErrorResponse.java
│   ├── MatchResultRequest.java
│   ├── MatchReportResponse.java
│   ├── LeaderboardEntryResponse.java
│   ├── LeaderboardPageResponse.java
│   ├── PlayerRankResponse.java
│   ├── QueueJoinRequest.java
│   ├── QueueJoinResponse.java
│   ├── QueueStatusResponse.java
│   ├── QueueLeaveResponse.java
│   └── HeartbeatResponse.java
│
├── exception/           Error codes + global exception handler
│   ├── ForgeErrorCode.java
│   ├── ForgeApiException.java
│   └── GlobalExceptionHandler.java
│
├── security/            Filters + tokens (Spring Security plumbing)
│   ├── ForgeApiKeyAuthenticationFilter.java
│   ├── ForgeApiKeyHasher.java
│   ├── ForgeGameAuthenticationToken.java
│   ├── ForgeJwtAuthenticationFilter.java
│   └── ForgeJwtAuthenticationToken.java
│
├── config/              Spring configuration + properties
│   ├── SecurityConfig.java
│   ├── ForgeBackendConfiguration.java
│   ├── ForgeJwtProperties.java
│   ├── ForgeSteamProperties.java
│   ├── ForgeMatchmakingProperties.java
│   └── WebSocketConfig.java
│
├── steam/               Steam Web API integration
│   ├── SteamClient.java              (interface)
│   ├── SteamWebApiClient.java        (production)
│   ├── DevOnlySteamClientStub.java   (dev only)
│   └── SteamTicketValidationResult.java
│
├── matchmaking/         Matchmaking orchestration + event transport
│   ├── ForgeEventBus.java            (interface, swappable for GCP Pub/Sub later)
│   ├── InProcessEventBusAdapter.java (@Primary, pushes STOMP)
│   ├── MatchFoundEvent.java          (WebSocket payload record)
│   ├── QueueTimeoutEvent.java        (WebSocket payload record)
│   ├── MatchmakingOrchestrator.java  (transactional match formation + delivery)
│   └── MatchmakingCamelRoutes.java   (timer scans + retry route)
│
├── devtools/            Dev-only utilities (not deployed)
│   └── SeedDevGame.java
│
└── ForgeBackendApplication.java      Spring Boot entry point
```

## Layering Rules

1. **Layer 2 (controller/)** - Pure gateway. No business logic, no direct repository calls.
   Every method delegates to a Layer 3 service.

2. **Layer 3 (service/)** - All business logic lives here. Transaction boundaries,
   validation, reconciliation, JWT issuance, synchronous matchmaking operations
   (join, leave, status, heartbeat).

3. **Layer 4 (repository/ + entity/)** - Data access and persistence. Repositories
   expose queries; entities map tables.

4. **Cross-cutting** - `security/`, `config/`, `exception/`, `dto/`, `steam/`,
   `matchmaking/` are shared infrastructure. They do not contain business rules
   belonging to a specific feature controller.

## Notes on the `matchmaking/` Package

The `matchmaking/` package mirrors the same convention as `steam/`: it groups
integration-adjacent code (event transport + async orchestration) behind a thin
interface so it can be swapped without touching controller or service layers.

- `ForgeEventBus` is the only abstraction service code depends on when pushing
  player-directed events. The MVP binding pushes via Spring STOMP; the cloud
  binding will push via GCP Pub/Sub with no call-site changes.
- `MatchmakingOrchestrator` owns all multi-player database state transitions
  triggered by the Camel timer routes. The synchronous `MatchmakingService` in
  `service/` stays focused on per-caller HTTP operations.
- `MatchmakingCamelRoutes` contains the timer-driven matchmaker and eviction
  loops plus the retry policy for `match_found` delivery. Routes are deliberately
  thin; they call the orchestrator and do not touch repositories directly.

Per the project commandments, internal complexity may grow inside these packages
as long as the external client contract in the controller layer stays stable
and the client-facing happy path remains 4 to 6 operations.
