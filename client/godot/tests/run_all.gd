extends SceneTree

##
## Headless test runner for the Forge GDScript SDK.
##
## Run from `client/godot/`:
##
##     godot --headless --quit --script res://tests/run_all.gd
##
## Each test is a `func test_<name>(): -> void` on this script. The runner
## awaits each in order, prints PASS/FAIL, and exits non-zero on any
## failure so it can be wired into CI later.
##
## Coverage map (US-L1-SDK-XX from documentation/discussions/UserStories.md):
##   - test_config_missing_file_returns_readable_error            -> US-L1-SDK-01
##   - test_config_missing_required_field_returns_readable_error  -> US-L1-SDK-01
##   - test_login_steam_happy_path_stores_jwt                     -> US-L1-SDK-02
##   - test_protected_call_before_login_returns_auth_required     -> US-L1-SDK-02
##   - test_transparent_reauth_on_invalid_token                   -> US-L1-SDK-02
##   - test_join_queue_happy_path                                 -> US-L1-SDK-03
##   - test_match_found_signal_dedupes_by_match_id                -> US-L1-SDK-03 + Done #7
##   - test_queue_timeout_signal_emits_payload                    -> US-L1-SDK-03
##   - test_heartbeat_returns_clean_status                        -> US-L1-SDK-04
##   - test_heartbeat_when_not_queued_returns_clean_status        -> US-L1-SDK-04
##   - test_report_result_happy_path                              -> US-L1-SDK-05
##   - test_report_result_invalid_payload_returns_error           -> US-L1-SDK-05
##   - test_top_returns_ranked_items                              -> US-L1-SDK-06
##   - test_rank_player_not_found_returns_mapped_code             -> US-L1-SDK-06
##   - test_public_surface_has_no_transport_terms                 -> US-L1-SDK-07
##

const StubHttp := preload("res://tests/stubs/stub_http_client.gd")
const StubStomp := preload("res://tests/stubs/stub_stomp_client.gd")
const ForgeAuthT := preload("res://addons/forge_sdk/services/forge_auth.gd")
const ForgeMatchmakingT := preload("res://addons/forge_sdk/services/forge_matchmaking.gd")
const ForgeLeaderboardT := preload("res://addons/forge_sdk/services/forge_leaderboard.gd")
const ForgeJwtStoreT := preload("res://addons/forge_sdk/internal/forge_jwt_store.gd")
const ForgeLoggerT := preload("res://addons/forge_sdk/internal/forge_logger.gd")
const ForgeConfigT := preload("res://addons/forge_sdk/internal/forge_config.gd")

var _failures: Array[String] = []
var _passed: int = 0


func _initialize() -> void:
	await _run()
	if _failures.is_empty():
		print("\n[ForgeSDK Tests] PASS (%d tests)" % _passed)
		quit(0)
	else:
		print("\n[ForgeSDK Tests] FAIL (%d failures)" % _failures.size())
		for line in _failures:
			print("  - " + line)
		quit(1)


func _run() -> void:
	var tests := [
		"test_config_missing_file_returns_readable_error",
		"test_config_missing_required_field_returns_readable_error",
		"test_login_steam_happy_path_stores_jwt",
		"test_protected_call_before_login_returns_auth_required",
		"test_transparent_reauth_on_invalid_token",
		"test_join_queue_happy_path",
		"test_match_found_signal_dedupes_by_match_id",
		"test_queue_timeout_signal_emits_payload",
		"test_heartbeat_returns_clean_status",
		"test_heartbeat_when_not_queued_returns_clean_status",
		"test_report_result_happy_path",
		"test_report_result_invalid_payload_returns_error",
		"test_top_returns_ranked_items",
		"test_rank_player_not_found_returns_mapped_code",
		"test_public_surface_has_no_transport_terms",
	]
	for name in tests:
		print("\n--- %s ---" % name)
		await call(name)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

func _assert(condition: bool, message: String) -> void:
	if condition:
		_passed += 1
		print("  ok: " + message)
	else:
		_failures.append(message)
		print("  FAIL: " + message)


func _build_auth(http: StubHttp, jwt: ForgeJwtStore = null) -> ForgeAuthT:
	if jwt == null:
		jwt = ForgeJwtStoreT.new()
	var logger := ForgeLoggerT.new()
	var auth := ForgeAuthT.new()
	root.add_child(auth)
	auth.configure(http, jwt, logger)
	return auth


func _build_matchmaking(http: StubHttp, stomp: StubStomp, jwt: ForgeJwtStore = null) -> ForgeMatchmakingT:
	if jwt == null:
		jwt = ForgeJwtStoreT.new()
	var logger := ForgeLoggerT.new()
	var config := ForgeConfigT.from_dictionary({
		"forge_base_url": "http://localhost:8080",
		"forge_api_key": "test-key",
	})
	var mm := ForgeMatchmakingT.new()
	root.add_child(mm)
	mm.configure(http, stomp, config, jwt, logger)
	return mm


func _build_leaderboard(http: StubHttp) -> ForgeLeaderboardT:
	var lb := ForgeLeaderboardT.new()
	root.add_child(lb)
	lb.configure(http)
	return lb


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

func test_config_missing_file_returns_readable_error() -> void:
	var config := ForgeConfigT.load_from_path("res://does_not_exist.json")
	_assert(not config.is_loaded(), "config without file should not be loaded")
	_assert(config.load_error_code == ForgeErrors.FORGE_SDK_NOT_CONFIGURED, "missing config emits FORGE_SDK_NOT_CONFIGURED")
	_assert(config.load_error_message != "", "missing config has a readable message")


func test_config_missing_required_field_returns_readable_error() -> void:
	var config := ForgeConfigT.from_dictionary({"forge_api_key": "x"})
	_assert(not config.is_loaded(), "config without forge_base_url is not loaded")
	_assert(config.load_error_code == ForgeErrors.FORGE_SDK_NOT_CONFIGURED, "missing field maps to FORGE_SDK_NOT_CONFIGURED")
	_assert("forge_base_url" in config.load_error_message, "error message names the missing field")


func test_login_steam_happy_path_stores_jwt() -> void:
	var http := StubHttp.new()
	root.add_child(http)
	http.enqueue_success("POST", "/v1/auth/steam", {"access_token": "tok-1", "token_type": "Bearer", "expires_in": 3600})
	var jwt := ForgeJwtStoreT.new()
	var auth := _build_auth(http, jwt)
	var result = await auth.login_steam("deadbeefdeadbeef")
	_assert(result.ok, "login_steam returns ok=true")
	_assert(jwt.has_token(), "JWT store holds a token after login")
	_assert(jwt.token() == "tok-1", "stored token matches server response")
	_assert(jwt.last_steam_ticket() == "deadbeefdeadbeef", "ticket remembered for transparent re-auth")


func test_protected_call_before_login_returns_auth_required() -> void:
	# Use a real ForgeHttpClient so its auth-required guard runs.
	var ForgeHttpT := preload("res://addons/forge_sdk/internal/forge_http_client.gd")
	var http := ForgeHttpT.new()
	root.add_child(http)
	var config := ForgeConfigT.from_dictionary({
		"forge_base_url": "http://localhost:8080",
		"forge_api_key": "k",
	})
	var jwt := ForgeJwtStoreT.new()
	var logger := ForgeLoggerT.new()
	http.configure(config, jwt, logger)
	var result = await http.get_json("/v1/me")
	_assert(not result.ok, "call without JWT fails")
	_assert(result.error_code == ForgeErrors.FORGE_SDK_AUTH_REQUIRED, "error code is FORGE_SDK_AUTH_REQUIRED")
	_assert(result.error_message != "", "error message is readable")


func test_transparent_reauth_on_invalid_token() -> void:
	var http := StubHttp.new()
	root.add_child(http)
	# Initial login.
	http.enqueue_success("POST", "/v1/auth/steam", {"access_token": "old", "token_type": "Bearer", "expires_in": 3600})
	var jwt := ForgeJwtStoreT.new()
	var auth := _build_auth(http, jwt)
	var first = await auth.login_steam("deadbeefdeadbeef")
	_assert(first.ok, "initial login succeeded")
	# Protected call: first response is 401 invalid token, then re-auth, then retry succeeds.
	http.enqueue_failure("GET", "/v1/me", ForgeErrors.FORGE_INVALID_TOKEN, "expired")
	# Re-auth replays last steam ticket.
	http.enqueue_success("POST", "/v1/auth/steam", {"access_token": "new", "token_type": "Bearer", "expires_in": 3600})
	# Retry of the original GET after re-auth.
	http.enqueue_success("GET", "/v1/me", {"player_id": "abc"})
	var protected = await http.get_json("/v1/me")
	_assert(protected.ok, "protected call eventually succeeded after transparent re-auth")
	_assert(jwt.token() == "new", "JWT store updated to new token")


func test_join_queue_happy_path() -> void:
	var http := StubHttp.new()
	root.add_child(http)
	var stomp := StubStomp.new()
	root.add_child(stomp)
	http.enqueue_success("POST", "/v1/matchmaking/queue", {
		"queue_ticket_id": "tkt-1",
		"status": "queued",
		"joined_at": "2026-04-15T12:00:00Z",
		"timeout_at": "2026-04-15T12:01:00Z",
	})
	var mm := _build_matchmaking(http, stomp)
	var result = await mm.join_queue({"mode": "ranked_1v1"})
	_assert(result.ok, "join_queue returns ok=true")
	_assert(String(result.data.get("status", "")) == "queued", "status comes through as 'queued'")
	_assert(http.count_for("POST", "/v1/matchmaking/queue") == 1, "exactly one queue request was sent")


func test_match_found_signal_dedupes_by_match_id() -> void:
	var http := StubHttp.new()
	root.add_child(http)
	var stomp := StubStomp.new()
	root.add_child(stomp)
	var mm := _build_matchmaking(http, stomp)
	var emitted: Array = []
	mm.match_found.connect(func(event): emitted.append(event))
	var event := {
		"event_type": "match_found",
		"match_id": "match-42",
		"mode": "ranked_1v1",
		"players": [{"player_id": "a"}, {"player_id": "b"}],
	}
	stomp.simulate_frame("/user/queue/matchmaking.match-found", event)
	stomp.simulate_frame("/user/queue/matchmaking.match-found", event)
	stomp.simulate_frame("/user/queue/matchmaking.match-found", event)
	_assert(emitted.size() == 1, "match_found signal fires exactly once for duplicate match_id")
	# A different match_id still fires.
	stomp.simulate_frame("/user/queue/matchmaking.match-found", {"match_id": "match-43", "players": []})
	_assert(emitted.size() == 2, "match_found fires again for a new match_id")


func test_queue_timeout_signal_emits_payload() -> void:
	var http := StubHttp.new()
	root.add_child(http)
	var stomp := StubStomp.new()
	root.add_child(stomp)
	var mm := _build_matchmaking(http, stomp)
	var captured := []
	mm.queue_timeout.connect(func(event): captured.append(event))
	var payload := {
		"event_type": "queue_timeout",
		"queue_ticket_id": "tkt-7",
		"message": "Queue timeout reached after 60 seconds",
	}
	stomp.simulate_frame("/user/queue/matchmaking.queue-timeout", payload)
	_assert(captured.size() == 1, "queue_timeout signal fires once")
	_assert(String(captured[0].get("message", "")).length() > 0, "payload carries human-readable message")


func test_heartbeat_returns_clean_status() -> void:
	var http := StubHttp.new()
	root.add_child(http)
	var stomp := StubStomp.new()
	root.add_child(stomp)
	http.enqueue_success("POST", "/v1/matchmaking/heartbeat", {
		"status": "queued",
		"next_heartbeat_due_in_seconds": 10,
	})
	var mm := _build_matchmaking(http, stomp)
	var result = await mm.heartbeat()
	_assert(result.ok, "heartbeat ok")
	_assert(int(result.data.get("next_heartbeat_due_in_seconds", 0)) == 10, "heartbeat surfaces interval")


func test_heartbeat_when_not_queued_returns_clean_status() -> void:
	var http := StubHttp.new()
	root.add_child(http)
	var stomp := StubStomp.new()
	root.add_child(stomp)
	http.enqueue_success("POST", "/v1/matchmaking/heartbeat", {"status": "not_queued"})
	var mm := _build_matchmaking(http, stomp)
	var result = await mm.heartbeat()
	_assert(result.ok, "heartbeat returns ok=true even when player is not queued")
	_assert(String(result.data.get("status", "")) == "not_queued", "status is reported back, no exception")


func test_report_result_happy_path() -> void:
	var http := StubHttp.new()
	root.add_child(http)
	http.enqueue_success("POST", "/v1/leaderboard/results", {
		"status": "completed",
		"message": "Match reconciled and stats updated",
	})
	var lb := _build_leaderboard(http)
	var result = await lb.report_result("m1", "winner", "loser")
	_assert(result.ok, "report_result ok")
	_assert(String(result.data.get("status", "")) == "completed", "status comes back as completed")


func test_report_result_invalid_payload_returns_error() -> void:
	var http := StubHttp.new()
	root.add_child(http)
	var lb := _build_leaderboard(http)
	var result = await lb.report_result("", "", "")
	_assert(not result.ok, "empty IDs are rejected client-side")
	_assert(result.error_code == ForgeErrors.LEADERBOARD_INVALID_RESULT, "maps to LEADERBOARD_INVALID_RESULT")


func test_top_returns_ranked_items() -> void:
	var http := StubHttp.new()
	root.add_child(http)
	http.enqueue_success("GET", "/v1/leaderboard/top?page=1&size=10", {
		"page": 1, "size": 10, "total_players": 1, "total_pages": 1,
		"items": [{"rank": 1, "player_id": "p1", "wins": 5, "losses": 0}],
	})
	var lb := _build_leaderboard(http)
	var result = await lb.top(1, 10)
	_assert(result.ok, "top ok")
	_assert((result.data.get("items", []) as Array).size() == 1, "items returned")


func test_rank_player_not_found_returns_mapped_code() -> void:
	var http := StubHttp.new()
	root.add_child(http)
	http.enqueue_failure("GET", "/v1/leaderboard/rank/missing", ForgeErrors.LEADERBOARD_PLAYER_NOT_FOUND, "no entry")
	var lb := _build_leaderboard(http)
	var result = await lb.rank("missing")
	_assert(not result.ok, "rank for missing player fails")
	_assert(result.error_code == ForgeErrors.LEADERBOARD_PLAYER_NOT_FOUND, "maps to LEADERBOARD_PLAYER_NOT_FOUND")


func test_public_surface_has_no_transport_terms() -> void:
	var banned := ["Bearer", "X-Forge-Api-Key", "STOMP", "WebSocket", "websocket", "stomp"]
	var dir := DirAccess.open("res://addons/forge_sdk/services")
	_assert(dir != null, "services directory exists")
	if dir == null:
		return
	dir.list_dir_begin()
	var name := dir.get_next()
	var any_violation := false
	while name != "":
		if name.ends_with(".gd"):
			var path := "res://addons/forge_sdk/services/" + name
			var file := FileAccess.open(path, FileAccess.READ)
			if file != null:
				var text := file.get_as_text()
				file.close()
				for term in banned:
					if term in text:
						any_violation = true
						_failures.append("transport term '%s' leaked into public surface (%s)" % [term, path])
		name = dir.get_next()
	dir.list_dir_end()
	_assert(not any_violation, "no transport terms in addons/forge_sdk/services/*.gd")
