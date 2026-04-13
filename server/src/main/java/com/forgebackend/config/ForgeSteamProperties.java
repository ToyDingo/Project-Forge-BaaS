package com.forgebackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Timeouts and base URL for outbound Steam Web API calls. */
@ConfigurationProperties(prefix = "forge.steam")
public record ForgeSteamProperties(
        int requestTimeoutSeconds,
        String baseUrl
) {
}
