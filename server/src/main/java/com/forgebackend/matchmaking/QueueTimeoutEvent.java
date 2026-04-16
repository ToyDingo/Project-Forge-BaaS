package com.forgebackend.matchmaking;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Payload for the {@code queue_timeout} WebSocket event pushed to a player whose queue
 * ticket expired without being matched. The field shape is part of the public client contract.
 *
 * @param queueTicketId The specific ticket that timed out.
 * @param message       Human-readable explanation suitable for UI display.
 */
public record QueueTimeoutEvent(
        @JsonProperty("event_type") String eventType,
        @JsonProperty("queue_ticket_id") UUID queueTicketId,
        @JsonProperty("message") String message
) {

    public static final String EVENT_TYPE = "queue_timeout";

    /** Convenience factory that fills in the stable {@code event_type} string. */
    public static QueueTimeoutEvent of(UUID queueTicketId, String message) {
        return new QueueTimeoutEvent(EVENT_TYPE, queueTicketId, message);
    }
}
