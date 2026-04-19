extends Node
class_name StubStompClient

##
## Drop-in substitute for `ForgeStompClient`.
##
## Tests can:
##   - Inspect `connect_called` / `disconnect_called` / `registered_destinations`.
##   - Drive the SDK's signal pipeline by calling
##     `simulate_frame(destination, payload)`.
##

signal frame_received(destination: String, payload: Dictionary)
signal connected
signal disconnected
signal connection_failed(error_code: String, error_message: String)

var connect_called: int = 0
var disconnect_called: int = 0
var registered_destinations: Array[String] = []
var _connected: bool = false


func configure(_logger) -> void:
	pass


func register_destination(destination: String) -> void:
	if not registered_destinations.has(destination):
		registered_destinations.append(destination)


func is_connected_realtime() -> bool:
	return _connected


func connect_realtime(_base_url: String, _jwt: String) -> void:
	connect_called += 1
	_connected = true
	emit_signal("connected")


func disconnect_realtime() -> void:
	disconnect_called += 1
	_connected = false
	emit_signal("disconnected")


func simulate_frame(destination: String, payload: Dictionary) -> void:
	emit_signal("frame_received", destination, payload)
