package com.forgebackend.controller;

import com.forgebackend.dto.HeartbeatResponse;
import com.forgebackend.dto.LeaderboardPageResponse;
import com.forgebackend.dto.MatchReportResponse;
import com.forgebackend.dto.MatchResultRequest;
import com.forgebackend.dto.PlayerRankResponse;
import com.forgebackend.dto.QueueJoinRequest;
import com.forgebackend.dto.QueueJoinResponse;
import com.forgebackend.dto.QueueLeaveResponse;
import com.forgebackend.dto.QueueStatusResponse;
import com.forgebackend.dto.SteamAuthRequest;
import com.forgebackend.dto.TokenResponse;
import com.forgebackend.security.ForgeJwtAuthenticationToken;
import com.forgebackend.service.AuthService;
import com.forgebackend.service.ForgeJwtService;
import com.forgebackend.service.LeaderboardService;
import com.forgebackend.service.MatchmakingService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Single entry point for all Forge HTTP endpoints.
 * <p>
 * This controller contains zero business logic; every request is delegated
 * to the appropriate service.
 */
@RestController
public class ForgeGatewayController {

    private final AuthService authService;
    private final LeaderboardService leaderboardService;
    private final MatchmakingService matchmakingService;

    public ForgeGatewayController(
            AuthService authService,
            LeaderboardService leaderboardService,
            MatchmakingService matchmakingService) {
        this.authService = authService;
        this.leaderboardService = leaderboardService;
        this.matchmakingService = matchmakingService;
    }

    /**
     * Lightweight liveness probe for load balancers and smoke tests.
     *
     * <ul>
     *   <li><b>Auth:</b> none (publicly accessible)</li>
     *   <li><b>Method:</b> {@code GET /health}</li>
     *   <li><b>Request:</b> no body</li>
     *   <li><b>Response:</b> {@code {"status":"UP"}} (200 OK)</li>
     *   <li><b>Errors:</b> none under normal operation</li>
     * </ul>
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    /**
     * Exchanges a Steam session ticket for a Forge JWT access token. The authenticated
     * game is resolved from the {@code X-Forge-Api-Key} header by
     * {@link com.forgebackend.security.ForgeApiKeyAuthenticationFilter} before this
     * method is invoked. A player row is upserted if the Steam identity is new.
     *
     * <ul>
     *   <li><b>Auth:</b> {@code X-Forge-Api-Key} header (game-level API key)</li>
     *   <li><b>Method:</b> {@code POST /v1/auth/steam}</li>
     *   <li><b>Request:</b> JSON body {@code {"steam_ticket":"<hex>"}}</li>
     *   <li><b>Response:</b> {@link TokenResponse} with {@code access_token}, {@code token_type}, {@code expires_in} (200 OK)</li>
     *   <li><b>Errors:</b>
     *     <ul>
     *       <li>{@code FORGE_INVALID_REQUEST} (400) — missing or blank steam_ticket</li>
     *       <li>{@code FORGE_GAME_NOT_FOUND} (401) — unknown API key</li>
     *       <li>{@code FORGE_GAME_MISCONFIGURED} (422) — game lacks Steam config</li>
     *       <li>{@code STEAM_VALIDATION_FAILED} (401) — Steam rejected the ticket</li>
     *       <li>{@code STEAM_UNAVAILABLE} (503) — could not reach Steam</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    @PostMapping("/v1/auth/steam")
    public TokenResponse authenticateWithSteam(
            Authentication authentication,
            @Valid @RequestBody SteamAuthRequest request) {
        return authService.authenticateWithSteam(authentication, request);
    }

    /**
     * Returns the identity claims embedded in the caller's Forge JWT. No database
     * lookup is performed; the response is derived entirely from the verified token.
     *
     * <ul>
     *   <li><b>Auth:</b> {@code Authorization: Bearer <forge-jwt>}</li>
     *   <li><b>Method:</b> {@code GET /v1/me}</li>
     *   <li><b>Request:</b> no body</li>
     *   <li><b>Response:</b> JSON with {@code player_id}, {@code game_id}, {@code platform} (200 OK)</li>
     *   <li><b>Errors:</b>
     *     <ul>
     *       <li>{@code FORGE_INVALID_TOKEN} (401) — missing, expired, or malformed JWT</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    @GetMapping("/v1/me")
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

    /**
     * Reports a match result from the authenticated player. Each match requires
     * two independent reports (one per participant); stats are updated only after
     * both reports arrive and are reconciled.
     *
     * <ul>
     *   <li><b>Auth:</b> {@code Authorization: Bearer <forge-jwt>}</li>
     *   <li><b>Method:</b> {@code POST /v1/leaderboard/results}</li>
     *   <li><b>Request:</b> JSON body {@link MatchResultRequest} with {@code match_id}, {@code winner_id}, {@code loser_id}</li>
     *   <li><b>Response:</b> {@link MatchReportResponse} with {@code status} and {@code message} (200 OK or 202 Accepted)</li>
     *   <li><b>Errors:</b>
     *     <ul>
     *       <li>{@code LEADERBOARD_INVALID_RESULT} (400) — invalid payload (same winner/loser, reporter not a participant, player not in game)</li>
     *       <li>{@code LEADERBOARD_DUPLICATE_REPORT} (409) — this player already reported for this match</li>
     *       <li>{@code FORGE_INVALID_TOKEN} (401) — missing, expired, or malformed JWT</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    @PostMapping("/v1/leaderboard/results")
    public MatchReportResponse reportMatchResult(
            Authentication authentication,
            @Valid @RequestBody MatchResultRequest request) {
        ForgeJwtAuthenticationToken jwtAuth = (ForgeJwtAuthenticationToken) authentication;
        ForgeJwtService.ForgeAccessTokenClaims claims = jwtAuth.getAccessTokenClaims();
        return leaderboardService.reportMatchResult(claims.gameId(), claims.playerId(), request);
    }

    /**
     * Returns a paginated leaderboard for the authenticated game, ranked by wins
     * descending with tiebreakers on losses, recency, and player ID.
     *
     * <ul>
     *   <li><b>Auth:</b> {@code Authorization: Bearer <forge-jwt>}</li>
     *   <li><b>Method:</b> {@code GET /v1/leaderboard/top}</li>
     *   <li><b>Request:</b> query params {@code page} (default 1), {@code size} (default 10, max 10)</li>
     *   <li><b>Response:</b> {@link LeaderboardPageResponse} with pagination metadata and ranked entries (200 OK)</li>
     *   <li><b>Errors:</b>
     *     <ul>
     *       <li>{@code FORGE_INVALID_TOKEN} (401) — missing, expired, or malformed JWT</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    @GetMapping("/v1/leaderboard/top")
    public LeaderboardPageResponse getLeaderboard(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        ForgeJwtAuthenticationToken jwtAuth = (ForgeJwtAuthenticationToken) authentication;
        ForgeJwtService.ForgeAccessTokenClaims claims = jwtAuth.getAccessTokenClaims();
        return leaderboardService.getLeaderboard(claims.gameId(), page, size);
    }

    /**
     * Returns the rank and win/loss record for a specific player within the
     * authenticated game's leaderboard.
     *
     * <ul>
     *   <li><b>Auth:</b> {@code Authorization: Bearer <forge-jwt>}</li>
     *   <li><b>Method:</b> {@code GET /v1/leaderboard/rank/{playerId}}</li>
     *   <li><b>Request:</b> path variable {@code playerId} (UUID)</li>
     *   <li><b>Response:</b> {@link PlayerRankResponse} with rank, wins, losses, and display name (200 OK)</li>
     *   <li><b>Errors:</b>
     *     <ul>
     *       <li>{@code LEADERBOARD_PLAYER_NOT_FOUND} (404) — no stats exist for this player in this game</li>
     *       <li>{@code FORGE_INVALID_TOKEN} (401) — missing, expired, or malformed JWT</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    @GetMapping("/v1/leaderboard/rank/{playerId}")
    public PlayerRankResponse getPlayerRank(
            Authentication authentication,
            @PathVariable UUID playerId) {
        ForgeJwtAuthenticationToken jwtAuth = (ForgeJwtAuthenticationToken) authentication;
        ForgeJwtService.ForgeAccessTokenClaims claims = jwtAuth.getAccessTokenClaims();
        return leaderboardService.getPlayerRank(claims.gameId(), playerId);
    }

    /**
     * Joins the matchmaking queue for the authenticated player.
     *
     * <ul>
     *   <li><b>Auth:</b> {@code Authorization: Bearer <forge-jwt>}</li>
     *   <li><b>Method:</b> {@code POST /v1/matchmaking/queue}</li>
     *   <li><b>Request:</b> {@link QueueJoinRequest} with {@code mode}, {@code client_version}, and optional region/latency fields</li>
     *   <li><b>Response:</b> {@link QueueJoinResponse} with {@code queue_ticket_id}, {@code status} ({@code queued}), {@code joined_at}, {@code timeout_at} (200 OK)</li>
     *   <li><b>Errors:</b>
     *     <ul>
     *       <li>{@code FORGE_INVALID_REQUEST} (400) — missing mode or client_version</li>
     *       <li>{@code MATCHMAKING_ALREADY_QUEUED} (409) — active ticket for this mode already exists</li>
     *       <li>{@code FORGE_INVALID_TOKEN} (401) — missing, expired, or malformed JWT</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    @PostMapping("/v1/matchmaking/queue")
    public QueueJoinResponse joinMatchmakingQueue(
            Authentication authentication,
            @Valid @RequestBody QueueJoinRequest request) {
        ForgeJwtAuthenticationToken jwtAuth = (ForgeJwtAuthenticationToken) authentication;
        ForgeJwtService.ForgeAccessTokenClaims claims = jwtAuth.getAccessTokenClaims();
        return matchmakingService.joinQueue(claims.gameId(), claims.playerId(), claims.platform(), request);
    }

    /**
     * Leaves the matchmaking queue. This endpoint is idempotent; calling it without an active
     * ticket is not an error.
     *
     * <ul>
     *   <li><b>Auth:</b> {@code Authorization: Bearer <forge-jwt>}</li>
     *   <li><b>Method:</b> {@code POST /v1/matchmaking/leave}</li>
     *   <li><b>Request:</b> no body</li>
     *   <li><b>Response:</b> {@link QueueLeaveResponse} with {@code status=left_queue} (200 OK)</li>
     *   <li><b>Errors:</b>
     *     <ul>
     *       <li>{@code FORGE_INVALID_TOKEN} (401) — missing, expired, or malformed JWT</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    @PostMapping("/v1/matchmaking/leave")
    public QueueLeaveResponse leaveMatchmakingQueue(Authentication authentication) {
        ForgeJwtAuthenticationToken jwtAuth = (ForgeJwtAuthenticationToken) authentication;
        ForgeJwtService.ForgeAccessTokenClaims claims = jwtAuth.getAccessTokenClaims();
        return matchmakingService.leaveQueue(claims.gameId(), claims.playerId());
    }

    /**
     * Returns the authenticated player's most recent active queue ticket, or a {@code not_queued}
     * response when no active ticket exists.
     *
     * <ul>
     *   <li><b>Auth:</b> {@code Authorization: Bearer <forge-jwt>}</li>
     *   <li><b>Method:</b> {@code GET /v1/matchmaking/status}</li>
     *   <li><b>Request:</b> no body</li>
     *   <li><b>Response:</b> {@link QueueStatusResponse} with {@code status} and optional ticket fields (200 OK)</li>
     *   <li><b>Errors:</b>
     *     <ul>
     *       <li>{@code FORGE_INVALID_TOKEN} (401) — missing, expired, or malformed JWT</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    @GetMapping("/v1/matchmaking/status")
    public QueueStatusResponse getMatchmakingStatus(Authentication authentication) {
        ForgeJwtAuthenticationToken jwtAuth = (ForgeJwtAuthenticationToken) authentication;
        ForgeJwtService.ForgeAccessTokenClaims claims = jwtAuth.getAccessTokenClaims();
        return matchmakingService.getQueueStatus(claims.gameId(), claims.playerId());
    }

    /**
     * Stamps a heartbeat on the authenticated player's active queue ticket. Used by the queue
     * eviction loop to distinguish live clients from abandoned ones.
     *
     * <ul>
     *   <li><b>Auth:</b> {@code Authorization: Bearer <forge-jwt>}</li>
     *   <li><b>Method:</b> {@code POST /v1/matchmaking/heartbeat}</li>
     *   <li><b>Request:</b> no body</li>
     *   <li><b>Response:</b> {@link HeartbeatResponse} with current {@code status} and {@code next_heartbeat_due_in_seconds} (200 OK)</li>
     *   <li><b>Errors:</b>
     *     <ul>
     *       <li>{@code FORGE_INVALID_TOKEN} (401) — missing, expired, or malformed JWT</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    @PostMapping("/v1/matchmaking/heartbeat")
    public HeartbeatResponse heartbeatMatchmaking(Authentication authentication) {
        ForgeJwtAuthenticationToken jwtAuth = (ForgeJwtAuthenticationToken) authentication;
        ForgeJwtService.ForgeAccessTokenClaims claims = jwtAuth.getAccessTokenClaims();
        return matchmakingService.heartbeat(claims.gameId(), claims.playerId());
    }
}
