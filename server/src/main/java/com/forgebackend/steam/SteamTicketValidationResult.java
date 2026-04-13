package com.forgebackend.steam;

/**
 * Outcome of validating a Steam session ticket with the Steam Web API.
 */
public sealed interface SteamTicketValidationResult {

    record ValidSteamIdentity(String steamId64) implements SteamTicketValidationResult {
    }

    record InvalidSteamTicket(String reason) implements SteamTicketValidationResult {
    }

    record SteamTransportFailure(String message, Throwable cause) implements SteamTicketValidationResult {
    }
}
