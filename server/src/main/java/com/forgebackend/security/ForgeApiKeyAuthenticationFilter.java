package com.forgebackend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebackend.dto.ErrorResponse;
import com.forgebackend.entity.Game;
import com.forgebackend.exception.ForgeErrorCode;
import com.forgebackend.repository.GameRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Validates {@code X-Forge-Api-Key} for {@code POST /v1/auth/steam} and binds the resolved {@link Game} to the security context.
 */
@Component
public class ForgeApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_FORGE_API_KEY = "X-Forge-Api-Key";

    private final GameRepository gameRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public ForgeApiKeyAuthenticationFilter(
            GameRepository gameRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper) {
        this.gameRepository = gameRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    /**
     * Runs only for Steam auth; other requests pass through unchanged.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !"/v1/auth/steam".equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawApiKey = request.getHeader(HEADER_FORGE_API_KEY);
        if (rawApiKey == null || rawApiKey.isBlank()) {
            writeError(response, ForgeErrorCode.FORGE_INVALID_REQUEST, "Missing " + HEADER_FORGE_API_KEY + " header");
            return;
        }

        String lookupHash = ForgeApiKeyHasher.sha256HexLowercase(rawApiKey.trim());
        var gameOpt = gameRepository.findByApiKeyLookupHash(lookupHash);
        if (gameOpt.isEmpty()) {
            writeError(response, ForgeErrorCode.FORGE_GAME_NOT_FOUND, ForgeErrorCode.FORGE_GAME_NOT_FOUND.defaultMessage());
            return;
        }

        Game game = gameOpt.get();
        String storedHash = game.getApiKeyHash();
        if (storedHash == null || !passwordEncoder.matches(rawApiKey.trim(), storedHash)) {
            writeError(response, ForgeErrorCode.FORGE_GAME_NOT_FOUND, ForgeErrorCode.FORGE_GAME_NOT_FOUND.defaultMessage());
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new ForgeGameAuthenticationToken(game.getId()));
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, ForgeErrorCode code, String message) throws IOException {
        response.setStatus(code.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        byte[] json = objectMapper.writeValueAsBytes(ErrorResponse.of(code.name(), message));
        response.getOutputStream().write(json);
    }
}
