package com.forgebackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body for {@code POST /v1/matchmaking/leave}. The operation is idempotent and
 * always returns the same fixed status.
 */
public record QueueLeaveResponse(@JsonProperty("status") String status) {

    /** Single instance returned on every call since the response is fixed. */
    public static QueueLeaveResponse left() {
        return new QueueLeaveResponse("left_queue");
    }
}
