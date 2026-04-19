extends RefCounted
class_name ForgeResult

##
## Uniform envelope returned by every public SDK call.
##
## Fields:
##  - `ok`            : true on success, false on any failure.
##  - `data`          : the parsed JSON body for successful calls; empty on failure.
##  - `error_code`    : a stable string constant from `ForgeErrors` on failure;
##                      empty string on success.
##  - `error_message` : human-readable message safe to log or show in dev UI.
##
## Game code that wants to branch on a specific failure should compare
## `error_code` against the constants in `ForgeErrors`.
##

var ok: bool = false
var data: Dictionary = {}
var error_code: String = ""
var error_message: String = ""


static func success(payload: Dictionary) -> ForgeResult:
	var r := ForgeResult.new()
	r.ok = true
	r.data = payload
	return r


static func failure(code: String, message: String) -> ForgeResult:
	var r := ForgeResult.new()
	r.ok = false
	r.error_code = code
	r.error_message = message
	return r


func _to_string() -> String:
	if ok:
		return "ForgeResult(ok=true, data=%s)" % [data]
	return "ForgeResult(ok=false, error_code=%s, error_message=%s)" % [error_code, error_message]
