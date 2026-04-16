package com.forgebackend.exception;

/**
 * Stable machine-readable error codes returned in JSON for API clients.
 */
public enum ForgeErrorCode {

    FORGE_GAME_NOT_FOUND(401, "No game matches the provided Forge API key."),
    FORGE_INVALID_REQUEST(400, "The request was malformed or missing required fields."),
    FORGE_GAME_MISCONFIGURED(422, "This game is missing Steam configuration required for authentication."),
    STEAM_VALIDATION_FAILED(401, "Steam could not validate the session ticket for this game."),
    STEAM_UNAVAILABLE(503, "Forge could not reach Steam to validate the session ticket."),
    FORGE_INVALID_TOKEN(401, "The bearer token is missing, invalid, or expired."),

    LEADERBOARD_PLAYER_NOT_FOUND(404, "The specified player has no leaderboard entry."),
    LEADERBOARD_INVALID_RESULT(400, "The match result payload is invalid or incomplete."),
    LEADERBOARD_MATCH_NOT_READY(202, "Match report recorded; waiting for the other player's report."),
    LEADERBOARD_DUPLICATE_REPORT(409, "This player has already reported a result for this match."),

    MATCHMAKING_ALREADY_QUEUED(409, "Player is already queued for this game mode."),
    MATCHMAKING_QUEUE_TIMEOUT(200, "No matches were available before the queue timeout elapsed."),
    MATCHMAKING_MATCH_CANCELLED(200, "The match was cancelled after notification retries were exhausted.");

    private final int httpStatus;
    private final String defaultMessage;

    ForgeErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
