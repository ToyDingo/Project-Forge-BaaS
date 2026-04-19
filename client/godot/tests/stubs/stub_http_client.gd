extends Node
class_name StubHttpClient

##
## Drop-in substitute for `ForgeHttpClient` used in automated SDK tests.
##
## Tests configure responses keyed by `(method, path)` via
## `enqueue(method, path, result)` and inspect what the SDK actually sent
## via `calls`.
##
## A second enqueue for the same key is consumed in FIFO order, which lets
## tests express scenarios like "first call returns 401, second call
## returns 200" used by the transparent re-auth tests.
##

class CallRecord:
	var method: String
	var path: String
	var body: Variant


var calls: Array[CallRecord] = []
var _queues: Dictionary = {}  # key -> Array[ForgeResult]
var _reauth_callable: Callable = Callable()


func enqueue(method: String, path: String, result: ForgeResult) -> void:
	var key := method + " " + path
	if not _queues.has(key):
		_queues[key] = []
	(_queues[key] as Array).append(result)


func enqueue_failure(method: String, path: String, code: String, message: String = "") -> void:
	enqueue(method, path, ForgeResult.failure(code, message))


func enqueue_success(method: String, path: String, data: Dictionary) -> void:
	enqueue(method, path, ForgeResult.success(data))


func set_reauth_callback(callback: Callable) -> void:
	_reauth_callable = callback


func get_json(path: String) -> ForgeResult:
	return await _consume("GET", path, null)


func post_json(path: String, body: Dictionary) -> ForgeResult:
	return await _consume("POST", path, body)


func _consume(method: String, path: String, body) -> ForgeResult:
	var record := CallRecord.new()
	record.method = method
	record.path = path
	record.body = body
	calls.append(record)
	var key := method + " " + path
	if _queues.has(key) and (_queues[key] as Array).size() > 0:
		var queue: Array = _queues[key]
		var result: ForgeResult = queue.pop_front()
		# Mirror the real client's behavior: if a 401 invalid-token comes
		# back and a re-auth callable is registered, attempt re-auth and
		# retry exactly once.
		if not result.ok and result.error_code == ForgeErrors.FORGE_INVALID_TOKEN and _reauth_callable.is_valid():
			var reauth = await _reauth_callable.call()
			if reauth is ForgeResult and reauth.ok:
				if queue.size() > 0:
					return queue.pop_front()
		return result
	return ForgeResult.failure(ForgeErrors.FORGE_SDK_NETWORK_ERROR, "No stub response queued for %s %s." % [method, path])


func count_for(method: String, path: String) -> int:
	var n := 0
	for c in calls:
		if c.method == method and c.path == path:
			n += 1
	return n
