package com.forgebackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Response for a single player's rank lookup. */
public record PlayerRankResponse(
        @JsonProperty("rank") long rank,
        @JsonProperty("player_id") UUID playerId,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("wins") int wins,
        @JsonProperty("losses") int losses
) {}
