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

/** Per-client game result report; two reports per match (one from each player). */
@Entity
@Table(name = "match_reports")
public class MatchReport {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false, updatable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false, updatable = false)
    private Player reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_winner_id", nullable = false, updatable = false)
    private Player reportedWinner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_loser_id", nullable = false, updatable = false)
    private Player reportedLoser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private final Instant createdAt = Instant.now();

    protected MatchReport() {
    }

    public MatchReport(UUID id, UUID matchId, Game game, Player reporter, Player reportedWinner, Player reportedLoser) {
        this.id = id;
        this.matchId = matchId;
        this.game = game;
        this.reporter = reporter;
        this.reportedWinner = reportedWinner;
        this.reportedLoser = reportedLoser;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public Game getGame() {
        return game;
    }

    public Player getReporter() {
        return reporter;
    }

    public Player getReportedWinner() {
        return reportedWinner;
    }

    public Player getReportedLoser() {
        return reportedLoser;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
