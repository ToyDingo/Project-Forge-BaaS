extends Node
class_name ForgeHttpClient

##
## Internal HTTP helper for the Forge SDK.
##
## Wraps Godot's `HTTPRequest` node with `await`-friendly methods and the
## SDK's standard rules:
##
## - Every protected call attaches `Authorization: Bearer <jwt>` if a token
##   is present in the JWT store.
## - `X-Forge-Api-Key` is attached only when `requires_api_key` is true.
##   The only public path that needs this today is `POST /v1/auth/steam`.
## - Backend `ErrorResponse` bodies (`{"error":{"code","message"}}`) are
##   parsed into `ForgeResult` failures with the original `code`.
## - On `401 FORGE_INVALID_TOKEN` the client invokes the optional re-auth
##   callback once. If re-auth succeeds, the original request is retried
##   exactly once with the new token.
##
## This class intentionally does NOT expose any of these mechanics to the
## game developer. Public surface area is the three service classes.
##

const _BACKEND_AUTH_PATH := "/v1/auth/steam"

var _config: ForgeConfig
var _jwt_store: ForgeJwtStore
var _logger: ForgeLogger
var _reauth_callable: Callable = Callable()


func configure(config: ForgeConfig, jwt_store: ForgeJwtStore, logger: ForgeLogger) -> void:
	_config = config
	_jwt_store = jwt_store
	_logger = logger


func set_reauth_callback(callback: Callable) -> void:
	_reauth_callable = callback


func get_json(path: String) -> ForgeResult:
	return await _send("GET", path, null, false, true)


func post_json(path: String, body: Dictionary) -> ForgeResult:
	var requires_api_key := path == _BACKEND_AUTH_PATH
	# The auth endpoint is the only call that does not need a JWT yet.
	var allow_no_jwt := path == _BACKEND_AUTH_PATH
	return await _send("POST", path, body, requires_api_key, not allow_no_jwt)


func _send(method: String, path: String, body, requires_api_key: bool, requires_jwt: bool) -> ForgeResult:
	if _config == null or not _config.is_loaded():
		return ForgeResult.failure(
			ForgeErrors.FORGE_SDK_NOT_CONFIGURED,
			"Forge SDK is not configured. Add forge_config.json with forge_base_url and forge_api_key."
		)
	if requires_jwt and (_jwt_store == null or not _jwt_store.has_token()):
		return ForgeResult.failure(
			ForgeErrors.FORGE_SDK_AUTH_REQUIRED,
			"Call ForgeSDK.auth().login_steam(steam_ticket) before using this service."
		)
	var first := await _send_once(method, path, body, requires_api_key)
	if first.ok:
		return first
	if first.error_code == ForgeErrors.FORGE_INVALID_TOKEN and _reauth_callable.is_valid() and path != _BACKEND_AUTH_PATH:
		_logger.info("JWT rejected; attempting transparent re-auth")
		var reauth_result = await _reauth_callable.call()
		if reauth_result is ForgeResult and reauth_result.ok:
			return await _send_once(method, path, body, requires_api_key)
		_logger.warn("Transparent re-auth failed; surfacing original 401 to caller")
	return first


func _send_once(method: String, path: String, body, requires_api_key: bool) -> ForgeResult:
	var request := HTTPRequest.new()
	add_child(request)
	var url := _config.forge_base_url + path
	var headers := PackedStringArray(["Content-Type: application/json", "Accept: application/json"])
	if requires_api_key:
		headers.append("X-Forge-Api-Key: " + _config.forge_api_key)
	if _jwt_store != null and _jwt_store.has_token():
		headers.append("Authorization: Bearer " + _jwt_store.token())
	var encoded_body := ""
	if body != null:
		encoded_body = JSON.stringify(body)
	var http_method := HTTPClient.METHOD_GET
	if method == "POST":
		http_method = HTTPClient.METHOD_POST
	elif method == "PUT":
		http_method = HTTPClient.METHOD_PUT
	elif method == "DELETE":
		http_method = HTTPClient.METHOD_DELETE
	var error := request.request(url, headers, http_method, encoded_body)
	if error != OK:
		request.queue_free()
		return ForgeResult.failure(
			ForgeErrors.FORGE_SDK_NETWORK_ERROR,
			"Failed to start HTTP request to %s (godot error code %d)." % [url, error]
		)
	var signal_args = await request.request_completed
	request.queue_free()
	return _decode_response(method, path, signal_args)


func _decode_response(method: String, path: String, signal_args: Array) -> ForgeResult:
	# Signal payload: [result, response_code, headers, body]
	if signal_args.size() < 4:
		return ForgeResult.failure(
			ForgeErrors.FORGE_SDK_NETWORK_ERROR,
			"HTTP response was malformed (signal payload size %d)." % signal_args.size()
		)
	var result_code: int = signal_args[0]
	var status: int = signal_args[1]
	var body_bytes: PackedByteArray = signal_args[3]
	if result_code != HTTPRequest.RESULT_SUCCESS:
		_logger.warn("HTTP transport failure", {"path": path, "result": result_code})
		return ForgeResult.failure(
			ForgeErrors.FORGE_SDK_NETWORK_ERROR,
			"%s %s could not reach the Forge backend (transport result %d)." % [method, path, result_code]
		)
	var body_text := body_bytes.get_string_from_utf8()
	var parsed = null
	if body_text != "":
		parsed = JSON.parse_string(body_text)
	if status >= 200 and status < 300:
		var data: Dictionary = {}
		if typeof(parsed) == TYPE_DICTIONARY:
			data = parsed
		elif parsed != null:
			data = {"value": parsed}
		return ForgeResult.success(data)
	# Non-2xx: try to map a Forge ErrorResponse.
	if typeof(parsed) == TYPE_DICTIONARY and parsed.has("error") and typeof(parsed["error"]) == TYPE_DICTIONARY:
		var err: Dictionary = parsed["error"]
		var code: String = String(err.get("code", ""))
		var message: String = String(err.get("message", ""))
		if code == "":
			code = ForgeErrors.FORGE_SDK_NETWORK_ERROR
		if message == "":
			message = "Backend returned status %d." % status
		return ForgeResult.failure(code, message)
	_logger.warn("Unstructured backend error", {"status": status, "body": body_text})
	return ForgeResult.failure(
		ForgeErrors.FORGE_SDK_NETWORK_ERROR,
		"%s %s failed with status %d." % [method, path, status]
	)
