# Cloud migration delta (Docker local → cloud)

Single source of truth for what must change when leaving local Docker. Extend this as decisions are locked.

## Configuration and secrets

- **Single config surface**: same env var names locally and in cloud; avoid hardcoded `localhost` except dev-only defaults.
- **Secrets**: local `.env` / Compose secrets → **Secret Manager** (GCP) or equivalent in prod.
- **JWT signing keys (RS256)**: injected via secrets in both environments; no dev-only key paths in prod.

## Data layer

- **PostgreSQL**: Compose service → **Cloud SQL** (or RDS): SSL, backups, patching, connection limits.
- **Connection strings**: JDBC URL and pool settings differ; use env-driven config (see `FORGE_DATASOURCE_*` in [README.md](../../README.md)).
- **Migrations**: **Flyway** runs from `server/src/main/resources/db/migration` on app startup; root `db/` scripts remain available for hand-applied installs. If you used manual `001` only, baseline Flyway or apply `002` once—avoid duplicating history.
- **Per-game secrets in DB**: `games.steam_web_api_key` and `games.steam_app_id` are stored per row; treat DB access as sensitive; prefer **encryption at rest** (managed DB + optional app-level encryption) in production.

## Layer 2 (gateway / edge)

- **Public URLs**: `iss` / `aud` / CORS / allowed origins — environment-specific.
- **TLS**: local may be HTTP; cloud uses **HTTPS**; WebSockets use **wss://**.
- **Rate limiting**: in-process (e.g. Bucket4j) OK for MVP locally; at scale consider **Redis-backed** or edge limits.
- **Health**: **liveness/readiness** HTTP endpoints for orchestrator (K8s, Cloud Run, etc.).

## Networking and realtime

- **Outbound HTTPS**: backend must reach **Steam Web API** from cloud (egress, firewall rules).
- **WebSockets**: behind LB/ingress — **idle timeouts**, **upgrade headers**, **sticky sessions** if ever required; validate with a proxy in Compose if issues appear.

## Identity (Steam-first slice)

- **Steam ticket validation** via outbound HTTPS to Steam Web API (`ISteamUserAuth/AuthenticateUserTicket`); ensure **egress**, DNS, and firewall rules allow this from cloud VPC.
- **Steam Web API key**: stored **per game** in `games.steam_web_api_key` (not a single global env var); each title may use its publisher key. Do not log this column.
- **Forge API key**: `games.api_key_lookup_hash` (SHA-256 hex) + `games.api_key_hash` (BCrypt); resolve and verify the same way in all environments.
- **JWT signing**: RS256 PEM pair via `FORGE_JWT_PRIVATE_KEY_PEM` / `FORGE_JWT_PUBLIC_KEY_PEM` (or classpath paths); **rotate** via secrets injection, not baked images.
- **Dev-only assets**: default `classpath:keys/dev-*.pem` and `@MockBean` Steam client in tests must **not** be relied on in prod; ensure prod path does not depend on dev-only mocks.
- **Tenant/game scoping**: JWT claims include `game_id` + player `sub`; preserve when adding more routes.

## Observability and operations

- **Logging**: structured logs; **correlation IDs**; ship to cloud logging in prod.
- **Metrics/alerts**: auth failures, latency, DB pool health — align with **Cloud Monitoring** (or equivalent).

## CI/CD

- **Same container image** in CI as in local Compose; Compose or K8s manifests as deploy contract.
- **Build**: Gradle (`server/`) uses a **Java 21** toolchain (Foojay resolver may download a JDK); CI should pin JDK 21+ or supply `ORG_GRADLE_JAVA_HOME` / toolchain provisioning so builds are reproducible.

## Deferred but cloud-relevant when enabled

- **Redis (Memorystore)**: VPC, auth, TLS.
- **Pub/Sub**: service accounts, IAM, per-env topic names.
- **Multi-region**: DNS, data residency, session — later.

---

*Last aligned with discussion: Steam + Godot indie focus; auth vertical slice implemented in Spring Boot (`server/`) with Flyway, per-game Steam keys, Forge API key + JWT; Docker Compose for app+DB still optional/future.*