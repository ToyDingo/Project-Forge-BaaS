package com.forgebackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT issuer settings and PEM paths for RS256 signing (see README for key generation).
 */
@ConfigurationProperties(prefix = "forge.jwt")
public record ForgeJwtProperties(
        String issuer,
        String audience,
        int accessTokenTtlSeconds,
        String privateKeyPemPath,
        String publicKeyPemPath
) {
}
