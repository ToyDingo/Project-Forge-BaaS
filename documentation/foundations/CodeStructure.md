# Code Structure

Layered architecture. The repository is split between the Spring Boot backend
under `server/` and the Godot SDK under `client/godot/`. Every backend class
resides in exactly one top-level package under
`server/src/main/java/com/forgebackend/`.

## Top-level repository layout

```
GameBackend/
├── server/             Spring Boot backend (layers 2 through 4)
├── db/                 Hand-applied SQL (mirrored under server Flyway)
├── client/
│   └── godot/          Godot 4.3 host project for the SDK addon and harness
│       ├── addons/forge_sdk/   The shippable SDK (Layer 1)
│       ├── tests/              Headless GDScript test runner
│       └── test_harness/       Manual cockpit scene
├── documentation/      Foundations, architecture, decisions, slices, discussions
└── README.md
```

## Backend package layout (`server/`)

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

## GDScript SDK layout (`client/godot/`)

```
client/godot/
├── project.godot                    Bare host project for editor work and tests
├── forge_config.example.json        Template developers copy and fill in
├── .gitignore                       Excludes .godot/, .import/, real config
├── addons/
│   └── forge_sdk/                   Drop-in addon (the shippable Layer 1 SDK)
│       ├── plugin.cfg
│       ├── plugin.gd                Registers the ForgeSDK autoload
│       ├── forge_sdk.gd             Public entry point with chained services
│       ├── README.md                Quickstart and API key responsibility
│       ├── CHANGELOG.md
│       ├── services/                Public surface
│       │   ├── forge_auth.gd
│       │   ├── forge_matchmaking.gd
│       │   └── forge_leaderboard.gd
│       └── internal/                Hidden from game code
│           ├── forge_config.gd
│           ├── forge_errors.gd
│           ├── forge_result.gd
│           ├── forge_logger.gd
│           ├── forge_jwt_store.gd
│           ├── forge_http_client.gd
│           └── forge_stomp_client.gd
├── tests/                           Headless test runner + stubs
│   ├── run_all.gd
│   └── stubs/
│       ├── stub_http_client.gd
│       └── stub_stomp_client.gd
└── test_harness/
    ├── cockpit.gd
    └── cockpit.tscn                 Manual cockpit scene per design 8.4
```

### SDK layering rules

1. **Public surface** lives only in `addons/forge_sdk/forge_sdk.gd` and
   `addons/forge_sdk/services/`. Method signatures here are frozen once a
   release is shipped (Commandment 7).
2. **Internal modules** under `addons/forge_sdk/internal/` may evolve freely
   between releases. They handle config, transport, JWT storage, STOMP framing,
   and structured logging.
3. **Game projects never import internals.** The autoload exposes
   `ForgeSDK.auth()`, `ForgeSDK.matchmaking()`, and `ForgeSDK.leaderboard()`,
   plus their methods and signals. Nothing else.
4. **No transport terms in the public surface.** Words like `Bearer`,
   `X-Forge-Api-Key`, `STOMP`, and `WebSocket` must not appear in
   `addons/forge_sdk/services/*.gd`. The headless test runner enforces this
   with a source scan.
