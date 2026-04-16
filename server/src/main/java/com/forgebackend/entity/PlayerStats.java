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

/** Aggregated win/loss record per player per game; drives leaderboard ranking. */
@Entity
@Table(name = "player_stats")
public class PlayerStats {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false, updatable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private Player player;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int losses;

    @Column(name = "last_win_at")
    private Instant lastWinAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PlayerStats() {
    }

    public PlayerStats(UUID id, Game game, Player player) {
        this.id = id;
        this.game = game;
        this.player = player;
    }

    public void incrementWins() {
        this.wins++;
        this.lastWinAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void incrementLosses() {
        this.losses++;
        this.updatedAt = Instant.now();
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

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public Instant getLastWinAt() {
        return lastWinAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
