package com.forgebackend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebackend.dto.ErrorResponse;
import com.forgebackend.exception.ForgeErrorCode;
import com.forgebackend.service.ForgeJwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Validates {@code Authorization: Bearer} JWTs for protected routes (not used for {@code /v1/auth/steam}).
 */
@Component
public class ForgeJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ForgeJwtService forgeJwtService;
    private final ObjectMapper objectMapper;

    public ForgeJwtAuthenticationFilter(ForgeJwtService forgeJwtService, ObjectMapper objectMapper) {
        this.forgeJwtService = forgeJwtService;
        this.objectMapper = objectMapper;
    }

    /**
     * Skips public endpoints; parses JWT for all other requests.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isPublicPath(path, request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            writeError(response, ForgeErrorCode.FORGE_INVALID_TOKEN, "Missing or invalid Authorization bearer token");
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            ForgeJwtService.ForgeAccessTokenClaims claims = forgeJwtService.parseAndVerify(token);
            SecurityContextHolder.getContext().setAuthentication(new ForgeJwtAuthenticationToken(claims));
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            String message = ex.getMessage() != null ? ex.getMessage() : ForgeErrorCode.FORGE_INVALID_TOKEN.defaultMessage();
            writeError(response, ForgeErrorCode.FORGE_INVALID_TOKEN, message);
        }
    }


    private static boolean isPublicPath(String path, String method) {
        if ("GET".equalsIgnoreCase(method) && ("/health".equals(path) || "/actuator/health".equals(path))) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && "/v1/auth/steam".equals(path)) {
            return true;
        }
        // WebSocket handshake: authentication is enforced on the STOMP CONNECT frame via
        // the JWT channel interceptor in WebSocketConfig, not at the HTTP upgrade request.
        return path != null && (path.equals("/ws") || path.startsWith("/ws/"));
    }

    private void writeError(HttpServletResponse response, ForgeErrorCode code, String message) throws IOException {
        response.setStatus(code.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        byte[] json = objectMapper.writeValueAsBytes(ErrorResponse.of(code.name(), message));
        response.getOutputStream().write(json);
    }
}
