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
 * JPA entity for a formed match between queued players.
 * <p>
 * A match starts life in {@link Status#PENDING_NOTIFY}, transitions to {@link Status#READY}
 * once every participant has received the {@code match_found} notification, or
 * {@link Status#CANCELLED} when delivery fails past the retry budget.
 */
@Entity
@Table(name = "matchmaking_matches")
public class MatchmakingMatch {

    /** Lifecycle states for a formed match row. */
    public enum Status {
        /** Match was formed; at least one participant still needs to be notified. */
        PENDING_NOTIFY,
        /** Every participant has been notified successfully. */
        READY,
        /** Match was abandoned; typically after notification retries were exhausted. */
        CANCELLED
    }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false, updatable = false)
    private Game game;

    @Column(nullable = false, length = 64, updatable = false)
    private String mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private final Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected MatchmakingMatch() {
    }

    /**
     * Full constructor used when the matchmaker forms a new match.
     */
    public MatchmakingMatch(UUID id, Game game, String mode, Instant expiresAt) {
        this.id = id;
        this.game = game;
        this.mode = mode;
        this.status = Status.PENDING_NOTIFY;
        this.expiresAt = expiresAt;
    }

    /** Updates the lifecycle status of this match. */
    public void setStatus(Status status) {
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public Game getGame() {
        return game;
    }

    public String getMode() {
        return mode;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
