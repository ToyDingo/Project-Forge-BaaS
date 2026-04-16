package com.forgebackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Request body for {@code POST /v1/matchmaking/queue}.
 *
 * @param mode             Game mode identifier, e.g. {@code ranked_1v1}.
 * @param clientVersion    Client build version. Used for compatibility matching at MVP.
 * @param region           Optional preferred region code.
 * @param latencyByRegionMs Optional measured latency per region, in milliseconds.
 */
public record QueueJoinRequest(
        @NotBlank @JsonProperty("mode") String mode,
        @NotBlank @JsonProperty("client_version") String clientVersion,
        @JsonProperty("region") String region,
        @JsonProperty("latency_by_region_ms") Map<String, Integer> latencyByRegionMs
) {
}
