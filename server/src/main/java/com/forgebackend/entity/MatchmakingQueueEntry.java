package com.forgebackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for a single player's active queue ticket.
 * <p>
 * One active row is allowed per (game, player, mode) combination while the status is
 * {@link Status#QUEUED} or {@link Status#MATCHED}. Terminal statuses leave the row in place
 * as a historical record and no longer participate in the active-ticket uniqueness constraint.
 */
@Entity
@Table(name = "matchmaking_queue_entries")
public class MatchmakingQueueEntry {

    /**
     * Lifecycle states for a queue ticket.
     * <p>
     * The string values are persisted to the {@code status} column and must not be renamed
     * without a coordinated database migration.
     */
    public enum Status {
        /** Player is waiting for a match. */
        QUEUED,
        /** A match has been reserved for this player. */
        MATCHED,
        /** Player waited past the timeout without matching. */
        TIMED_OUT,
        /** Player explicitly left the queue. */
        LEFT_QUEUE,
        /** Player missed the configured heartbeat threshold and was evicted. */
        STALE_REMOVED
    }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false, updatable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private Player player;

    @Column(nullable = false, length = 64, updatable = false)
    private String mode;

    @Column(name = "client_version", nullable = false, length = 64, updatable = false)
    private String clientVersion;

    @Column(nullable = false, length = 32, updatable = false)
    private String platform;

    @Column(length = 64, updatable = false)
    private String region;

    /**
     * Optional map of region code to measured latency in milliseconds, serialized as JSON text.
     * Stored as plain text for portability; MVP does not rely on JSONB query semantics.
     */
    @Column(name = "latency_by_region_json", columnDefinition = "TEXT", updatable = false)
    private String latencyByRegionJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private final Instant joinedAt = Instant.now();

    @Column(name = "timeout_at", nullable = false, updatable = false)
    private Instant timeoutAt;

    @Column(name = "last_heartbeat_at", nullable = false)
    private Instant lastHeartbeatAt = Instant.now();

    protected MatchmakingQueueEntry() {
    }

    /**
     * Full constructor used by the service when creating a new active queue ticket.
     */
    public MatchmakingQueueEntry(
            UUID id,
            Game game,
            Player player,
            String mode,
            String clientVersion,
            String platform,
            String region,
            String latencyByRegionJson,
            Instant timeoutAt) {
        this.id = id;
        this.game = game;
        this.player = player;
        this.mode = mode;
        this.clientVersion = clientVersion;
        this.platform = platform;
        this.region = region;
        this.latencyByRegionJson = latencyByRegionJson;
        this.status = Status.QUEUED;
        this.timeoutAt = timeoutAt;
    }

    /** Records a fresh heartbeat moment for this ticket. */
    public void markHeartbeat(Instant at) {
        this.lastHeartbeatAt = at;
    }

    /** Transitions this ticket to the supplied terminal or intermediate status. */
    public void setStatus(Status status) {
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public Game getGame() {
        return game;
    }

    public Player getPlayer() {
        return player;
    }

    public String getMode() {
        return mode;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public String getPlatform() {
        return platform;
    }

    public String getRegion() {
        return region;
    }

    public String getLatencyByRegionJson() {
        return latencyByRegionJson;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getTimeoutAt() {
        return timeoutAt;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }
}
