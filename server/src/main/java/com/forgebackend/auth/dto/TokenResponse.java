package com.forgebackend.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth2-shaped access token response returned after successful Steam authentication.
 */
public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresInSeconds
) {
}
