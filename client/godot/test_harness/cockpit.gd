extends Control

##
## Manual cockpit for the Forge SDK.
##
## Drive every public SDK call from buttons, see every `ForgeResult` and
## every realtime signal in the log panel. This is the design 8.4
## developer cockpit; it doubles as a smoke test against a locally running
## Forge backend (see project root README.md for `./gradlew bootRun`).
##

const StubBaseUrl := "http://localhost:8080"

@onready var _steam_ticket: LineEdit = $Layout/Inputs/SteamTicket
@onready var _mode: LineEdit = $Layout/Inputs/Mode
@onready var _match_id: LineEdit = $Layout/Inputs/MatchId
@onready var _winner_id: LineEdit = $Layout/Inputs/WinnerId
@onready var _loser_id: LineEdit = $Layout/Inputs/LoserId
@onready var _player_id: LineEdit = $Layout/Inputs/PlayerId
@onready var _log: TextEdit = $Layout/Log


func _ready() -> void:
	_log.text = ""
	_log_line("Forge SDK cockpit ready. Backend URL is sourced from forge_config.json.")
	if not ForgeSDK.is_ready():
		var err = ForgeSDK.last_init_error()
		_log_line("[init error] %s: %s" % [err.code, err.message])
	ForgeSDK.matchmaking().match_found.connect(_on_match_found)
	ForgeSDK.matchmaking().queue_timeout.connect(_on_queue_timeout)


func _on_login_pressed() -> void:
	var ticket := _steam_ticket.text.strip_edges()
	_log_line("> auth.login_steam(%s)" % ticket)
	var result = await ForgeSDK.auth().login_steam(ticket)
	_log_result("auth.login_steam", result)


func _on_me_pressed() -> void:
	_log_line("> auth.me()")
	var result = await ForgeSDK.auth().me()
	_log_result("auth.me", result)


func _on_connect_realtime_pressed() -> void:
	_log_line("> matchmaking.connect_realtime()")
	ForgeSDK.matchmaking().connect_realtime()


func _on_disconnect_realtime_pressed() -> void:
	_log_line("> matchmaking.disconnect_realtime()")
	ForgeSDK.matchmaking().disconnect_realtime()


func _on_join_queue_pressed() -> void:
	var attrs := {
		"mode": _mode.text.strip_edges(),
		"client_version": "1.0.0",
		"region": "us-central1",
	}
	_log_line("> matchmaking.join_queue(%s)" % JSON.stringify(attrs))
	var result = await ForgeSDK.matchmaking().join_queue(attrs)
	_log_result("matchmaking.join_queue", result)


func _on_heartbeat_pressed() -> void:
	_log_line("> matchmaking.heartbeat()")
	var result = await ForgeSDK.matchmaking().heartbeat()
	_log_result("matchmaking.heartbeat", result)


func _on_status_pressed() -> void:
	_log_line("> matchmaking.status()")
	var result = await ForgeSDK.matchmaking().status()
	_log_result("matchmaking.status", result)


func _on_leave_queue_pressed() -> void:
	_log_line("> matchmaking.leave_queue()")
	var result = await ForgeSDK.matchmaking().leave_queue()
	_log_result("matchmaking.leave_queue", result)


func _on_report_result_pressed() -> void:
	_log_line("> leaderboard.report_result(%s, %s, %s)" % [_match_id.text, _winner_id.text, _loser_id.text])
	var result = await ForgeSDK.leaderboard().report_result(_match_id.text.strip_edges(), _winner_id.text.strip_edges(), _loser_id.text.strip_edges())
	_log_result("leaderboard.report_result", result)


func _on_top_pressed() -> void:
	_log_line("> leaderboard.top(1, 10)")
	var result = await ForgeSDK.leaderboard().top(1, 10)
	_log_result("leaderboard.top", result)


func _on_rank_pressed() -> void:
	_log_line("> leaderboard.rank(%s)" % _player_id.text)
	var result = await ForgeSDK.leaderboard().rank(_player_id.text.strip_edges())
	_log_result("leaderboard.rank", result)


func _on_match_found(event: Dictionary) -> void:
	_log_line("[signal match_found] %s" % JSON.stringify(event))


func _on_queue_timeout(event: Dictionary) -> void:
	_log_line("[signal queue_timeout] %s" % JSON.stringify(event))


func _log_result(label: String, result: ForgeResult) -> void:
	if result.ok:
		_log_line("< %s ok %s" % [label, JSON.stringify(result.data)])
	else:
		_log_line("< %s FAIL [%s] %s" % [label, result.error_code, result.error_message])


func _log_line(line: String) -> void:
	_log.text += line + "\n"
