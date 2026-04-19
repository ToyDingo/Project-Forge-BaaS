# Discussion summary (secretary-style notes)

**Purpose:** Consolidated record of decisions, Q&A, architecture, and follow-ups from this thread. Paste into your own file as needed.

---

## 1. Layers vs languages — Q&A

**Question:** Layer 1 in GDScript; is Layer 2 also GDScript, maybe inside the SDK with Layer 1?

**Answer:** **Layer 1 (client/SDK) = GDScript** for Godot devs. **Layer 2 (API gateway) is not GDScript** and **does not live in the SDK**. It is **server-side** (in the PDF design: **Spring Boot**). The SDK only **calls** the gateway over HTTPS/WebSocket.

**Nuance:** Layer 1 also includes a **browser dashboard** (not GDScript); the design doc marks the dashboard as **Phase 2 / out of MVP**.

---

## 2. How Layer 2 exists server-side — Q&A

**Question:** How does Layer 2 work if Layer 1 must reach Layer 3 through it?

**Answer:**

- **Logical gateway** (recommended for MVP): **same Spring Boot deployable** as core services — security filters, rate limits, routing by path prefix; domain code in packages. One public API, one process.
- **Physical gateway** (later): dedicated edge (e.g. Envoy, API Gateway) in front of services; more ops overhead.

**Request path:** SDK → HTTPS to public host → **Layer 2** (auth, limits, routing) → **Layer 3** controllers/services → DB / messaging / WS as designed.

**Recommendation:** Define an **endpoint credential matrix** early (`public` | `api-key` | `jwt` | combinations) so gateway rules stay unambiguous.

**Capability:** Assistance with **GDScript** (including production-style patterns) was confirmed.

---

## 3. Build order — your plan vs recommendation

**Your plan:** Layer 3 first → Layer 2 → Layer 4 (Postgres) → Layer 1 (GDScript; new to Godot, dummy game).

**Pushback:** Do not sequence **Layer 2 and Layer 3 in separate silos** — gateway policy and API contracts **co-evolve** with services. **Layer 4 (Postgres) is needed early for auth**, not after Layer 2/3 are “done.”

**Recommended order:**

1. **Contract-first** — auth endpoints, JWT claims, tenant/game scoping, rate limits.
2. **Auth vertical slice** — **Layer 2 + Layer 3 + Layer 4 together** (register/login or equivalent, JWT, DB, tests).
3. **Connection slice** — authenticated **WebSocket** (identity at handshake), session binding, heartbeats — **before** full matchmaking complexity.
4. **Matchmaking** — Pub/Sub, pairing, push over WS.
5. **Minimal Layer 1** — small Godot sandbox to avoid late integration surprises.

---

## 4. Auth first, then connection? — Q&A

**Question:** End-to-end auth across L2–L4 first, then connection second?

**Answer:** **Yes.** **“Connection”** was refined as:

- **First:** authenticated WebSocket / session binding (no matchmaking yet).
- **Second:** matchmaking and async events.

**Rationale:** Realtime features need a **trusted player identity**; building WS before auth usually means rework.

---

## 5. “WS” — Q&A

**Question:** What does “WS” mean?

**Answer:** **WebSocket** — persistent channel for server push (e.g. match-found), vs polling HTTP.

---

## 6. Platform login (Steam / PSN / Xbox) — discussion

**Your point:** Many games do not use a per-game username/password; they use **Steam, PSN, Xbox** as identity.

**Agreement:** That model fits: **platform proves identity** → backend **maps to an internal player** → issues **Forge JWT** (or similar) for your APIs.

**Caveats:**

- You still typically maintain **internal `player_id`** and **platform account linking**.
- **Steam, PSN, and Xbox differ** in APIs, tokens, and certification — **do not implement all three in the first slice.**

**Scoped decision for this thread:** **Steam + Godot indie only** for now.

---

## 7. Docker locally, then cloud — Q&A

**Your view:** MVP on **Docker locally**, then deploy to cloud; few pitfalls expected.

**Assessment:** **Reasonable.** Pitfalls to avoid:

- **Parity:** config via env, secrets not baked in, migrations from day one.
- **Outbound calls:** Steam validation needs **HTTPS from the container**.
- **WS in prod:** TLS (`wss://`), LB idle timeouts, proxy behavior — test before launch.
- **JWT keys and DB:** paths and URLs must not be **localhost-only** assumptions.

**“Definition of done” suggested:** `docker compose up` → app + Postgres; scripted or manual proof of **Steam auth E2E**; CI running same stack where possible.

---

## 8. Cloud migration checklist (memory / `CloudMigration.md`)

**Your request:** Track everything that must change for **Docker → cloud**.

**Delivered in chat:** A **running checklist** covering: config/secrets, Postgres → managed DB, migrations, TLS/wss, rate limiting scale-out, health probes, Steam egress, WS behind LB, observability, CI/CD image parity, deferred Redis/Pub/Sub/multi-region.

**File:** You asked to save as **`CloudMigration.md`** and refer back when asked.

**Persistence note:** Chat-based “memory” is **not guaranteed across unrelated future sessions** unless you keep a **file (e.g. `CloudMigration.md`)** or repeat context.

---

## 10. Glossary of terms used in thread

| Term        | Meaning |
|------------|---------|
| **Layer 1** | Client: Godot SDK (+ future dashboard) |
| **Layer 2** | API gateway: auth, limits, routing (server-side) |
| **Layer 3** | Core business services (Java/Spring in doc) |
| **Layer 4** | Data & infra: Postgres, cache, messaging, compute |
| **WS**      | WebSocket |
| **JWT**     | Bearer tokens for API authorization after login |
| **Vertical slice** | Thin end-to-end feature across stack |
| **Steam-first**    | Current scope: validate Steam identity before other platforms |

---

## 11. Open / deferred items (not fully resolved in thread)

- Exact **Steam** flow (session ticket format, Web API validation steps, error codes).
- **API key** vs **JWT** on each route (full matrix).
- **Refresh tokens** vs short-lived access only.
- **Multi-tenancy** (`game_id` / API key) detailed schema.
- **Leaderboard integrity** if scores are client-submitted (post-auth topic).
- **Exact cloud target** (GCP per original doc vs AWS) — migration checklist stays provider-agnostic where possible.

---

*End of consolidated notes.*