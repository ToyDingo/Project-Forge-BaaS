extends Node
class_name ForgeMatchmaking

##
## Public matchmaking service.
##
## REST surface:
##
##   await ForgeSDK.matchmaking().join_queue(attrs: Dictionary) -> ForgeResult
##   await ForgeSDK.matchmaking().leave_queue() -> ForgeResult
##   await ForgeSDK.matchmaking().status() -> ForgeResult
##   await ForgeSDK.matchmaking().heartbeat() -> ForgeResult
##
## Realtime surface:
##
##   ForgeSDK.matchmaking().connect_realtime()
##   ForgeSDK.matchmaking().disconnect_realtime()
##   signal match_found(event: Dictionary)
##   signal queue_timeout(event: Dictionary)
##
## The `match_found` signal is deduplicated internally by `match_id`, so a
## game can connect a callback once and trust that it fires exactly one
## time per match even when the backend's at-least-once delivery duplicates
## the event.
##
## Heartbeat scheduling is intentionally left to the developer (design
## section 4.1.6 step 4): they decide whether to use a `Timer` node, a
## `process()` accumulator, or any other rhythm that suits their game.
##

signal match_found(event: Dictionary)
signal queue_timeout(event: Dictionary)

const _QUEUE_PATH := "/v1/matchmaking/queue"
const _LEAVE_PATH := "/v1/matchmaking/leave"
const _STATUS_PATH := "/v1/matchmaking/status"
const _HEARTBEAT_PATH := "/v1/matchmaking/heartbeat"

const _MATCH_FOUND_DESTINATION := "/user/queue/matchmaking.match-found"
const _QUEUE_TIMEOUT_DESTINATION := "/user/queue/matchmaking.queue-timeout"

var _http
var _realtime
var _config
var _jwt_store
var _logger
var _seen_match_ids: Dictionary = {}


func configure(http, realtime, config, jwt_store, logger) -> void:
	_http = http
	_realtime = realtime
	_config = config
	_jwt_store = jwt_store
	_logger = logger
	_realtime.register_destination(_MATCH_FOUND_DESTINATION)
	_realtime.register_destination(_QUEUE_TIMEOUT_DESTINATION)
	_realtime.frame_received.connect(_on_realtime_frame)


func connect_realtime() -> void:
	if _jwt_store == null or not _jwt_store.has_token():
		_logger.warn("connect_realtime called without an authenticated session")
		return
	_seen_match_ids.clear()
	_realtime.connect_realtime(_config.forge_base_url, _jwt_store.token())


func disconnect_realtime() -> void:
	_realtime.disconnect_realtime()


func is_realtime_connected() -> bool:
	return _realtime.is_connected_realtime()


func join_queue(attrs: Dictionary) -> ForgeResult:
	if attrs == null:
		attrs = {}
	return await _http.post_json(_QUEUE_PATH, attrs)


func leave_queue() -> ForgeResult:
	return await _http.post_json(_LEAVE_PATH, {})


func status() -> ForgeResult:
	return await _http.get_json(_STATUS_PATH)


func heartbeat() -> ForgeResult:
	return await _http.post_json(_HEARTBEAT_PATH, {})


func _on_realtime_frame(destination: String, payload: Dictionary) -> void:
	if destination == _MATCH_FOUND_DESTINATION:
		var match_id := String(payload.get("match_id", ""))
		if match_id == "":
			_logger.warn("match_found event missing match_id; dropping")
			return
		if _seen_match_ids.has(match_id):
			_logger.debug("Duplicate match_found dropped", {"match_id": match_id})
			return
		_seen_match_ids[match_id] = true
		emit_signal("match_found", payload)
	elif destination == _QUEUE_TIMEOUT_DESTINATION:
		emit_signal("queue_timeout", payload)
	else:
		_logger.debug("Ignoring frame on unknown destination", {"destination": destination})
