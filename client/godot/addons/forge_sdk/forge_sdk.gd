extends Node

##
## Forge GDScript SDK entry point.
##
## This script is registered as the `ForgeSDK` autoload by the addon plugin
## (`addons/forge_sdk/plugin.gd`). Game code uses it like this:
##
##     await ForgeSDK.auth().login_steam(steam_ticket)
##     ForgeSDK.matchmaking().connect_realtime()
##     ForgeSDK.matchmaking().match_found.connect(_on_match_found)
##     await ForgeSDK.matchmaking().join_queue({"mode": "ranked_1v1"})
##     await ForgeSDK.leaderboard().report_result(match_id, winner_id, loser_id)
##
## Internally this node owns:
##   - the loaded `ForgeConfig`
##   - the in-memory JWT store
##   - the HTTP client (one shared instance for all REST calls)
##   - the STOMP client (one shared instance for realtime)
##   - the three public service nodes (auth, matchmaking, leaderboard)
##
## All of those are internal. The public surface is exactly:
##   `auth()`, `matchmaking()`, `leaderboard()`,
##   `is_ready()`, `last_init_error()`, and
##   `init_with_config(dict)` for tests/harness overrides.
##

const _ForgeConfigT := preload("res://addons/forge_sdk/internal/forge_config.gd")
const _ForgeLoggerT := preload("res://addons/forge_sdk/internal/forge_logger.gd")
const _ForgeJwtStoreT := preload("res://addons/forge_sdk/internal/forge_jwt_store.gd")
const _ForgeHttpClientT := preload("res://addons/forge_sdk/internal/forge_http_client.gd")
const _ForgeStompClientT := preload("res://addons/forge_sdk/internal/forge_stomp_client.gd")
const _ForgeAuthT := preload("res://addons/forge_sdk/services/forge_auth.gd")
const _ForgeMatchmakingT := preload("res://addons/forge_sdk/services/forge_matchmaking.gd")
const _ForgeLeaderboardT := preload("res://addons/forge_sdk/services/forge_leaderboard.gd")

var _config
var _logger
var _jwt_store
var _http
var _stomp
var _auth
var _matchmaking
var _leaderboard
var _ready_ok := false
var _init_error_code: String = ""
var _init_error_message: String = ""


func _ready() -> void:
	_init_with(_ForgeConfigT.load_default())


func init_with_config(values: Dictionary) -> void:
	# Support tests and the cockpit harness that need to inject config
	# without writing a real `forge_config.json`.
	_dispose_internals()
	_init_with(_ForgeConfigT.from_dictionary(values))


func is_ready() -> bool:
	return _ready_ok


func last_init_error() -> Dictionary:
	return {"code": _init_error_code, "message": _init_error_message}


func auth():
	return _auth


func matchmaking():
	return _matchmaking


func leaderboard():
	return _leaderboard


func _init_with(config) -> void:
	_logger = _ForgeLoggerT.new()
	_logger.set_level(config.log_level if config != null else "info")
	if config == null or not config.is_loaded():
		_ready_ok = false
		if config != null:
			_init_error_code = config.load_error_code
			_init_error_message = config.load_error_message
		else:
			_init_error_code = "FORGE_SDK_NOT_CONFIGURED"
			_init_error_message = "Forge SDK could not load configuration."
		_logger.error(_init_error_message, {"code": _init_error_code})
		_install_disabled_services()
		return
	_config = config
	_jwt_store = _ForgeJwtStoreT.new()
	_http = _ForgeHttpClientT.new()
	_http.name = "ForgeHttpClient"
	add_child(_http)
	_http.configure(_config, _jwt_store, _logger)
	_stomp = _ForgeStompClientT.new()
	_stomp.name = "ForgeStompClient"
	add_child(_stomp)
	_stomp.configure(_logger)
	_auth = _ForgeAuthT.new()
	_auth.name = "ForgeAuth"
	add_child(_auth)
	_auth.configure(_http, _jwt_store, _logger)
	_matchmaking = _ForgeMatchmakingT.new()
	_matchmaking.name = "ForgeMatchmaking"
	add_child(_matchmaking)
	_matchmaking.configure(_http, _stomp, _config, _jwt_store, _logger)
	_leaderboard = _ForgeLeaderboardT.new()
	_leaderboard.name = "ForgeLeaderboard"
	add_child(_leaderboard)
	_leaderboard.configure(_http)
	_ready_ok = true
	_init_error_code = ""
	_init_error_message = ""
	_logger.info("Forge SDK ready", {"base_url": _config.forge_base_url})


func _install_disabled_services() -> void:
	# Keep the public methods callable so the developer always gets a clean
	# `ForgeResult` instead of a null reference on a misconfigured project.
	_jwt_store = _ForgeJwtStoreT.new()
	_http = null
	_stomp = null
	_auth = _DisabledService.new(_init_error_code, _init_error_message)
	_matchmaking = _DisabledMatchmaking.new(_init_error_code, _init_error_message)
	_leaderboard = _DisabledService.new(_init_error_code, _init_error_message)
	add_child(_auth)
	add_child(_matchmaking)
	add_child(_leaderboard)


func _dispose_internals() -> void:
	for child in get_children():
		child.queue_free()
	_config = null
	_logger = null
	_jwt_store = null
	_http = null
	_stomp = null
	_auth = null
	_matchmaking = null
	_leaderboard = null
	_ready_ok = false


# ---------------------------------------------------------------------------
# Internal "disabled" placeholders. They preserve the public method shape so
# game code can still `await` against the SDK even when configuration is
# missing; every call returns the same readable failure result.
# ---------------------------------------------------------------------------

class _DisabledService extends Node:
	var _code: String
	var _message: String

	func _init(code: String, message: String) -> void:
		_code = code
		_message = message

	func _make_failure() -> ForgeResult:
		return ForgeResult.failure(_code, _message)

	func login_steam(_ticket): return _make_failure()
	func me(): return _make_failure()
	func report_result(_a, _b, _c): return _make_failure()
	func top(_p = 1, _s = 10): return _make_failure()
	func rank(_p): return _make_failure()


class _DisabledMatchmaking extends Node:
	signal match_found(event: Dictionary)
	signal queue_timeout(event: Dictionary)

	var _code: String
	var _message: String

	func _init(code: String, message: String) -> void:
		_code = code
		_message = message

	func _make_failure() -> ForgeResult:
		return ForgeResult.failure(_code, _message)

	func connect_realtime() -> void: pass
	func disconnect_realtime() -> void: pass
	func is_realtime_connected() -> bool: return false
	func join_queue(_attrs): return _make_failure()
	func leave_queue(): return _make_failure()
	func status(): return _make_failure()
	func heartbeat(): return _make_failure()
