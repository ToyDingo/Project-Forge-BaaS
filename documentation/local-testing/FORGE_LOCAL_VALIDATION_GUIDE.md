# Forge Test Harness: Complete Setup and Testing Guide

**Version:** 1.0  
**Audience:** Anyone on the team — no prior Godot, Forge, or testing experience required.

## Validation Status Update

**Status:** Completed - PASS  
**Date:** 2026-04-28  
**Environment:** Local Forge harness validation (Godot 4.x test harness)  
**Result:** All planned validation phases were executed successfully and passed.

### Passed Scope

- Phase 0: Baseline login + me checks
- Phase 1: Auth, matchmaking, and leaderboard functional checks
- Phase 2: Two-client matchmaking symmetry and shared `match_id` verification
- Phase 3: Multi-client queue/match distribution checks
- Phase 4: Resilience/fault scenarios and recovery consistency

### Final Assessment

No Blocker or High-severity defects were observed during validation.  
Harness behavior is stable and repeatable under the documented local test plan.

---

## Part 1: Understanding the Big Picture

### What is Forge?

Forge is a backend service that handles the server-side features of a multiplayer game. Instead of building your own game server infrastructure from scratch, game developers drop Forge in and get:

- **Player login** via Steam
- **Matchmaking** (finding opponents and pairing them)
- **Leaderboards** (tracking wins, losses, rankings)

### What is the Godot SDK (L1)?

The Godot SDK is a code library that game developers put inside their Godot game project. It gives them simple one-line calls (`login_steam`, `join_queue`, etc.) without needing to know anything about web APIs, WebSockets, or JSON.

### What is the test harness?

A test harness is a minimal "fake game" that only has buttons. There is no gameplay. Each button calls one Forge feature. When you press a button, you see the result in a log window. The purpose is to test that Forge is working correctly before any real game relies on it.

### What are the 4 pillars we are testing?

1. **Auth** — Can a player log in via Steam and get a token?
2. **Matchmaking** — Can players queue up, be paired, and receive notifications?
3. **Leaderboard** — Can match results be recorded and rankings retrieved?
4. **SDK Integration** — Is the Forge SDK easy to drop into a Godot project without friction?

---

## Part 2: Software You Need

1. **Godot Engine 4.x** — the game engine used to run the harness
2. **Forge backend** — the local server (ask your team lead for the repo and startup instructions)
3. **Docker Desktop** — the tool that runs the Postgres database container
4. The **forge-test-harness** project folder (ask your team lead)

---

## Part 3: Installing Godot

1. Go to [https://godotengine.org/download](https://godotengine.org/download).
2. Download the **Standard** version for Windows (not the .NET version).
3. The download is a `.zip` file. Extract it.
4. Inside you will find `Godot_v4.x-stable_win64.exe`.
5. Move that file to a folder you can find easily, for example `C:\Tools\Godot\`.
6. Double-click the file. Godot opens.

You do not need to install anything; Godot is a single executable file.

---

## Part 4: Opening the Test Harness Project

1. In Godot's Project Manager window, click **Import**.
2. Browse to the `forge-test-harness` folder on your machine.
3. Select the `project.godot` file inside it.
4. Click **Import & Edit**.

The project opens in the Godot editor.

---

## Part 5: Understanding the Godot Editor (orientation for beginners)

When the project opens you will see several panels:

- **Scene tree** (left side) — shows the "nodes" (building blocks) in the current scene.
- **Inspector** (right side) — shows the settings for the currently selected node.
- **FileSystem** (bottom left) — shows your project files.
- **Viewport** (center) — shows a visual preview of the current scene.
- **Output/Debugger** (bottom) — shows logs and errors when you run the project.

The most important thing to know: **everything in Godot is a Node**. Buttons, labels, text boxes, and containers are all nodes. Code is attached to nodes via scripts.

---

## Part 6: What the Scene Looks Like

Open `scenes/harness_ui.tscn` from the FileSystem panel.

The scene tree should look like this:

```
HarnessUI (Control)
  Layout (VBoxContainer)
    Title (Label)
    SteamTicketInput (LineEdit)
    LoginButton (Button)
    MeButton (Button)
    JoinQueueButton (Button)
    StatusButton (Button)
    HeartbeatButton (Button)
    LeaveQueueButton (Button)
    TopButton (Button)
    RankPlayerIdInput (LineEdit)
    RankButton (Button)
    LogOutput (TextEdit)
```

A `VBoxContainer` stacks children vertically from top to bottom. Each child takes the full width. This gives you a simple top-to-bottom dashboard layout.

---

## Part 7: What Each Button Does

| Button label | Forge action it calls | Pillar |
|---|---|---|
| Login Steam | `ForgeSDK.auth().login_steam(ticket)` | Auth |
| Get Me | `ForgeSDK.auth().me()` | Auth |
| Join Queue | `ForgeSDK.matchmaking().join_queue(attrs)` | Matchmaking |
| Queue Status | `ForgeSDK.matchmaking().status()` | Matchmaking |
| Heartbeat | `ForgeSDK.matchmaking().heartbeat()` | Matchmaking |
| Leave Queue | `ForgeSDK.matchmaking().leave_queue()` | Matchmaking |
| Leaderboard Top | `ForgeSDK.leaderboard().top(page, size)` | Leaderboard |
| Leaderboard Rank | `ForgeSDK.leaderboard().rank(player_id)` | Leaderboard |

---

## Part 8: Adding the Forge Config File

Before the harness can talk to Forge, it needs to know where the server lives and what API key to use.

1. In the FileSystem panel, right-click the root folder (`res://`) and click **Open in File Manager**.
2. Create a new file called `forge_config.json` in that folder.
3. Open it in a text editor and paste this:

```json
{
  "forge_base_url": "http://127.0.0.1:8080",
  "forge_api_key": "YOUR_DEV_API_KEY_HERE"
}
```

4. Replace `YOUR_DEV_API_KEY_HERE` with the actual dev API key from your Forge setup.
5. Replace `8080` with whatever port your local Forge backend uses if it is different.
6. Save the file.

---

## Part 9: Wiring the Buttons (the code)

The harness scene has all the buttons but clicking them does nothing yet. We need to wire them to the Forge SDK.

### How wiring works in Godot

In Godot, you connect a button's `pressed` signal to a function in a script. When the button is clicked, Godot calls that function automatically. We do this in code inside `_ready()`, which runs automatically when the scene loads.

### The complete harness script

Open `scripts/harness_ui.gd` in the Godot editor by double-clicking it in the FileSystem panel.

Replace its entire contents with the following:

```gdscript
extends Control

@onready var log_output: TextEdit = $Layout/LogOutput
@onready var ticket_input: LineEdit = $Layout/SteamTicketInput
@onready var rank_id_input: LineEdit = $Layout/RankPlayerIdInput

func _ready() -> void:
    $Layout/LoginButton.pressed.connect(_on_login_pressed)
    $Layout/MeButton.pressed.connect(_on_me_pressed)
    $Layout/JoinQueueButton.pressed.connect(_on_join_queue_pressed)
    $Layout/StatusButton.pressed.connect(_on_status_pressed)
    $Layout/HeartbeatButton.pressed.connect(_on_heartbeat_pressed)
    $Layout/LeaveQueueButton.pressed.connect(_on_leave_queue_pressed)
    $Layout/TopButton.pressed.connect(_on_top_pressed)
    $Layout/RankButton.pressed.connect(_on_rank_pressed)

    ForgeSDK.matchmaking().match_found.connect(_on_match_found)
    ForgeSDK.matchmaking().queue_timeout.connect(_on_queue_timeout)

    append_log("Harness ready.")

# --- Auth ---

func _on_login_pressed() -> void:
    var ticket := ticket_input.text.strip_edges()
    if ticket == "":
        append_log("ERROR: enter a ticket string before clicking Login Steam.")
        return
    append_log("Calling login_steam with ticket: " + ticket)
    var result: ForgeResult = await ForgeSDK.auth().login_steam(ticket)
    _log_result("login_steam", result)

func _on_me_pressed() -> void:
    append_log("Calling me...")
    var result: ForgeResult = await ForgeSDK.auth().me()
    _log_result("me", result)

# --- Matchmaking ---

func _on_join_queue_pressed() -> void:
    append_log("Connecting realtime and joining queue...")
    ForgeSDK.matchmaking().connect_realtime()
    var attrs := {"mode": "ranked_1v1", "client_version": "1.0", "region": "us-central1"}
    var result: ForgeResult = await ForgeSDK.matchmaking().join_queue(attrs)
    _log_result("join_queue", result)

func _on_status_pressed() -> void:
    var result: ForgeResult = await ForgeSDK.matchmaking().status()
    _log_result("status", result)

func _on_heartbeat_pressed() -> void:
    var result: ForgeResult = await ForgeSDK.matchmaking().heartbeat()
    _log_result("heartbeat", result)

func _on_leave_queue_pressed() -> void:
    var result: ForgeResult = await ForgeSDK.matchmaking().leave_queue()
    _log_result("leave_queue", result)

# --- Leaderboard ---

func _on_top_pressed() -> void:
    var result: ForgeResult = await ForgeSDK.leaderboard().top(1, 10)
    _log_result("top", result)

func _on_rank_pressed() -> void:
    var player_id := rank_id_input.text.strip_edges()
    if player_id == "":
        append_log("ERROR: enter a player ID before clicking Leaderboard Rank.")
        return
    var result: ForgeResult = await ForgeSDK.leaderboard().rank(player_id)
    _log_result("rank", result)

# --- Realtime signals ---

func _on_match_found(event: Dictionary) -> void:
    append_log("*** MATCH FOUND *** match_id=" + str(event.get("match_id", "?")))
    append_log("  Full event: " + JSON.stringify(event))

func _on_queue_timeout(event: Dictionary) -> void:
    append_log("*** QUEUE TIMEOUT *** ticket=" + str(event.get("queue_ticket_id", "?")))
    append_log("  Full event: " + JSON.stringify(event))

# --- Helpers ---

func _log_result(action: String, result: ForgeResult) -> void:
    if result.ok:
        append_log("[OK] " + action + " | data: " + JSON.stringify(result.data))
    else:
        append_log("[FAIL] " + action + " | code: " + result.error_code + " | " + result.error_message)

func append_log(msg: String) -> void:
    var ts := Time.get_datetime_string_from_system()
    log_output.text += "[%s] %s\n" % [ts, msg]
    log_output.scroll_vertical = log_output.get_line_count()
```

Save the file with `Ctrl+S`.

### What this code does

- `_ready()` runs automatically when the scene loads. It wires every button to a function and subscribes to realtime events.
- Every button handler calls one Forge SDK method and logs the result.
- `_log_result` prints `[OK]` or `[FAIL]` so you can see at a glance whether a call worked.
- `_on_match_found` and `_on_queue_timeout` fire automatically when the server pushes a realtime event — no button press required.

---

## Part 10: Known Compatibility Notes

Before testing, be aware of two known setup issues on Godot 4.6:

1. **Type inference errors** (`Cannot infer the type of "result" variable`) in SDK service files — fixed by using explicit types (`ForgeResult`, `String`) in the affected lines. See `GODOT_FORGE_SDK_TYPE_INFERENCE.md` for details.

2. **NUL character parser spam** at startup — caused by STOMP null-terminator handling in GDScript string literals. Fixed by using `PackedByteArray` for frame termination. See `GODOT_46_STOMP_NULL_PARSER_COMPAT.md` for details.

If either of these appears, apply the documented fixes before continuing.

---

## Part 11: Verifying the Setup Before Testing

1. Confirm Docker Desktop is running and the Postgres container is active.
2. Confirm the Forge backend is running.
3. Open the harness project in Godot.
4. Press **F5** to run the project.
5. A window appears showing the dashboard.
6. Check that the log box says `Harness ready.`
7. If there are errors in the Godot output panel, stop and resolve them before continuing.

---

## Part 12: Running the Tests

### Queue attrs used in all matchmaking tests

All clients must use these identical attrs for pairing to work:

```json
{
  "mode": "ranked_1v1",
  "client_version": "1.0",
  "region": "us-central1"
}
```

These are already hardcoded into the Join Queue button handler above.

---

## Phase 0: Baseline — Does anything work at all?

1. In the ticket field, type: `player_a`
2. Click **Login Steam**

Expected log output:
```
[OK] login_steam | data: {"access_token": "...", "token_type": "Bearer", "expires_in": ...}
```

3. Click **Get Me**

Expected:
```
[OK] me | data: {"player_id": "...", "game_id": "...", "platform": "steam"}
```

**If both pass: baseline confirmed. Continue.**  
**If either fails: stop. Fix the backend/config before continuing.**

---

## Phase 1A: Auth Tests (single client)

### Test A1: Normal login
- Type any non-empty string in ticket field
- Click **Login Steam**
- Expected: `[OK]`

### Test A2: Empty ticket (negative path)
- Clear the ticket field completely
- Click **Login Steam**
- Expected: `[FAIL]` with a clear error message about empty ticket

### Test A3: Protected call after login
- Login first (A1)
- Click **Get Me**
- Expected: `[OK]` with player identity

---

## Phase 1B: Matchmaking Tests (single client)

### Test M1: Join queue
- Login first
- Click **Join Queue**
- Expected: `[OK] join_queue` with a `queue_ticket_id` in the data

### Test M2: Status while queued
- After joining, click **Queue Status**
- Expected: `[OK]` with `status: queued`

### Test M3: Heartbeat
- Click **Heartbeat**
- Expected: `[OK]` with `next_heartbeat_due_in_seconds`

### Test M4: Leave queue
- Click **Leave Queue**
- Expected: `[OK]` with `status: left_queue`

### Test M5: Status after leaving
- Click **Queue Status**
- Expected: `[OK]` with `status: not_queued`

### Test M6: Timeout path (solo wait)
- Login and click **Join Queue**
- Do NOT click Heartbeat at all
- Wait at least 60 seconds
- Expected: `*** QUEUE TIMEOUT ***` event appears in log automatically

---

## Phase 1C: Leaderboard Tests (single client)

### Test L1: Top leaderboard
- Login first
- Click **Leaderboard Top**
- Expected: `[OK]` with a list of ranked entries (or empty array if no matches have been recorded yet)

### Test L2: Rank lookup
- Copy the `player_id` value that appeared in your `me` response
- Paste it into the Player ID field
- Click **Leaderboard Rank**
- Expected: `[OK]` with rank data, or a clear not-found error if the player has no stats yet

### Test L3: Negative report validation
- This test requires calling `report_result` from code or a tool (not in the UI yet)
- Submit an invalid payload (same player as winner and loser)
- Expected: deterministic validation error code

---

## Phase 2: Two-Client Matchmaking (critical)

You need **two running harness instances** simultaneously.

### How to open two instances

**Option A (simplest):** Export a build from Godot:
1. `Project -> Export`
2. Run both the exported `.exe` and the editor version (F5) at the same time.

**Option B (command line):** While the editor run is open, launch a second instance:
```
"C:\Tools\Godot\Godot_v4.x-stable_win64.exe" --path "C:\path\to\forge-test-harness"
```

### Steps
1. Client A: type `player_a` in ticket field, click **Login Steam**
2. Client B: type `player_b` in ticket field, click **Login Steam**
3. Client A: click **Get Me** — record the `player_id` value
4. Client B: click **Get Me** — record the `player_id` value
5. Client A: click **Join Queue**
6. Client B: click **Join Queue** (immediately after)
7. Wait up to 10 seconds

### What to verify
- Both logs show `*** MATCH FOUND ***`
- Both show the **same `match_id`** value
- Neither client shows the match event more than once

---

## Phase 3: Multi-Client Test (3+ clients, recommended first pass: 10)

### Burst test (all at once)
1. Launch N clients, each with a unique ticket: `player_a` through `player_j`
2. Login all clients
3. Connect realtime on all clients (handled automatically on Join Queue)
4. Click **Join Queue** on all clients within about 30 seconds
5. Count match events in each client log

**Expected for 10 clients in 1v1:** 5 match pairs formed; all 10 clients receive `match_found`

### What to log per client
- Did this client receive `match_found`?
- What `match_id` did it receive?
- Was the same `match_id` received by exactly one other client?
- Did any client get stuck with no terminal event after 90 seconds?

### Staggered test
1. Same as burst but join in waves — for example 2 clients every 10 seconds
2. Compare: do later joiners wait longer? Are queue states still clean between waves?

---

## Phase 4: Resilience and Fault Tests

### R1: Kill a client before delivery
1. Queue two compatible clients (A and B)
2. After both click **Join Queue**, immediately close Client B's window
3. Watch Client A's log for at least 30 seconds
4. Expected: retries happen on the backend; match eventually cancels; Client A logs return to queued state

### R2: Reconnect and recover
1. Reopen Client B
2. Login again with `player_b`
3. Click **Queue Status**
4. Expected: status is coherent (not stuck inside a cancelled match)

### R3: Stale heartbeat eviction
1. Login a client and click **Join Queue**
2. Do not click **Heartbeat** at all
3. Wait approximately 30 seconds (2 missed heartbeat windows at 10s each)
4. Expected: a stale removal status or timeout event appears automatically in the log

### R4: Repeatability check
- Repeat each resilience case at least twice
- Expected: behavior is consistent across runs, not random

---

## Part 13: Evidence Template

Fill in one block per test run. Keep these in a shared doc or spreadsheet.

```
Scenario ID:       (e.g. A1, M3, Phase2-run1)
Date/Time:
Tester name:
Client(s) used:    (e.g. Client A = player_a, Client B = player_b)
Forge backend branch/version:
Preconditions:     (e.g. "fresh login", "already queued", "no prior state")
Steps taken:       (numbered list)
Observed output:   (exact text from log box)
Expected output:
Verdict:           PASS / FAIL
If FAIL:           exact error_code + error_message + timestamp + backend log excerpt
```

---

## Part 14: Defect Severity Rubric

| Severity | Examples |
|---|---|
| **Blocker** | Cannot log in at all; pairing never happens under valid conditions; match IDs don't match between clients; unrecoverable stuck queue state |
| **High** | Retry/cancel path loses a player permanently; stale entries persist after removal window; duplicate match events survive SDK dedupe |
| **Medium** | Confusing error messages; inconsistent log verbosity; intermittent failures with a clear workaround |
| **Low** | Cosmetic log formatting; minor wording issues in error messages |

---

## Part 15: Phase Sign-Off Criteria

| Phase | Gate |
|---|---|
| Phase 0 | Login + me both pass on a clean start |
| Phase 1 | All A1-A3, M1-M6, L1-L2 return expected results |
| Phase 2 | Symmetric `match_found` confirmed; same `match_id` on both clients |
| Phase 3 | No stuck/orphaned clients; correct number of matches formed |
| Phase 4 | System converges after every fault type; no ghost state |

All phases must pass before cloud testing begins.

## Final Sign-Off

All local validation phases have been completed and passed.  
Project is approved to proceed to the cloud handoff checklist.

---

## Part 16: Cloud Handoff Checklist (next phase, not now)

Only proceed to GCP testing after all local phases pass with no Blocker or High severity defects:

- Validate HTTPS/WSS behavior behind cloud load balancer
- Validate JWT key injection via GCP Secret Manager
- Validate Pub/Sub event bus adapter (replace `InProcessEventBusAdapter`)
- Validate observability: structured logs, correlation IDs, metrics dashboards
- Validate distributed rate limiting at scale

---

*Document version: 1.0 — written for Forge harness / Godot 4 local validation.*
