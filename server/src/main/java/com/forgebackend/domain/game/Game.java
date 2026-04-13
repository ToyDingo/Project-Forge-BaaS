package com.forgebackend.domain.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** JPA entity for a registered game (tenant) and its Forge/Steam credentials. */
@Entity
@Table(name = "games")
public class Game {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "api_key_hash", length = 255)
    private String apiKeyHash;

    @Column(name = "api_key_lookup_hash", length = 64)
    private String apiKeyLookupHash;

    @Column(name = "steam_app_id")
    private Long steamAppId;

    @Column(name = "steam_web_api_key", columnDefinition = "TEXT")
    private String steamWebApiKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Game() {
    }

    public Game(UUID id, String name, String apiKeyHash, String apiKeyLookupHash, Long steamAppId, String steamWebApiKey) {
        this.id = id;
        this.name = name;
        this.apiKeyHash = apiKeyHash;
        this.apiKeyLookupHash = apiKeyLookupHash;
        this.steamAppId = steamAppId;
        this.steamWebApiKey = steamWebApiKey;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public String getApiKeyLookupHash() {
        return apiKeyLookupHash;
    }

    public Long getSteamAppId() {
        return steamAppId;
    }

    public String getSteamWebApiKey() {
        return steamWebApiKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
