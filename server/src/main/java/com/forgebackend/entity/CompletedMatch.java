package com.forgebackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Tombstone for a finalized match, used to keep scoring idempotent after report cleanup. */
@Entity
@Table(name = "completed_matches")
public class CompletedMatch {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false, updatable = false)
    private Game game;

    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "winner_id", nullable = false, updatable = false)
    private Player winner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loser_id", nullable = false, updatable = false)
    private Player loser;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private final Instant completedAt = Instant.now();

    protected CompletedMatch() {
    }

    public CompletedMatch(UUID id, Game game, UUID matchId, Player winner, Player loser) {
        this.id = id;
        this.game = game;
        this.matchId = matchId;
        this.winner = winner;
        this.loser = loser;
    }

    public UUID getId() {
        return id;
    }

    public Game getGame() {
        return game;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public Player getWinner() {
        return winner;
    }

    public Player getLoser() {
        return loser;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
