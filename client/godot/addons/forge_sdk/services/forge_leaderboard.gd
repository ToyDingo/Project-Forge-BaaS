extends Node
class_name ForgeLeaderboard

##
## Public leaderboard service.
##
## Surface:
##
##   await ForgeSDK.leaderboard().report_result(match_id, winner_id, loser_id) -> ForgeResult
##   await ForgeSDK.leaderboard().top(page, size) -> ForgeResult
##   await ForgeSDK.leaderboard().rank(player_id) -> ForgeResult
##
## Backend `LEADERBOARD_*` codes flow back through `result.error_code` for
## game code that wants to branch.
##

const _RESULTS_PATH := "/v1/leaderboard/results"
const _TOP_PATH := "/v1/leaderboard/top"
const _RANK_PATH := "/v1/leaderboard/rank/"

var _http


func configure(http) -> void:
	_http = http


func report_result(match_id: String, winner_id: String, loser_id: String) -> ForgeResult:
	if match_id == "" or winner_id == "" or loser_id == "":
		return ForgeResult.failure(
			ForgeErrors.LEADERBOARD_INVALID_RESULT,
			"report_result requires match_id, winner_id, and loser_id."
		)
	var body := {
		"match_id": match_id,
		"winner_id": winner_id,
		"loser_id": loser_id,
	}
	return await _http.post_json(_RESULTS_PATH, body)


func top(page: int = 1, size: int = 10) -> ForgeResult:
	var safe_page := max(page, 1)
	var safe_size := clamp(size, 1, 10)
	var path := "%s?page=%d&size=%d" % [_TOP_PATH, safe_page, safe_size]
	return await _http.get_json(path)


func rank(player_id: String) -> ForgeResult:
	if player_id == "":
		return ForgeResult.failure(
			ForgeErrors.LEADERBOARD_INVALID_RESULT,
			"rank requires a non-empty player_id."
		)
	return await _http.get_json(_RANK_PATH + player_id)
