package com.forgebackend.matchmaking;

import java.util.UUID;

/**
 * Transport-agnostic publisher for player-directed matchmaking events.
 * <p>
 * The interface exists so service and orchestration code never depends directly on
 * the WebSocket transport. The MVP binding is {@link InProcessEventBusAdapter}, which
 * pushes over Spring STOMP. A future adapter can publish to GCP Pub/Sub without
 * changing any calling code.
 */
public interface ForgeEventBus {

    /**
     * Delivers a {@link MatchFoundEvent} to the specified player.
     *
     * @throws EventDeliveryException if the transport failed and the caller should retry.
     */
    void pushMatchFound(UUID playerId, MatchFoundEvent event) throws EventDeliveryException;

    /**
     * Delivers a {@link QueueTimeoutEvent} to the specified player. Failures are tolerated
     * because the client can recover state via the status endpoint; this method should not
     * throw on transport errors.
     */
    void pushQueueTimeout(UUID playerId, QueueTimeoutEvent event);

    /**
     * Thrown when the event bus cannot deliver a critical event and the caller is expected
     * to trigger a retry. Unchecked so Camel routes can propagate without boilerplate.
     */
    class EventDeliveryException extends RuntimeException {
        public EventDeliveryException(String message) {
            super(message);
        }

        public EventDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
