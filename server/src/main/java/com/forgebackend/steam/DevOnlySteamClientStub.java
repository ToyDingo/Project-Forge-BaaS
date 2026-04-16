package com.forgebackend.steam;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * DEV ONLY — temporary stand-in for Valve's Web API so local tools (e.g. Postman) can exercise auth without Steam.
 * <p>
 * <strong>Remove this class from the active path once real {@link SteamWebApiClient} validation is the default workflow.</strong>
 * Enable with {@code forge.steam.dev-stub-enabled=true} or {@code FORGE_STEAM_DEV_STUB_ENABLED=true}.
 * <p>
 * A ticket is treated as valid if it is non-blank and looks like a hex-encoded blob (typical for session tickets); otherwise
 * validation fails with {@link SteamTicketValidationResult.InvalidSteamTicket}.
 */
@Component
@ConditionalOnProperty(prefix = "forge.steam", name = "dev-stub-enabled", havingValue = "true")
public class DevOnlySteamClientStub implements SteamClient {

    private static final Logger log = LoggerFactory.getLogger(DevOnlySteamClientStub.class);

    /** Minimum hex length to accept as a plausible ticket (arbitrary; real tickets are longer). */
    private static final int MIN_HEX_TICKET_LENGTH = 16;
    /** SteamID64 universe base used to build deterministic mock identities from ticket input. */
    private static final long STEAM_ID_64_BASE = 76561197960265728L;
    /** Ticket hash modulus to keep generated IDs in a realistic range while varying per ticket. */
    private static final long STEAM_ID_OFFSET_MOD = 900_000_000L;

    private static final Pattern HEX_ONLY = Pattern.compile("(?i)[0-9a-f]+");

    @PostConstruct
    void logDevWarning() {
        log.warn(
                "FORGE DEV ONLY: Steam Web API is stubbed — no calls to api.steampowered.com. "
                        + "Disable forge.steam.dev-stub-enabled before production.");
    }

    /**
     * Returns a fake success identity when the ticket looks well-formed; otherwise returns an invalid ticket result.
     */
    @Override
    public SteamTicketValidationResult validateTicket(long steamAppId, String steamWebApiKey, String steamTicketHex) {
        if (steamAppId <= 0) {
            return new SteamTicketValidationResult.InvalidSteamTicket("DEV STUB: steam_app_id must be positive");
        }
        if (steamWebApiKey == null || steamWebApiKey.isBlank()) {
            return new SteamTicketValidationResult.InvalidSteamTicket("DEV STUB: steam_web_api_key must not be blank");
        }
        if (steamTicketHex == null) {
            return new SteamTicketValidationResult.InvalidSteamTicket("DEV STUB: steam_ticket is required");
        }
        String ticket = steamTicketHex.trim();
        if (ticket.length() < MIN_HEX_TICKET_LENGTH) {
            return new SteamTicketValidationResult.InvalidSteamTicket(
                    "DEV STUB: steam_ticket too short — use at least " + MIN_HEX_TICKET_LENGTH + " hex chars for a mock success");
        }
        if (!HEX_ONLY.matcher(ticket).matches()) {
            return new SteamTicketValidationResult.InvalidSteamTicket(
                    "DEV STUB: steam_ticket must be hexadecimal for mock success path");
        }
        long offset = Math.floorMod(ticket.toLowerCase().hashCode(), STEAM_ID_OFFSET_MOD);
        long generatedSteamId = STEAM_ID_64_BASE + offset;
        return new SteamTicketValidationResult.ValidSteamIdentity(Long.toString(generatedSteamId));
    }
}
