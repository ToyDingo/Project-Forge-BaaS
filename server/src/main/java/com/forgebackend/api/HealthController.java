package com.forgebackend.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight liveness endpoint (also exposed via Spring Boot Actuator).
 */
@RestController
public class HealthController {

    /**
     * Returns a minimal JSON payload for load balancers and smoke tests.
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
