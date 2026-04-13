package com.forgebackend.auth;

import com.forgebackend.auth.dto.SteamAuthRequest;
import com.forgebackend.auth.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP API for Steam-based authentication.
 */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Exchanges a Steam session ticket for a Forge JWT (upserts the player row as needed).
     */
    @PostMapping("/steam")
    public TokenResponse authenticateWithSteam(
            Authentication authentication,
            @Valid @RequestBody SteamAuthRequest request) {
        return authService.authenticateWithSteam(authentication, request);
    }
}
