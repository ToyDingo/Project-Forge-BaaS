package com.forgebackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Single row in a leaderboard listing. */
public record LeaderboardEntryResponse(
        @JsonProperty("rank") long rank,
        @JsonProperty("player_id") UUID playerId,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("wins") int wins,
        @JsonProperty("losses") int losses
) {}
