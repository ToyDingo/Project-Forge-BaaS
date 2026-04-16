package com.forgebackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body for {@code POST /v1/matchmaking/heartbeat}.
 *
 * @param status                      Current ticket status ({@code queued} or {@code matched}).
 * @param nextHeartbeatDueInSeconds   Recommended delay before the next heartbeat.
 */
public record HeartbeatResponse(
        @JsonProperty("status") String status,
        @JsonProperty("next_heartbeat_due_in_seconds") int nextHeartbeatDueInSeconds
) {
}
