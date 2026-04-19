extends RefCounted
class_name ForgeLogger

##
## Tiny structured logger used by SDK internals.
##
## All log lines are prefixed with `[ForgeSDK]` so they are easy to grep
## out of Godot's mixed editor output. The verbosity is controlled by
## `forge_config.json -> log_level`.
##
## Levels (low to high): "debug", "info", "warn", "error".
##

const LEVEL_DEBUG := 0
const LEVEL_INFO := 1
const LEVEL_WARN := 2
const LEVEL_ERROR := 3

const _LEVEL_BY_NAME := {
	"debug": LEVEL_DEBUG,
	"info": LEVEL_INFO,
	"warn": LEVEL_WARN,
	"warning": LEVEL_WARN,
	"error": LEVEL_ERROR,
}

var _level: int = LEVEL_INFO


func set_level(level_name: String) -> void:
	var key := level_name.to_lower()
	if _LEVEL_BY_NAME.has(key):
		_level = int(_LEVEL_BY_NAME[key])
	else:
		_level = LEVEL_INFO


func debug(message: String, fields: Dictionary = {}) -> void:
	_emit(LEVEL_DEBUG, "DEBUG", message, fields)


func info(message: String, fields: Dictionary = {}) -> void:
	_emit(LEVEL_INFO, "INFO", message, fields)


func warn(message: String, fields: Dictionary = {}) -> void:
	_emit(LEVEL_WARN, "WARN", message, fields)


func error(message: String, fields: Dictionary = {}) -> void:
	_emit(LEVEL_ERROR, "ERROR", message, fields)


func _emit(level: int, label: String, message: String, fields: Dictionary) -> void:
	if level < _level:
		return
	var line := "[ForgeSDK] %s %s" % [label, message]
	if not fields.is_empty():
		line += " " + JSON.stringify(fields)
	if level >= LEVEL_ERROR:
		push_error(line)
	elif level >= LEVEL_WARN:
		push_warning(line)
	else:
		print(line)
