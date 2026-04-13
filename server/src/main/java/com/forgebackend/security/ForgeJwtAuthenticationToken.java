package com.forgebackend.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Authentication established by validating a Forge-issued Bearer JWT.
 */
public class ForgeJwtAuthenticationToken extends AbstractAuthenticationToken {

    private final ForgeJwtService.ForgeAccessTokenClaims accessTokenClaims;

    public ForgeJwtAuthenticationToken(ForgeJwtService.ForgeAccessTokenClaims accessTokenClaims) {
        super(null);
        this.accessTokenClaims = accessTokenClaims;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return accessTokenClaims.playerId();
    }

    public ForgeJwtService.ForgeAccessTokenClaims getAccessTokenClaims() {
        return accessTokenClaims;
    }
}
