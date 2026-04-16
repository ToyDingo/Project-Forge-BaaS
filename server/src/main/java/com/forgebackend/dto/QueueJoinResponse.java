package com.forgebackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for a successful {@code POST /v1/matchmaking/queue}.
 *
 * @param queueTicketId Server-generated identifier for the new queue ticket.
 * @param status        Lifecycle status; always {@code queued} on a successful join.
 * @param joinedAt      Moment the ticket was created server-side.
 * @param timeoutAt     Moment the ticket will expire if no match is found.
 */
public record QueueJoinResponse(
        @JsonProperty("queue_ticket_id") UUID queueTicketId,
        @JsonProperty("status") String status,
        @JsonProperty("joined_at") Instant joinedAt,
        @JsonProperty("timeout_at") Instant timeoutAt
) {
}
