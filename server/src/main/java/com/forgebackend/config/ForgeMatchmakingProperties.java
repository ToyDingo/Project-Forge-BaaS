package com.forgebackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MVP matchmaking behavior tunables.
 * <p>
 * Each field in this record corresponds to an item in the {@code WebConfigurables.md} backlog
 * and is expected to become per-game configurable in Phase 2. For MVP they are sourced from
 * {@code application.yml} under the {@code forge.matchmaking} prefix with sane defaults.
 *
 * @param queueTimeoutSeconds             Maximum seconds a queue ticket may wait before being evicted.
 * @param heartbeatIntervalSeconds        Expected interval between client heartbeats for an active ticket.
 * @param staleHeartbeatThreshold         Number of missed heartbeat intervals before a ticket is stale-removed.
 * @param notificationRetryCount          Number of times match-found delivery is retried before giving up.
 * @param notificationRetryIntervalSeconds Delay between notification retries.
 */
@ConfigurationProperties(prefix = "forge.matchmaking")
public record ForgeMatchmakingProperties(
        int queueTimeoutSeconds,
        int heartbeatIntervalSeconds,
        int staleHeartbeatThreshold,
        int notificationRetryCount,
        int notificationRetryIntervalSeconds
) {
}
