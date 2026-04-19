# Layer 4 — design discussion summary (secretary-style notes)

**Purpose:** Record what was agreed about **Layer 4 (persistence)** for the **auth vertical slice**: scope, tables, and boundaries — for pasting into your own markdown file.

---

## 1. Order of work vs Layers 2 and 3

**Agreed:** Stand up **Layer 4 first in Docker** — specifically **PostgreSQL** (Compose, volume, credentials via env) so **Layers 2 and 3** have a real database for the auth slice.

**Nuance:** For this slice, Layer 4 means **Postgres + migration path** — not Redis, Pub/Sub, or full cloud infra yet.

---

## 2. First-pass idea: one table with username, password hash, service name

**Idea:** A single table with **username**, **password_hash**, **service name**, **creation_date** — minimal MVP.

**Pushback:**

- This slice targets **Steam-first** auth, not classic **username/password** login. For Steam you typically store a **stable platform id** (e.g. SteamID64) and **platform** (`steam`), not a password hash.
- **Display names** are not stable identity; **SteamID64** (or equivalent) is.
- If **password** login is only for local dev, that should be called out explicitly so it does not silently replace the Steam design.

**Conclusion:** Prefer **platform identity columns** for the Steam slice; **password_hash** only if you deliberately add a separate dev/password path.

---

## 3. Separate `games` table — agree or feature creep?

**Your view:** Add a **registered games** table for organization; supports **multiple games** and avoids treating identity as global (e.g. same display name on different services/games).

**Assessment:** **Agree** — this is **not** meaningful feature creep.

- **One small table** (`games`) plus **`players` → `games` FK** is normal **tenant/game scoping**.
- It **reduces** future pain vs. a single flat table with ambiguous “service name” on every row.
- **Creep to avoid:** extra product features on `games` (billing, regions, etc.) until needed.

---

## 4. Final schema agreement (MVP slice)

**Agreed:** **Two tables** for now:

| Table     | Role |
|-----------|------|
| **`games`** | Registered games on the service — **organizational / tenant** boundary. |
| **`players`** | Rows used by the service to recognize **who may act as a player for which game** (Steam identity scoped per game). |

**Relationship:** `players` references **`games`** (foreign key).

**Uniqueness:** Enforce uniqueness on **`(game_id, platform, platform_user_id)`** (or the chosen stable platform id), **not** on a global username alone.

**Primary keys:** Use a proper surrogate key (**UUID or bigserial**) for `games` and `players` — not username as PK.

---

## 5. Terminology: “game session” vs these tables

**Clarification:** Phrases like “authenticate into a **game session**” can mix:

- **Identity / registration** — what **`games` + `players`** model (who is allowed for which game).
- **Match / realtime session** — queues, rooms, match IDs — **later** tables and services.

These two tables support **login and authorization context**, not the full **multiplayer session lifecycle** by themselves.

---

## 6. Open items (not closed in this thread)

*(Superseded by implementation—see §7.)*

---

## 7. As implemented (repo state)

**Manual SQL (root `db/`):**

- [db/001_create_games_and_players.sql](db/001_create_games_and_players.sql) — `games` (`id`, `name`, `api_key_hash`, `created_at`), `players` (`id`, `game_id`, `platform`, `platform_user_id`, `display_name`, `created_at`), unique `(game_id, platform, platform_user_id)`.
- [db/002_games_steam_and_api_key_lookup.sql](db/002_games_steam_and_api_key_lookup.sql) — adds `api_key_lookup_hash` (partial unique index where not null), `steam_app_id`, `steam_web_api_key` (TEXT).
- [db/003_leaderboard_match_reports_and_player_stats.sql](db/003_leaderboard_match_reports_and_player_stats.sql) — adds `match_reports` (dual-client reports) and `player_stats` (wins/losses aggregate for ranking).
- [db/004_completed_matches_idempotency.sql](db/004_completed_matches_idempotency.sql) — adds `completed_matches` tombstones to prevent replay/double-counting after report cleanup.

**Flyway (app-managed):** same schema in `server/src/main/resources/db/migration/` as `V1__` / `V2__` / `V3__` / `V4__` for automatic migration on startup.

**Forge API key on `games`:** `api_key_lookup_hash` = SHA-256 (hex, lowercase) of the raw key for lookup; `api_key_hash` = BCrypt of the raw key for verification (L2). No separate password column for Steam-first auth.

**Steam (per game):** `steam_app_id` + `steam_web_api_key` on `games`; used by L3 to call Steam’s Web API. Not the Forge JWT signing keys (those are env/PEM; see README and CloudMigration).

**Seeding:** insert `games` rows via admin SQL or tooling; compute hashes with the same algorithms as the server (`ForgeApiKeyHasher`, `BCryptPasswordEncoder`).

---

*End of Layer 4 discussion notes.*