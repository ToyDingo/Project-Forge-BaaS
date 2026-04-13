package com.forgebackend.auth;

import com.forgebackend.auth.dto.SteamAuthRequest;
import com.forgebackend.auth.dto.TokenResponse;
import com.forgebackend.config.ForgeJwtProperties;
import com.forgebackend.domain.game.Game;
import com.forgebackend.domain.game.GameRepository;
import com.forgebackend.domain.player.Player;
import com.forgebackend.domain.player.PlayerRepository;
import com.forgebackend.exception.ForgeApiException;
import com.forgebackend.exception.ForgeErrorCode;
import com.forgebackend.security.ForgeGameAuthenticationToken;
import com.forgebackend.security.ForgeJwtService;
import com.forgebackend.steam.SteamClient;
import com.forgebackend.steam.SteamTicketValidationResult;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates Steam ticket validation, player upsert, and Forge JWT issuance.
 */
@Service
public class AuthService {

    private static final String PLATFORM_STEAM = "steam";

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final SteamClient steamClient;
    private final ForgeJwtService forgeJwtService;
    private final ForgeJwtProperties forgeJwtProperties;

    public AuthService(
            GameRepository gameRepository,
            PlayerRepository playerRepository,
            SteamClient steamClient,
            ForgeJwtService forgeJwtService,
            ForgeJwtProperties forgeJwtProperties) {
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
        this.steamClient = steamClient;
        this.forgeJwtService = forgeJwtService;
        this.forgeJwtProperties = forgeJwtProperties;
    }

    /**
     * Validates the Steam session ticket for the authenticated game and returns a Forge access token.
     */
    @Transactional
    public TokenResponse authenticateWithSteam(Authentication authentication, SteamAuthRequest request) {
        if (!(authentication instanceof ForgeGameAuthenticationToken gameAuth)) {
            throw new ForgeApiException(ForgeErrorCode.FORGE_INVALID_REQUEST, "Expected Forge game authentication");
        }
        UUID gameId = gameAuth.getGameId();

        if (request.steamTicket() == null || request.steamTicket().isBlank()) {
            throw new ForgeApiException(ForgeErrorCode.FORGE_INVALID_REQUEST, "steam_ticket is required");
        }

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ForgeApiException(ForgeErrorCode.FORGE_GAME_NOT_FOUND));

        if (game.getSteamAppId() == null || game.getSteamWebApiKey() == null || game.getSteamWebApiKey().isBlank()) {
            throw new ForgeApiException(ForgeErrorCode.FORGE_GAME_MISCONFIGURED);
        }

        SteamTicketValidationResult result = steamClient.validateTicket(
                game.getSteamAppId(),
                game.getSteamWebApiKey(),
                request.steamTicket().trim());

        String steamId64 = switch (result) {
            case SteamTicketValidationResult.ValidSteamIdentity v -> v.steamId64();
            case SteamTicketValidationResult.InvalidSteamTicket inv ->
                    throw new ForgeApiException(ForgeErrorCode.STEAM_VALIDATION_FAILED, inv.reason());
            case SteamTicketValidationResult.SteamTransportFailure t ->
                    throw new ForgeApiException(ForgeErrorCode.STEAM_UNAVAILABLE, t.message());
        };

        Player player = playerRepository
                .findByGame_IdAndPlatformAndPlatformUserId(gameId, PLATFORM_STEAM, steamId64)
                .orElseGet(() -> playerRepository.save(
                        new Player(UUID.randomUUID(), game, PLATFORM_STEAM, steamId64, null)));

        String accessToken = forgeJwtService.createAccessToken(player.getId(), gameId, PLATFORM_STEAM);
        return new TokenResponse(accessToken, "Bearer", forgeJwtProperties.accessTokenTtlSeconds());
    }
}
