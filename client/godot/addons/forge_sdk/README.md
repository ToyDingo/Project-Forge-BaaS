# Forge SDK for Godot

The drop-in Forge Backend client for Godot 4.x. Steam authentication,
matchmaking, and leaderboards in five operations from a fresh project.

This addon hides every transport detail. You will not write JWT headers,
bearer strings, STOMP frames, WebSocket lifecycle, or HTTP plumbing.

- Backend repo: see the project root `README.md`.
- Full design and acceptance criteria: see
  `documentation/slices/Layer1GdScriptSdk.md`.

---

## Quickstart (target: under 15 minutes)

### 1. Install the addon

Copy the entire `addons/forge_sdk/` folder into your Godot project's
`addons/` directory. In Godot, open **Project -> Project Settings ->
Plugins** and enable **Forge SDK**.

Enabling the plugin registers a global autoload named `ForgeSDK` so the
SDK is available from any script.

### 2. Drop in `forge_config.json`

Copy `forge_config.example.json` (in this repo at `client/godot/`) to your
project root as `forge_config.json` and fill in the real values:

```json
{
  "forge_base_url": "http://localhost:8080",
  "forge_api_key": "YOUR_RAW_FORGE_API_KEY",
  "log_level": "info",
  "auth": {
    "auto_refresh_on_expiry": true,
    "proactive_refresh_before_expiry": true,
    "log_token_events": true
  }
}
```

Keep this file out of public source control. See
[API key responsibility](#api-key-responsibility) below.

### 3. Use the SDK

```gdscript
extends Node

func _ready() -> void:
    # 1. Authenticate via Steam.
    var auth_result = await ForgeSDK.auth().login_steam(steam_ticket)
    if not auth_result.ok:
        push_error("Auth failed: %s" % auth_result.error_message)
        return

    # 2. Open the realtime event channel and subscribe to match events.
    ForgeSDK.matchmaking().match_found.connect(_on_match_found)
    ForgeSDK.matchmaking().queue_timeout.connect(_on_queue_timeout)
    ForgeSDK.matchmaking().connect_realtime()

    # 3. Join the queue.
    var ticket = await ForgeSDK.matchmaking().join_queue({
        "mode": "ranked_1v1",
        "client_version": "1.0.0",
        "region": "us-central1",
    })
    if not ticket.ok:
        push_error("Queue join failed: %s" % ticket.error_message)

    # 4. Send a heartbeat every 10 seconds while waiting (timer omitted for brevity).
    # await ForgeSDK.matchmaking().heartbeat()

func _on_match_found(event: Dictionary) -> void:
    # event has match_id, mode, players, created_at, expires_at, connect_hint
    print("Matched: ", event["match_id"])

func _on_queue_timeout(event: Dictionary) -> void:
    print("Queue timed out: ", event["message"])
```

That is the entire happy path. After the match completes, both clients
report the outcome:

```gdscript
var report = await ForgeSDK.leaderboard().report_result(match_id, winner_id, loser_id)
var top = await ForgeSDK.leaderboard().top(1, 10)
var my_rank = await ForgeSDK.leaderboard().rank(my_player_id)
```

---

## Public API

```
ForgeSDK
  .auth() -> ForgeAuth
    await login_steam(steam_ticket: String) -> ForgeResult
    await me() -> ForgeResult
  .matchmaking() -> ForgeMatchmaking
    connect_realtime() -> void
    disconnect_realtime() -> void
    signal match_found(event: Dictionary)
    signal queue_timeout(event: Dictionary)
    await join_queue(attrs: Dictionary) -> ForgeResult
    await leave_queue() -> ForgeResult
    await status() -> ForgeResult
    await heartbeat() -> ForgeResult
  .leaderboard() -> ForgeLeaderboard
    await report_result(match_id, winner_id, loser_id) -> ForgeResult
    await top(page, size) -> ForgeResult
    await rank(player_id) -> ForgeResult
```

Every call returns a `ForgeResult` with:

- `ok` (bool) - true on success, false on any failure.
- `data` (Dictionary) - parsed response body on success.
- `error_code` (String) - stable code such as `MATCHMAKING_ALREADY_QUEUED`.
- `error_message` (String) - human-readable text safe to log or show in dev UI.

Compare `error_code` against the constants in
`addons/forge_sdk/internal/forge_errors.gd` if your game branches on
specific failures.

---

## Configuration reference

| Field | Required | Notes |
|-------|----------|-------|
| `forge_base_url` | yes | Base URL of the Forge backend, no trailing slash. |
| `forge_api_key` | yes | Raw Forge API key for your game. |
| `log_level` | no | `debug`, `info` (default), `warn`, `error`. |
| `auth.auto_refresh_on_expiry` | no | Reserved for Phase 2 polishing; transparent re-auth already runs on `FORGE_INVALID_TOKEN`. |
| `auth.proactive_refresh_before_expiry` | no | Same. |
| `auth.log_token_events` | no | Adds verbose log lines around token issuance. |

To override config from a test or harness without writing a file:

```gdscript
ForgeSDK.init_with_config({
    "forge_base_url": "http://localhost:8080",
    "forge_api_key": "test-key",
    "log_level": "debug"
})
```

---

## API key responsibility

The Forge API key is a game-level credential. You drop it into
`forge_config.json` so your shipped game can talk to Forge at all.

This is an explicit product choice for low-friction indie integration. It
also means **protecting that key from leaks is your responsibility**.

Practical checklist:

- Add `forge_config.json` to `.gitignore`. The example file in this addon
  is the only one safe to commit.
- Use export presets to inject release keys at build time rather than
  bundling a single key into every artifact.
- Rotate the key from the Forge dashboard if you suspect a leak. The old
  key stops working immediately and the new key takes effect.
- A leaked key ties directly to your Forge account. Treat it like any
  other production credential.

The SDK sends the raw key only on `POST /v1/auth/steam` so the backend can
identify the title. Every other call uses the player JWT issued at login.

---

## Realtime channel notes

- `connect_realtime()` opens the WebSocket and subscribes to the per-user
  matchmaking destinations. Call it after `login_steam()` succeeds.
- The SDK does NOT auto-reconnect on disconnect (Phase 2 work). If the
  channel drops, call `connect_realtime()` again. Use `await
  ForgeSDK.matchmaking().status()` to recover queue state.
- Duplicate `match_found` events are dropped internally, so connecting a
  callback once is safe.

---

## Backward compatibility

Once a public method or signal is shipped in a versioned release, its
signature does not change. Internal modules under `internal/` may evolve
freely between releases.

---

## Layout

```
addons/forge_sdk/
  plugin.cfg              -- addon metadata
  plugin.gd               -- registers the ForgeSDK autoload
  forge_sdk.gd            -- root entry point
  README.md               -- this file
  CHANGELOG.md
  services/
    forge_auth.gd
    forge_matchmaking.gd
    forge_leaderboard.gd
  internal/
    forge_config.gd
    forge_errors.gd
    forge_result.gd
    forge_logger.gd
    forge_jwt_store.gd
    forge_http_client.gd
    forge_stomp_client.gd
```
