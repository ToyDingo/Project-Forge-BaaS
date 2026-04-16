package com.forgebackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request body for reporting a match result from one client. */
public record MatchResultRequest(
        @NotNull @JsonProperty("match_id") UUID matchId,
        @NotNull @JsonProperty("winner_id") UUID winnerId,
        @NotNull @JsonProperty("loser_id") UUID loserId
) {}
