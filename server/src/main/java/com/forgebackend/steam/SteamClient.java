package com.forgebackend.steam;

/**
 * Validates Steam session tickets against the Steam Web API (per-game publisher key and App ID).
 */
public interface SteamClient {

    /**
     * Calls Steam {@code ISteamUserAuth/AuthenticateUserTicket} and returns the resolved SteamID64 on success.
     */
    SteamTicketValidationResult validateTicket(long steamAppId, String steamWebApiKey, String steamTicketHex);
}
