package com.forgebackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Paginated leaderboard response. */
public record LeaderboardPageResponse(
        @JsonProperty("page") int page,
        @JsonProperty("size") int size,
        @JsonProperty("total_players") long totalPlayers,
        @JsonProperty("total_pages") int totalPages,
        @JsonProperty("items") List<LeaderboardEntryResponse> items
) {}
