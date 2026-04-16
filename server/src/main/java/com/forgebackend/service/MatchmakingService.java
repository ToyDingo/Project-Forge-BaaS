package com.forgebackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebackend.config.ForgeMatchmakingProperties;
import com.forgebackend.dto.HeartbeatResponse;
import com.forgebackend.dto.QueueJoinRequest;
import com.forgebackend.dto.QueueJoinResponse;
import com.forgebackend.dto.QueueLeaveResponse;
import com.forgebackend.dto.QueueStatusResponse;
import com.forgebackend.entity.Game;
import com.forgebackend.entity.MatchmakingQueueEntry;
import com.forgebackend.entity.Player;
import com.forgebackend.exception.ForgeApiException;
import com.forgebackend.exception.ForgeErrorCode;
import com.forgebackend.repository.GameRepository;
import com.forgebackend.repository.MatchmakingQueueEntryRepository;
import com.forgebackend.repository.PlayerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles the synchronous HTTP-driven part of the matchmaking lifecycle.
 * <p>
 * This service owns queue-ticket creation, the leave operation, status lookup, and
 * heartbeat updates. Match formation, notification, and eviction are handled by the
 * Camel routes, which call into this service only for state transitions they need
 * performed transactionally.
 */
@Service
public class MatchmakingService {

    private static final String STATUS_QUEUED = "queued";

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final MatchmakingQueueEntryRepository queueRepository;
    private final ForgeMatchmakingProperties matchmakingProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MatchmakingService(
            GameRepository gameRepository,
            PlayerRepository playerRepository,
            MatchmakingQueueEntryRepository queueRepository,
            ForgeMatchmakingProperties matchmakingProperties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
        this.queueRepository = queueRepository;
        this.matchmakingProperties = matchmakingProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Creates a new queue ticket for the caller, rejecting duplicates.
     *
     * @throws ForgeApiException {@link ForgeErrorCode#MATCHMAKING_ALREADY_QUEUED} if the player
     *         already has an active ticket for the same mode.
     */
    @Transactional
    public QueueJoinResponse joinQueue(UUID gameId, UUID playerId, String platform, QueueJoinRequest request) {
        if (request == null || request.mode() == null || request.mode().isBlank()) {
            throw new ForgeApiException(ForgeErrorCode.FORGE_INVALID_REQUEST, "mode is required");
        }
        if (request.clientVersion() == null || request.clientVersion().isBlank()) {
            throw new ForgeApiException(ForgeErrorCode.FORGE_INVALID_REQUEST, "client_version is required");
        }

        if (queueRepository.findActiveEntry(gameId, playerId, request.mode()).isPresent()) {
            throw new ForgeApiException(ForgeErrorCode.MATCHMAKING_ALREADY_QUEUED);
        }

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ForgeApiException(ForgeErrorCode.FORGE_GAME_NOT_FOUND));
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ForgeApiException(ForgeErrorCode.FORGE_INVALID_REQUEST,
                        "Unknown player in access token"));

        Instant now = Instant.now(clock);
        Instant timeoutAt = now.plus(Duration.ofSeconds(matchmakingProperties.queueTimeoutSeconds()));

        MatchmakingQueueEntry entry = new MatchmakingQueueEntry(
                UUID.randomUUID(),
                game,
                player,
                request.mode(),
                request.clientVersion(),
                platform,
                request.region(),
                serializeLatency(request.latencyByRegionMs()),
                timeoutAt);
        entry.markHeartbeat(now);
        queueRepository.save(entry);

        return new QueueJoinResponse(entry.getId(), STATUS_QUEUED, entry.getJoinedAt(), entry.getTimeoutAt());
    }

    /**
     * Leaves the queue. Idempotent: always returns {@code left_queue} even if no active ticket
     * exists for the caller.
     */
    @Transactional
    public QueueLeaveResponse leaveQueue(UUID gameId, UUID playerId) {
        List<MatchmakingQueueEntry> active = queueRepository.findActiveEntriesForPlayer(
                gameId, playerId, PageRequest.of(0, Integer.MAX_VALUE));
        for (MatchmakingQueueEntry entry : active) {
            if (entry.getStatus() == MatchmakingQueueEntry.Status.QUEUED) {
                entry.setStatus(MatchmakingQueueEntry.Status.LEFT_QUEUE);
            }
        }
        return QueueLeaveResponse.left();
    }

    /**
     * Returns the status of the caller's most recent active ticket across all modes.
     * When no active ticket is found, returns {@link QueueStatusResponse#notQueued()}.
     */
    @Transactional(readOnly = true)
    public QueueStatusResponse getQueueStatus(UUID gameId, UUID playerId) {
        List<MatchmakingQueueEntry> active = queueRepository.findActiveEntriesForPlayer(
                gameId, playerId, PageRequest.of(0, 1));
        Optional<MatchmakingQueueEntry> latest = active.stream().findFirst();
        return latest
                .map(entry -> new QueueStatusResponse(
                        entry.getStatus().name().toLowerCase(),
                        entry.getId(),
                        entry.getJoinedAt(),
                        entry.getTimeoutAt()))
                .orElseGet(QueueStatusResponse::notQueued);
    }

    /**
     * Stamps a fresh heartbeat on the caller's most recent active ticket. When the caller has
     * no active ticket, returns a {@code not_queued} response so clients can detect eviction
     * and recover without a separate status call.
     */
    @Transactional
    public HeartbeatResponse heartbeat(UUID gameId, UUID playerId) {
        List<MatchmakingQueueEntry> active = queueRepository.findActiveEntriesForPlayer(
                gameId, playerId, PageRequest.of(0, 1));
        if (active.isEmpty()) {
            return new HeartbeatResponse("not_queued", matchmakingProperties.heartbeatIntervalSeconds());
        }
        MatchmakingQueueEntry entry = active.get(0);
        entry.markHeartbeat(Instant.now(clock));
        return new HeartbeatResponse(
                entry.getStatus().name().toLowerCase(),
                matchmakingProperties.heartbeatIntervalSeconds());
    }

    /**
     * Serializes a latency map to JSON for the JSONB column. Null or empty maps become null.
     */
    private String serializeLatency(java.util.Map<String, Integer> latency) {
        if (latency == null || latency.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(latency);
        } catch (JsonProcessingException e) {
            throw new ForgeApiException(ForgeErrorCode.FORGE_INVALID_REQUEST,
                    "latency_by_region_ms could not be serialized");
        }
    }
}
