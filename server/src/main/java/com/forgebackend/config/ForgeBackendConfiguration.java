package com.forgebackend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties({
        ForgeJwtProperties.class,
        ForgeSteamProperties.class,
        ForgeMatchmakingProperties.class
})
public class ForgeBackendConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Shared system clock bean used by services that need to compute timestamps.
     * Providing a bean lets tests inject a fixed clock without touching production code.
     */
    @Bean
    public Clock forgeClock() {
        return Clock.systemUTC();
    }

    /**
     * HTTP client for Steam Web API calls with configurable timeouts (unused when the dev Steam stub is enabled).
     */
    @Bean
    @ConditionalOnProperty(prefix = "forge.steam", name = "dev-stub-enabled", havingValue = "false", matchIfMissing = true)
    public RestTemplate steamRestTemplate(RestTemplateBuilder builder, ForgeSteamProperties steamProperties) {
        Duration timeout = Duration.ofSeconds(steamProperties.requestTimeoutSeconds());
        return builder
                .setConnectTimeout(timeout)
                .setReadTimeout(timeout)
                .build();
    }
}
