package com.forgebackend.auth;

import com.forgebackend.domain.game.Game;
import com.forgebackend.domain.game.GameRepository;
import com.forgebackend.domain.player.PlayerRepository;
import com.forgebackend.security.ForgeApiKeyAuthenticationFilter;
import com.forgebackend.security.ForgeApiKeyHasher;
import com.forgebackend.steam.SteamClient;
import com.forgebackend.steam.SteamTicketValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    private static final String RAW_FORGE_API_KEY = "integration-test-forge-api-key";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private SteamClient steamClient;

    @BeforeEach
    void seedGame() {
        playerRepository.deleteAll();
        gameRepository.deleteAll();
        String lookup = ForgeApiKeyHasher.sha256HexLowercase(RAW_FORGE_API_KEY);
        String bcrypt = passwordEncoder.encode(RAW_FORGE_API_KEY);
        Game game = new Game(UUID.randomUUID(), "Integration Game", bcrypt, lookup, 480L, "steam-web-api-key-secret");
        gameRepository.save(game);
    }

    @Test
    void postSteamAuth_returnsTokenWhenSteamOk() throws Exception {
        org.mockito.Mockito.when(steamClient.validateTicket(anyLong(), anyString(), anyString()))
                .thenReturn(new SteamTicketValidationResult.ValidSteamIdentity("76561198000000001"));

        mockMvc.perform(post("/v1/auth/steam")
                        .header(ForgeApiKeyAuthenticationFilter.HEADER_FORGE_API_KEY, RAW_FORGE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"steam_ticket\":\"deadbeef\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isString())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600));
    }

    @Test
    void postSteamAuth_returns401WhenSteamInvalid() throws Exception {
        org.mockito.Mockito.when(steamClient.validateTicket(anyLong(), anyString(), anyString()))
                .thenReturn(new SteamTicketValidationResult.InvalidSteamTicket("bad ticket"));

        mockMvc.perform(post("/v1/auth/steam")
                        .header(ForgeApiKeyAuthenticationFilter.HEADER_FORGE_API_KEY, RAW_FORGE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"steam_ticket\":\"deadbeef\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("STEAM_VALIDATION_FAILED"));
    }

    @Test
    void postSteamAuth_returns401WhenApiKeyUnknown() throws Exception {
        mockMvc.perform(post("/v1/auth/steam")
                        .header(ForgeApiKeyAuthenticationFilter.HEADER_FORGE_API_KEY, "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"steam_ticket\":\"deadbeef\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("FORGE_GAME_NOT_FOUND"));
    }
}
