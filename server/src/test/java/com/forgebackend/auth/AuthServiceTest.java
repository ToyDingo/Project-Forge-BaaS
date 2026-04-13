package com.forgebackend.auth;

import com.forgebackend.auth.dto.SteamAuthRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private GameRepository gameRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private SteamClient steamClient;
    @Mock
    private ForgeJwtService forgeJwtService;
    @Mock
    private ForgeJwtProperties forgeJwtProperties;

    private AuthService authService;

    private final UUID gameId = UUID.randomUUID();
    private Game game;

    @BeforeEach
    void setUp() {
        authService = new AuthService(gameRepository, playerRepository, steamClient, forgeJwtService, forgeJwtProperties);
        game = new Game(gameId, "Test Game", "hash", "lookup", 480L, "steam-web-api-key");
        when(forgeJwtProperties.accessTokenTtlSeconds()).thenReturn(3600);
    }

    @Test
    void authenticateWithSteam_createsPlayerAndReturnsToken() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(steamClient.validateTicket(480L, "steam-web-api-key", "ticket-hex"))
                .thenReturn(new SteamTicketValidationResult.ValidSteamIdentity("76561198000000000"));
        when(playerRepository.findByGame_IdAndPlatformAndPlatformUserId(gameId, "steam", "76561198000000000"))
                .thenReturn(Optional.empty());
        when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));
        when(forgeJwtService.createAccessToken(any(), any(), any())).thenReturn("jwt-token");

        var auth = new ForgeGameAuthenticationToken(gameId);
        var response = authService.authenticateWithSteam(auth, new SteamAuthRequest("ticket-hex"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(3600);

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(captor.capture());
        assertThat(captor.getValue().getPlatformUserId()).isEqualTo("76561198000000000");
    }

    @Test
    void authenticateWithSteam_throwsWhenSteamInvalid() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(steamClient.validateTicket(480L, "steam-web-api-key", "bad"))
                .thenReturn(new SteamTicketValidationResult.InvalidSteamTicket("nope"));

        var auth = new ForgeGameAuthenticationToken(gameId);
        assertThatThrownBy(() -> authService.authenticateWithSteam(auth, new SteamAuthRequest("bad")))
                .isInstanceOf(ForgeApiException.class)
                .extracting(ex -> ((ForgeApiException) ex).getErrorCode())
                .isEqualTo(ForgeErrorCode.STEAM_VALIDATION_FAILED);
    }

    @Test
    void authenticateWithSteam_throwsWhenGameMisconfigured() {
        Game incomplete = new Game(gameId, "X", "h", "l", null, null);
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(incomplete));

        var auth = new ForgeGameAuthenticationToken(gameId);
        assertThatThrownBy(() -> authService.authenticateWithSteam(auth, new SteamAuthRequest("t")))
                .isInstanceOf(ForgeApiException.class)
                .extracting(ex -> ((ForgeApiException) ex).getErrorCode())
                .isEqualTo(ForgeErrorCode.FORGE_GAME_MISCONFIGURED);
    }

    @Test
    void authenticateWithSteam_throwsWhenSteamUnreachable() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(steamClient.validateTicket(480L, "steam-web-api-key", "ticket-hex"))
                .thenReturn(new SteamTicketValidationResult.SteamTransportFailure("timeout", new RuntimeException()));

        var auth = new ForgeGameAuthenticationToken(gameId);
        assertThatThrownBy(() -> authService.authenticateWithSteam(auth, new SteamAuthRequest("ticket-hex")))
                .isInstanceOf(ForgeApiException.class)
                .extracting(ex -> ((ForgeApiException) ex).getErrorCode())
                .isEqualTo(ForgeErrorCode.STEAM_UNAVAILABLE);
    }
}
