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
    FORGE_INVALID_TOKEN(401, "The bearer token is missing, invalid, or expired.");

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
