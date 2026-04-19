extends RefCounted
class_name ForgeJwtStore

##
## In-memory holder for the player JWT.
##
## The token is never written to disk. On process exit it is gone, which is
## the intended behavior for an MVP: replays cannot be issued from a stale
## file on a shared machine.
##
## `expires_at_unix` is captured from `expires_in` at login time and used by
## the HTTP client to attempt a proactive refresh shortly before the server
## would reject the token. Even if the proactive path is disabled, the HTTP
## client still falls back to a transparent re-auth on `401 FORGE_INVALID_TOKEN`.
##

var _token: String = ""
var _expires_at_unix: int = 0
var _last_steam_ticket: String = ""


func set_token(token: String, expires_in_seconds: int) -> void:
	_token = token
	if expires_in_seconds > 0:
		_expires_at_unix = int(Time.get_unix_time_from_system()) + expires_in_seconds
	else:
		_expires_at_unix = 0


func token() -> String:
	return _token


func has_token() -> bool:
	return _token != ""


func is_expired() -> bool:
	if _expires_at_unix == 0:
		return false
	return int(Time.get_unix_time_from_system()) >= _expires_at_unix


func seconds_until_expiry() -> int:
	if _expires_at_unix == 0:
		return -1
	return _expires_at_unix - int(Time.get_unix_time_from_system())


func remember_steam_ticket(ticket: String) -> void:
	_last_steam_ticket = ticket


func last_steam_ticket() -> String:
	return _last_steam_ticket


func clear() -> void:
	_token = ""
	_expires_at_unix = 0
	# Keep the steam ticket so background re-auth can still work after expiry.
