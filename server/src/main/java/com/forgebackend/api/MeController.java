package com.forgebackend.api;

import com.forgebackend.security.ForgeJwtAuthenticationToken;
import com.forgebackend.security.ForgeJwtService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Example protected route proving JWT authentication works end-to-end.
 */
@RestController
@RequestMapping("/v1/me")
public class MeController {

    /**
     * Echoes claims from a valid Forge access token (no database read).
     */
    @GetMapping
    public Map<String, Object> me(Authentication authentication) {
        if (!(authentication instanceof ForgeJwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException("Expected JWT authentication");
        }
        ForgeJwtService.ForgeAccessTokenClaims claims = jwtAuth.getAccessTokenClaims();
        UUID playerId = claims.playerId();
        UUID gameId = claims.gameId();
        String platform = claims.platform();
        return Map.of(
                "player_id", playerId.toString(),
                "game_id", gameId.toString(),
                "platform", platform
        );
    }
}
