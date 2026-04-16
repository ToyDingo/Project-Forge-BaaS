package com.forgebackend.matchmaking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * MVP implementation of {@link ForgeEventBus} backed by Spring's in-process STOMP broker.
 * <p>
 * Each event is delivered as a user-specific destination so only the owning WebSocket
 * session receives it. Destinations follow the convention {@code /queue/matchmaking.<event>}
 * under the per-user prefix configured by {@code WebSocketConfig}.
 * <p>
 * This adapter is intentionally the only place that knows about STOMP; the Camel routes and
 * services depend solely on {@link ForgeEventBus}. Swapping this for a Pub/Sub adapter
 * during cloud migration requires no changes outside this package.
 */
@Component
@Primary
public class InProcessEventBusAdapter implements ForgeEventBus {

    private static final Logger log = LoggerFactory.getLogger(InProcessEventBusAdapter.class);

    /** STOMP user-destination for match-found events. */
    public static final String MATCH_FOUND_DESTINATION = "/queue/matchmaking.match-found";

    /** STOMP user-destination for queue-timeout events. */
    public static final String QUEUE_TIMEOUT_DESTINATION = "/queue/matchmaking.queue-timeout";

    private final SimpMessagingTemplate messagingTemplate;

    public InProcessEventBusAdapter(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Delivers a match-found event to the specified player over their private STOMP session.
     */
    @Override
    public void pushMatchFound(UUID playerId, MatchFoundEvent event) {
        try {
            messagingTemplate.convertAndSendToUser(playerId.toString(), MATCH_FOUND_DESTINATION, event);
            log.debug("match_found pushed to player {} for match {}", playerId, event.matchId());
        } catch (MessagingException ex) {
            throw new EventDeliveryException(
                    "Failed to deliver match_found to player " + playerId, ex);
        }
    }

    /**
     * Delivers a queue-timeout event. Transport errors are logged and swallowed because the
     * client can recover state by polling the status endpoint.
     */
    @Override
    public void pushQueueTimeout(UUID playerId, QueueTimeoutEvent event) {
        try {
            messagingTemplate.convertAndSendToUser(playerId.toString(), QUEUE_TIMEOUT_DESTINATION, event);
            log.debug("queue_timeout pushed to player {} for ticket {}", playerId, event.queueTicketId());
        } catch (MessagingException ex) {
            log.warn("Failed to deliver queue_timeout to player {}: {}", playerId, ex.getMessage());
        }
    }
}
