package com.forgebackend.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.UUID;

/**
 * Authentication for {@code POST /v1/auth/steam} after the Forge API key is validated against {@code games}.
 */
public class ForgeGameAuthenticationToken extends AbstractAuthenticationToken {

    private final UUID gameId;

    public ForgeGameAuthenticationToken(UUID gameId) {
        super(null);
        this.gameId = gameId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return gameId;
    }

    public UUID getGameId() {
        return gameId;
    }
}
