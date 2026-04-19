@tool
extends EditorPlugin

##
## Editor-side hook for the Forge SDK addon.
##
## When a developer enables the addon from Project Settings, this script
## registers `ForgeSDK` as an autoload so the SDK is reachable from any
## script as `ForgeSDK.auth()`, `ForgeSDK.matchmaking()`, etc. Disabling the
## addon removes the autoload again.
##
## Developers who prefer to instantiate the SDK manually can disable the
## autoload after enabling the addon and use `var forge = ForgeSDK.new()`
## instead.
##

const _AUTOLOAD_NAME := "ForgeSDK"
const _AUTOLOAD_PATH := "res://addons/forge_sdk/forge_sdk.gd"


func _enter_tree() -> void:
	add_autoload_singleton(_AUTOLOAD_NAME, _AUTOLOAD_PATH)


func _exit_tree() -> void:
	remove_autoload_singleton(_AUTOLOAD_NAME)
