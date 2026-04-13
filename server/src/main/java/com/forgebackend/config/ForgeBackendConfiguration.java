package com.forgebackend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({ForgeJwtProperties.class, ForgeSteamProperties.class})
public class ForgeBackendConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * HTTP client for Steam Web API calls with configurable timeouts.
     */
    @Bean
    public RestTemplate steamRestTemplate(RestTemplateBuilder builder, ForgeSteamProperties steamProperties) {
        Duration timeout = Duration.ofSeconds(steamProperties.requestTimeoutSeconds());
        return builder
                .setConnectTimeout(timeout)
                .setReadTimeout(timeout)
                .build();
    }
}
