package com.forgebackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Timeouts and base URL for outbound Steam Web API calls.
 *
 * @param devStubEnabled DEV ONLY: when true, {@link com.forgebackend.steam.DevOnlySteamClientStub} is used instead of
 *                       real HTTP calls — remove stub usage before production Steam integration.
 */
@ConfigurationProperties(prefix = "forge.steam")
public record ForgeSteamProperties(
        int requestTimeoutSeconds,
        String baseUrl,
        boolean devStubEnabled
) {
}
