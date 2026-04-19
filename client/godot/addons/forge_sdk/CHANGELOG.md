# Forge SDK Changelog

All notable changes to the Forge GDScript SDK addon are recorded here.
The format follows [Keep a Changelog](https://keepachangelog.com) and the
project uses [Semantic Versioning](https://semver.org).

## 0.1.0 - Initial release

- Godot 4.3 addon at `addons/forge_sdk/`.
- `ForgeSDK` autoload entry point with chained services.
- Steam ticket exchange via `auth().login_steam(steam_ticket)`.
- Automatic JWT attach on every subsequent call and transparent re-auth on
  `FORGE_INVALID_TOKEN`.
- Matchmaking REST: `join_queue`, `leave_queue`, `status`, `heartbeat`.
- STOMP-over-WebSocket realtime channel with `match_found` (deduplicated
  by `match_id`) and `queue_timeout` Godot signals.
- Leaderboard REST: `report_result`, `top`, `rank`.
- Configuration via `res://forge_config.json`.
- Structured SDK logger.
