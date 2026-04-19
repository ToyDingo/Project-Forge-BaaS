extends RefCounted
class_name ForgeErrors

##
## Stable error code constants and the `ForgeResult` envelope used by every
## SDK call.
##
## These constants mirror the server `ForgeErrorCode` enum
## (`server/src/main/java/com/forgebackend/exception/ForgeErrorCode.java`).
## Game code can compare against these constants instead of hard-coding raw
## strings, for example:
##
##     if result.error_code == ForgeErrors.MATCHMAKING_ALREADY_QUEUED:
##         # show "already in queue" UI
##
## In addition to backend codes, the SDK defines a small set of local-only
## codes prefixed with `FORGE_SDK_` for failures that never reach the server
## (missing config, calling a service before login, etc.).
##

# Backend error codes mirrored verbatim. Do not rename without coordinating
# with the server enum and the SDK's published API contract.
const FORGE_GAME_NOT_FOUND := "FORGE_GAME_NOT_FOUND"
const FORGE_INVALID_REQUEST := "FORGE_INVALID_REQUEST"
const FORGE_GAME_MISCONFIGURED := "FORGE_GAME_MISCONFIGURED"
const FORGE_INVALID_TOKEN := "FORGE_INVALID_TOKEN"
const STEAM_VALIDATION_FAILED := "STEAM_VALIDATION_FAILED"
const STEAM_UNAVAILABLE := "STEAM_UNAVAILABLE"
const LEADERBOARD_PLAYER_NOT_FOUND := "LEADERBOARD_PLAYER_NOT_FOUND"
const LEADERBOARD_INVALID_RESULT := "LEADERBOARD_INVALID_RESULT"
const LEADERBOARD_MATCH_NOT_READY := "LEADERBOARD_MATCH_NOT_READY"
const LEADERBOARD_DUPLICATE_REPORT := "LEADERBOARD_DUPLICATE_REPORT"
const MATCHMAKING_ALREADY_QUEUED := "MATCHMAKING_ALREADY_QUEUED"
const MATCHMAKING_QUEUE_TIMEOUT := "MATCHMAKING_QUEUE_TIMEOUT"
const MATCHMAKING_MATCH_CANCELLED := "MATCHMAKING_MATCH_CANCELLED"

# SDK-local codes. These never come from the server; they are produced
# entirely inside the addon for client-side failure modes.
const FORGE_SDK_NOT_CONFIGURED := "FORGE_SDK_NOT_CONFIGURED"
const FORGE_SDK_AUTH_REQUIRED := "FORGE_SDK_AUTH_REQUIRED"
const FORGE_SDK_NETWORK_ERROR := "FORGE_SDK_NETWORK_ERROR"
const FORGE_SDK_DECODE_ERROR := "FORGE_SDK_DECODE_ERROR"
const FORGE_SDK_NOT_IMPLEMENTED := "FORGE_SDK_NOT_IMPLEMENTED"
const FORGE_SDK_REALTIME_NOT_CONNECTED := "FORGE_SDK_REALTIME_NOT_CONNECTED"
