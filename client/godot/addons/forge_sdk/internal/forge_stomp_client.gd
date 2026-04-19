extends Node
class_name ForgeStompClient

##
## Internal STOMP-over-WebSocket client.
##
## Wraps Godot 4's `WebSocketPeer` so the rest of the SDK can express
## "subscribe to a destination, give me a Dictionary when a frame arrives"
## without ever exposing STOMP, frame parsing, or WebSocket primitives to
## the game developer.
##
## Connection lifecycle (single, simple state machine):
##
##   1. `connect_realtime(base_url, jwt)`
##   2. The WebSocket reaches OPEN, the client sends a STOMP `CONNECT` frame
##      with `Authorization: Bearer <jwt>`.
##   3. On `CONNECTED` it sends a `SUBSCRIBE` per registered destination.
##   4. Each `MESSAGE` frame is decoded; the JSON body is emitted via the
##      `frame_received(destination, payload)` signal.
##   5. On disconnect the client emits `disconnected` and stops. Reconnects
##      are explicit (the developer calls `connect_realtime` again).
##
## Auto-reconnect is intentionally deferred (design 12.1).
##

signal frame_received(destination: String, payload: Dictionary)
signal connected
signal disconnected
signal connection_failed(error_code: String, error_message: String)

const _NULL := "\u0000"

var _logger: ForgeLogger
var _ws: WebSocketPeer = null
var _state := "idle"
var _pending_jwt: String = ""
var _pending_url: String = ""
var _read_buffer: String = ""
var _subscriptions: Array[String] = []
var _next_subscription_id: int = 0


func configure(logger: ForgeLogger) -> void:
	_logger = logger


func register_destination(destination: String) -> void:
	if not _subscriptions.has(destination):
		_subscriptions.append(destination)


func is_connected_realtime() -> bool:
	return _state == "subscribed"


func connect_realtime(base_url: String, jwt: String) -> void:
	if _state != "idle" and _state != "closed":
		_logger.debug("connect_realtime called while already %s; ignoring" % _state)
		return
	if jwt == "":
		emit_signal("connection_failed", ForgeErrors.FORGE_SDK_AUTH_REQUIRED, "Cannot open realtime channel without a JWT.")
		return
	_pending_jwt = jwt
	_pending_url = _build_ws_url(base_url)
	_read_buffer = ""
	_ws = WebSocketPeer.new()
	var error := _ws.connect_to_url(_pending_url)
	if error != OK:
		_state = "closed"
		emit_signal("connection_failed", ForgeErrors.FORGE_SDK_NETWORK_ERROR, "Could not open realtime channel to %s." % _pending_url)
		_ws = null
		return
	_state = "ws_connecting"
	set_process(true)


func disconnect_realtime() -> void:
	if _ws != null:
		_ws.close()
		_ws = null
	_state = "closed"
	_read_buffer = ""
	set_process(false)
	emit_signal("disconnected")


func _process(_delta: float) -> void:
	if _ws == null:
		set_process(false)
		return
	_ws.poll()
	var ws_state := _ws.get_ready_state()
	match _state:
		"ws_connecting":
			if ws_state == WebSocketPeer.STATE_OPEN:
				_send_connect_frame()
				_state = "stomp_connecting"
			elif ws_state == WebSocketPeer.STATE_CLOSED:
				_state = "closed"
				emit_signal("connection_failed", ForgeErrors.FORGE_SDK_NETWORK_ERROR, "WebSocket closed before STOMP CONNECT.")
				_ws = null
				set_process(false)
		"stomp_connecting", "subscribed":
			if ws_state == WebSocketPeer.STATE_CLOSED:
				_state = "closed"
				emit_signal("disconnected")
				_ws = null
				set_process(false)
				return
			while _ws.get_available_packet_count() > 0:
				var pkt := _ws.get_packet()
				_read_buffer += pkt.get_string_from_utf8()
			_drain_frames()


func _drain_frames() -> void:
	while true:
		var terminator := _read_buffer.find(_NULL)
		if terminator < 0:
			return
		var raw_frame := _read_buffer.substr(0, terminator)
		_read_buffer = _read_buffer.substr(terminator + 1)
		# Skip leading newlines used as STOMP heartbeats.
		while raw_frame.length() > 0 and (raw_frame[0] == "\n" or raw_frame[0] == "\r"):
			raw_frame = raw_frame.substr(1)
		if raw_frame.strip_edges() == "":
			continue
		_handle_frame(raw_frame)


func _handle_frame(frame: String) -> void:
	var header_split := frame.find("\n\n")
	if header_split < 0:
		_logger.debug("STOMP frame missing header/body separator", {"frame": frame})
		return
	var header_block := frame.substr(0, header_split)
	var body := frame.substr(header_split + 2)
	var header_lines := header_block.split("\n")
	if header_lines.size() == 0:
		return
	var command := String(header_lines[0]).strip_edges()
	var headers := {}
	for i in range(1, header_lines.size()):
		var line := String(header_lines[i])
		var colon := line.find(":")
		if colon > 0:
			headers[line.substr(0, colon)] = line.substr(colon + 1)
	match command:
		"CONNECTED":
			_state = "subscribed"
			for destination in _subscriptions:
				_send_subscribe_frame(destination)
			emit_signal("connected")
		"MESSAGE":
			var destination: String = String(headers.get("destination", ""))
			var payload: Dictionary = {}
			if body != "":
				var parsed = JSON.parse_string(body)
				if typeof(parsed) == TYPE_DICTIONARY:
					payload = parsed
			emit_signal("frame_received", destination, payload)
		"ERROR":
			var message: String = String(headers.get("message", "STOMP error"))
			_logger.error("STOMP ERROR frame", {"message": message, "body": body})
			emit_signal("connection_failed", ForgeErrors.FORGE_SDK_NETWORK_ERROR, message)
			disconnect_realtime()
		_:
			_logger.debug("Unhandled STOMP command", {"command": command})


func _send_connect_frame() -> void:
	var host := _pending_url
	var frame := (
		"CONNECT\n"
		+ "accept-version:1.2\n"
		+ "host:" + host + "\n"
		+ "Authorization:Bearer " + _pending_jwt + "\n"
		+ "\n"
		+ _NULL
	)
	_ws.put_packet(frame.to_utf8_buffer())


func _send_subscribe_frame(destination: String) -> void:
	_next_subscription_id += 1
	var frame := (
		"SUBSCRIBE\n"
		+ "id:%d\n" % _next_subscription_id
		+ "destination:" + destination + "\n"
		+ "ack:auto\n"
		+ "\n"
		+ _NULL
	)
	_ws.put_packet(frame.to_utf8_buffer())


func _build_ws_url(base_url: String) -> String:
	var url := base_url
	if url.begins_with("http://"):
		url = "ws://" + url.substr("http://".length())
	elif url.begins_with("https://"):
		url = "wss://" + url.substr("https://".length())
	if url.ends_with("/"):
		url = url.substr(0, url.length() - 1)
	return url + "/ws"
