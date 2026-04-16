package com.forgebackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for exchanging a Steam session ticket for a Forge JWT.
 */
public record SteamAuthRequest(
        @NotBlank @JsonProperty("steam_ticket") String steamTicket
) {
}
