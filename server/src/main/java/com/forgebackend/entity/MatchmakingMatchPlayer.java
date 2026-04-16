package com.forgebackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity tracking per-player delivery state for a formed match.
 * <p>
 * There is one row per participant. The row is used by the notification retry route to
 * drive the {@code match_found} WebSocket push loop and to decide when a match can be
 * promoted from {@code PENDING_NOTIFY} to {@code READY}.
 */
@Entity
@Table(name = "matchmaking_match_players")
public class MatchmakingMatchPlayer {

    /** Delivery state for a single participant's {@code match_found} notification. */
    public enum DeliveryStatus {
        /** Notification has not yet been delivered to this participant. */
        PENDING,
        /** Participant acknowledged the notification (or push succeeded in the at-least-once sense). */
        DELIVERED,
        /** All retry attempts were exhausted without successful delivery. */
        FAILED
    }

    /**
     * Composite primary key for {@link MatchmakingMatchPlayer}.
     */
    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "match_id", nullable = false, updatable = false)
        private UUID matchId;

        @Column(name = "player_id", nullable = false, updatable = false)
        private UUID playerId;

        protected Id() {
        }

        public Id(UUID matchId, UUID playerId) {
            this.matchId = matchId;
            this.playerId = playerId;
        }

        public UUID getMatchId() {
            return matchId;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id other)) return false;
            return Objects.equals(matchId, other.matchId) && Objects.equals(playerId, other.playerId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(matchId, playerId);
        }
    }

    @EmbeddedId
    private Id id;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 32)
    private DeliveryStatus deliveryStatus;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected MatchmakingMatchPlayer() {
    }

    /**
     * Creates a new participant row with delivery marked as pending.
     */
    public MatchmakingMatchPlayer(UUID matchId, UUID playerId) {
        this.id = new Id(matchId, playerId);
        this.deliveryStatus = DeliveryStatus.PENDING;
    }

    /** Updates delivery status and stamps the change moment. */
    public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
        this.updatedAt = Instant.now();
    }

    public Id getId() {
        return id;
    }

    public UUID getMatchId() {
        return id.getMatchId();
    }

    public UUID getPlayerId() {
        return id.getPlayerId();
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
