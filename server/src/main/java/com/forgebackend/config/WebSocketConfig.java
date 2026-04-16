package com.forgebackend.config;

import com.forgebackend.exception.ForgeApiException;
import com.forgebackend.exception.ForgeErrorCode;
import com.forgebackend.security.ForgeJwtAuthenticationToken;
import com.forgebackend.service.ForgeJwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;

import java.security.Principal;

/**
 * Wires the STOMP-over-WebSocket endpoint used to push matchmaking events to clients.
 * <p>
 * Authentication is performed at STOMP {@code CONNECT} time by parsing the same Bearer JWT
 * the REST filters already validate. Once connected, per-user destinations use the
 * JWT subject ({@code player_id}) as the principal name so
 * {@code convertAndSendToUser(playerId, ...)} reaches exactly one session.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final ForgeJwtService forgeJwtService;

    public WebSocketConfig(ForgeJwtService forgeJwtService) {
        this.forgeJwtService = forgeJwtService;
    }

    /**
     * Registers the single public WebSocket handshake endpoint at {@code /ws}. SockJS is not
     * enabled; the Godot client uses native WebSocket.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    /**
     * Configures broker destinations. Matchmaking events use user-destinations so each payload
     * is scoped to a single player session.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Installs a STOMP channel interceptor that validates the JWT and stamps the session
     * principal on {@code CONNECT}. All subsequent frames reuse the attached principal.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new JwtStompAuthInterceptor(forgeJwtService));
    }

    /**
     * Parses {@code Authorization: Bearer <jwt>} on the STOMP {@code CONNECT} frame and binds
     * the resulting {@link ForgeJwtAuthenticationToken} as the session principal.
     * <p>
     * Failures set no principal and allow the frame to continue; the broker will reject any
     * subsequent user-destination delivery.
     */
    static final class JwtStompAuthInterceptor implements ChannelInterceptor {

        private final ForgeJwtService forgeJwtService;

        JwtStompAuthInterceptor(ForgeJwtService forgeJwtService) {
            this.forgeJwtService = forgeJwtService;
        }

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
            if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
                return message;
            }

            String header = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
            if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
                log.debug("STOMP CONNECT rejected: missing bearer token");
                throw new ForgeApiException(ForgeErrorCode.FORGE_INVALID_TOKEN,
                        "Missing or invalid Authorization bearer token on STOMP CONNECT");
            }

            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                ForgeJwtService.ForgeAccessTokenClaims claims = forgeJwtService.parseAndVerify(token);
                ForgeJwtAuthenticationToken authentication = new ForgeJwtAuthenticationToken(claims);
                Principal principal = new StompPlayerPrincipal(claims.playerId().toString(), authentication);
                accessor.setUser(principal);
            } catch (Exception ex) {
                log.debug("STOMP CONNECT rejected: {}", ex.getMessage());
                throw new ForgeApiException(ForgeErrorCode.FORGE_INVALID_TOKEN,
                        ex.getMessage() != null ? ex.getMessage() : "Invalid JWT on STOMP CONNECT");
            }
            return message;
        }
    }

    /**
     * Minimal principal implementation whose name is the player UUID string, so
     * {@code convertAndSendToUser(playerId.toString(), ...)} routes correctly.
     */
    record StompPlayerPrincipal(String name, ForgeJwtAuthenticationToken authentication) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
