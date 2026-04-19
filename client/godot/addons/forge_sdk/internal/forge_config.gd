extends RefCounted
class_name ForgeConfig

##
## Loader for `res://forge_config.json`.
##
## The SDK refuses to operate without a config file because every other
## subsystem needs at minimum `forge_base_url` and `forge_api_key`.
##
## Schema (matches `client/godot/forge_config.example.json`):
##
##     {
##       "forge_base_url": "...",
##       "forge_api_key": "...",
##       "log_level": "info",
##       "auth": {
##         "auto_refresh_on_expiry": true,
##         "proactive_refresh_before_expiry": true,
##         "log_token_events": true
##       }
##     }
##
## Use `load_default()` at SDK startup. To support tests and the cockpit
## harness, callers can also build a config dictionary manually and pass it
## to `from_dictionary()`.
##

const DEFAULT_PATH := "res://forge_config.json"

var forge_base_url: String = ""
var forge_api_key: String = ""
var log_level: String = "info"
var auto_refresh_on_expiry: bool = true
var proactive_refresh_before_expiry: bool = true
var log_token_events: bool = true

# Set when load failed. Empty when the config is usable.
var load_error_code: String = ""
var load_error_message: String = ""


func is_loaded() -> bool:
	return load_error_code == "" and forge_base_url != "" and forge_api_key != ""


static func load_default() -> ForgeConfig:
	return load_from_path(DEFAULT_PATH)


static func load_from_path(path: String) -> ForgeConfig:
	var config := ForgeConfig.new()
	if not FileAccess.file_exists(path):
		config.load_error_code = ForgeErrors.FORGE_SDK_NOT_CONFIGURED
		config.load_error_message = (
			"Could not find %s. Copy forge_config.example.json to forge_config.json "
			+ "in your Godot project root and set forge_base_url and forge_api_key."
		) % path
		return config
	var file := FileAccess.open(path, FileAccess.READ)
	if file == null:
		config.load_error_code = ForgeErrors.FORGE_SDK_NOT_CONFIGURED
		config.load_error_message = "Could not open %s for reading." % path
		return config
	var raw := file.get_as_text()
	file.close()
	var parsed = JSON.parse_string(raw)
	if typeof(parsed) != TYPE_DICTIONARY:
		config.load_error_code = ForgeErrors.FORGE_SDK_NOT_CONFIGURED
		config.load_error_message = "%s is not valid JSON or does not contain a top-level object." % path
		return config
	return from_dictionary(parsed)


static func from_dictionary(values: Dictionary) -> ForgeConfig:
	var config := ForgeConfig.new()
	config.forge_base_url = String(values.get("forge_base_url", "")).strip_edges()
	config.forge_api_key = String(values.get("forge_api_key", "")).strip_edges()
	config.log_level = String(values.get("log_level", "info"))
	var auth_section: Dictionary = values.get("auth", {})
	if typeof(auth_section) == TYPE_DICTIONARY:
		config.auto_refresh_on_expiry = bool(auth_section.get("auto_refresh_on_expiry", true))
		config.proactive_refresh_before_expiry = bool(auth_section.get("proactive_refresh_before_expiry", true))
		config.log_token_events = bool(auth_section.get("log_token_events", true))
	if config.forge_base_url == "":
		config.load_error_code = ForgeErrors.FORGE_SDK_NOT_CONFIGURED
		config.load_error_message = "forge_config.json is missing required field forge_base_url."
		return config
	if config.forge_api_key == "":
		config.load_error_code = ForgeErrors.FORGE_SDK_NOT_CONFIGURED
		config.load_error_message = "forge_config.json is missing required field forge_api_key."
		return config
	# Strip trailing slash so callers can safely concatenate "/v1/..." paths.
	if config.forge_base_url.ends_with("/"):
		config.forge_base_url = config.forge_base_url.substr(0, config.forge_base_url.length() - 1)
	return config
