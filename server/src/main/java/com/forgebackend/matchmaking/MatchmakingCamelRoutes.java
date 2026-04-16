package com.forgebackend.matchmaking;

import com.forgebackend.config.ForgeMatchmakingProperties;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Camel routes that drive the asynchronous portion of the matchmaking lifecycle.
 * <p>
 * Three routes are installed:
 * <ol>
 *   <li><b>matchmaker</b>: periodic scan that forms matches from queued players.</li>
 *   <li><b>eviction</b>: periodic scan that removes timed-out or stale-heartbeat tickets.</li>
 *   <li><b>deliver</b>: direct endpoint that delivers {@code match_found} with redelivery.</li>
 * </ol>
 * Every route delegates to {@link MatchmakingOrchestrator} so database state changes remain
 * inside a single transactional Spring component and the Camel layer stays transport-only.
 */
@Component
public class MatchmakingCamelRoutes extends RouteBuilder {

    /** Run the matchmaker scan every 2 seconds at MVP. */
    private static final long MATCHMAKER_PERIOD_MS = 2_000L;

    /** Run the eviction scan every 5 seconds at MVP. */
    private static final long EVICTION_PERIOD_MS = 5_000L;

    private final MatchmakingOrchestrator orchestrator;
    private final ForgeMatchmakingProperties matchmakingProperties;

    public MatchmakingCamelRoutes(
            MatchmakingOrchestrator orchestrator,
            ForgeMatchmakingProperties matchmakingProperties) {
        this.orchestrator = orchestrator;
        this.matchmakingProperties = matchmakingProperties;
    }

    @Override
    public void configure() {
        long retryDelayMs = matchmakingProperties.notificationRetryIntervalSeconds() * 1_000L;
        int retryCount = matchmakingProperties.notificationRetryCount();

        // Notification delivery error policy: retry then cancel the match on exhaustion.
        // This must be declared before the delivery route so it takes effect for it.
        onException(ForgeEventBus.EventDeliveryException.class)
                .maximumRedeliveries(retryCount)
                .redeliveryDelay(retryDelayMs)
                .handled(true)
                .process(exchange -> {
                    UUID matchId = exchange.getIn().getBody(UUID.class);
                    if (matchId != null) {
                        orchestrator.onDeliveryExhausted(matchId);
                    }
                });

        from("timer:matchmaking-matchmaker?period=" + MATCHMAKER_PERIOD_MS)
                .routeId("matchmaking.matchmaker")
                .process(exchange -> orchestrator.formMatches());

        from("timer:matchmaking-eviction?period=" + EVICTION_PERIOD_MS)
                .routeId("matchmaking.eviction")
                .process(exchange -> orchestrator.evictExpired());

        from(MatchmakingOrchestrator.DELIVER_MATCH_ENDPOINT)
                .routeId("matchmaking.deliver")
                .process(exchange -> {
                    UUID matchId = exchange.getIn().getBody(UUID.class);
                    orchestrator.deliverMatchNotification(matchId);
                });
    }
}
