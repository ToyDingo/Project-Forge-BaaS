extends Node
class_name ForgeAuth

##
## Public auth service.
##
## Surface (kept tiny on purpose):
##
##   await ForgeSDK.auth().login_steam(steam_ticket: String) -> ForgeResult
##   await ForgeSDK.auth().me() -> ForgeResult
##
## `login_steam` exchanges the Steam session ticket for a Forge access
## token and stores it in memory so every subsequent SDK call carries it
## automatically. Developers never see the token, the auth header, or
## the API key header. Those concerns live in the internal HTTP module.
##
## After a successful login, the SDK also remembers the last good steam
## ticket so the HTTP client can perform a transparent re-auth when the
## token expires mid-session.
##

const _STEAM_PATH := "/v1/auth/steam"
const _ME_PATH := "/v1/me"

var _http
var _jwt_store
var _logger


func configure(http, jwt_store, logger) -> void:
	_http = http
	_jwt_store = jwt_store
	_logger = logger
	_http.set_reauth_callback(Callable(self, "_silent_reauth"))


func login_steam(steam_ticket: String) -> ForgeResult:
	if steam_ticket == null or String(steam_ticket).strip_edges() == "":
		return ForgeResult.failure(
			ForgeErrors.FORGE_INVALID_REQUEST,
			"login_steam requires a non-empty steam_ticket."
		)
	var result := await _http.post_json(_STEAM_PATH, {"steam_ticket": steam_ticket})
	if not result.ok:
		_logger.warn("login_steam failed", {"code": result.error_code})
		return result
	var token := String(result.data.get("access_token", ""))
	var expires_in := int(result.data.get("expires_in", 0))
	if token == "":
		return ForgeResult.failure(
			ForgeErrors.FORGE_SDK_DECODE_ERROR,
			"Auth response did not include an access_token."
		)
	_jwt_store.set_token(token, expires_in)
	_jwt_store.remember_steam_ticket(steam_ticket)
	_logger.info("Player authenticated", {"expires_in": expires_in})
	return result


func me() -> ForgeResult:
	return await _http.get_json(_ME_PATH)


func _silent_reauth() -> ForgeResult:
	var ticket := _jwt_store.last_steam_ticket()
	if ticket == "":
		return ForgeResult.failure(
			ForgeErrors.FORGE_SDK_AUTH_REQUIRED,
			"No remembered Steam ticket; cannot re-authenticate transparently."
		)
	# Clear the dead token first so the replay request does not reuse it.
	_jwt_store.clear()
	var result := await _http.post_json(_STEAM_PATH, {"steam_ticket": ticket})
	if not result.ok:
		return result
	var token := String(result.data.get("access_token", ""))
	var expires_in := int(result.data.get("expires_in", 0))
	if token == "":
		return ForgeResult.failure(
			ForgeErrors.FORGE_SDK_DECODE_ERROR,
			"Re-auth response did not include an access_token."
		)
	_jwt_store.set_token(token, expires_in)
	_logger.info("Re-authenticated transparently after token expiry")
	return result
