package com.forgebackend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for {@code GET /v1/matchmaking/status}.
 * <p>
 * When the player has no active ticket, only {@code status} ({@code not_queued}) is populated.
 *
 * @param status        Current status string.
 * @param queueTicketId Active ticket id, when applicable.
 * @param joinedAt      Moment the active ticket was created, when applicable.
 * @param timeoutAt     Moment the active ticket expires, when applicable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueueStatusResponse(
        @JsonProperty("status") String status,
        @JsonProperty("queue_ticket_id") UUID queueTicketId,
        @JsonProperty("joined_at") Instant joinedAt,
        @JsonProperty("timeout_at") Instant timeoutAt
) {

    /** Convenience factory for the no-active-ticket case. */
    public static QueueStatusResponse notQueued() {
        return new QueueStatusResponse("not_queued", null, null, null);
    }
}
