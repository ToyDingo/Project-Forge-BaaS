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

/** JPA entity linking a platform account to a single game. */
@Entity
@Table(name = "players")
public class Player {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(nullable = false, length = 32)
    private String platform;

    @Column(name = "platform_user_id", nullable = false, length = 64)
    private String platformUserId;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Player() {
    }

    public Player(UUID id, Game game, String platform, String platformUserId, String displayName) {
        this.id = id;
        this.game = game;
        this.platform = platform;
        this.platformUserId = platformUserId;
        this.displayName = displayName;
    }

    public UUID getId() {
        return id;
    }

    public Game getGame() {
        return game;
    }

    public String getPlatform() {
        return platform;
    }

    public String getPlatformUserId() {
        return platformUserId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
