package com.forgebackend.config;

import com.forgebackend.security.ForgeApiKeyAuthenticationFilter;
import com.forgebackend.security.ForgeJwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless security: Forge API key for Steam auth, JWT bearer tokens elsewhere.
 */
@Configuration
public class SecurityConfig {

    /**
     * Configures URL authorization and installs custom authentication filters ahead of the username/password slot.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ForgeJwtAuthenticationFilter forgeJwtAuthenticationFilter,
            ForgeApiKeyAuthenticationFilter forgeApiKeyAuthenticationFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/health", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(forgeJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(forgeApiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
