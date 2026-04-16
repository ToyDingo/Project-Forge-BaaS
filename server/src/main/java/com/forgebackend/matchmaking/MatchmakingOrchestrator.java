package com.forgebackend.matchmaking;

import com.forgebackend.config.ForgeMatchmakingProperties;
import com.forgebackend.entity.Game;
import com.forgebackend.entity.MatchmakingMatch;
import com.forgebackend.entity.MatchmakingMatchPlayer;
import com.forgebackend.entity.MatchmakingQueueEntry;
import com.forgebackend.repository.MatchmakingMatchPlayerRepository;
import com.forgebackend.repository.MatchmakingMatchRepository;
import com.forgebackend.repository.MatchmakingQueueEntryRepository;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Houses all asynchronous matchmaking work triggered by Camel routes: match formation,
 * queue eviction, and match-found delivery.
 * <p>
 * This class is deliberately the only place where multi-player database state transitions
 * occur outside of the HTTP-driven {@link com.forgebackend.service.MatchmakingService}.
 * Routes in {@link MatchmakingCamelRoutes} are thin wrappers that call methods here so the
 * Camel layer never touches repositories directly.
 */
@Component
public class MatchmakingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MatchmakingOrchestrator.class);

    /** Number of participants required to form a match at MVP (1v1). */
    private static final int MATCH_SIZE = 2;

    /** Camel direct endpoint that triggers notification delivery for a match id. */
    public static final String DELIVER_MATCH_ENDPOINT = "direct:matchmaking-deliver";

    /** How long a formed match stays in {@code PENDING_NOTIFY} before it is abandoned. */
    private static final Duration MATCH_EXPIRY = Duration.ofSeconds(60);

    private final MatchmakingQueueEntryRepository queueRepository;
    private final MatchmakingMatchRepository matchRepository;
    private final MatchmakingMatchPlayerRepository matchPlayerRepository;
    private final ForgeEventBus eventBus;
    private final ForgeMatchmakingProperties matchmakingProperties;
    private final Clock clock;

    /**
     * Producer template is resolved lazily to avoid a circular dependency at startup;
     * Camel's context is built after Spring bean wiring completes.
     */
    @Lazy
    private final ProducerTemplate producerTemplate;

    @Autowired
    public MatchmakingOrchestrator(
            MatchmakingQueueEntryRepository queueRepository,
            MatchmakingMatchRepository matchRepository,
            MatchmakingMatchPlayerRepository matchPlayerRepository,
            ForgeEventBus eventBus,
            ForgeMatchmakingProperties matchmakingProperties,
            Clock clock,
            @Lazy ProducerTemplate producerTemplate) {
        this.queueRepository = queueRepository;
        this.matchRepository = matchRepository;
        this.matchPlayerRepository = matchPlayerRepository;
        this.eventBus = eventBus;
        this.matchmakingProperties = matchmakingProperties;
        this.clock = clock;
        this.producerTemplate = producerTemplate;
    }

    /**
     * Scans every (game, mode) pair that has queued entries and forms matches greedily
     * by join-time order. Freshly formed matches are dispatched to the notification route.
     */
    @Transactional
    public void formMatches() {
        List<Object[]> pairs = queueRepository.findDistinctQueuedGameAndModePairs();
        for (Object[] row : pairs) {
            UUID gameId = (UUID) row[0];
            String mode = (String) row[1];
            formMatchesForScope(gameId, mode);
        }
    }

    /**
     * Forms as many matches as possible for a specific (game, mode) scope. Exposed as a
     * separate method so tests can drive a single scope without seeding multiple modes.
     */
    @Transactional
    public void formMatchesForScope(UUID gameId, String mode) {
        List<MatchmakingQueueEntry> queued = queueRepository.findQueuedByGameAndMode(gameId, mode);
        if (queued.size() < MATCH_SIZE) {
            return;
        }
        Instant now = Instant.now(clock);
        for (int i = 0; i + MATCH_SIZE <= queued.size(); i += MATCH_SIZE) {
            List<MatchmakingQueueEntry> participants = queued.subList(i, i + MATCH_SIZE);
            Game game = participants.get(0).getGame();

            MatchmakingMatch match = matchRepository.save(
                    new MatchmakingMatch(UUID.randomUUID(), game, mode, now.plus(MATCH_EXPIRY)));

            for (MatchmakingQueueEntry participant : participants) {
                matchPlayerRepository.save(
                        new MatchmakingMatchPlayer(match.getId(), participant.getPlayer().getId()));
                participant.setStatus(MatchmakingQueueEntry.Status.MATCHED);
            }

            log.info("Formed match {} in game {} mode {} with {} players",
                    match.getId(), gameId, mode, participants.size());

            producerTemplate.sendBody(DELIVER_MATCH_ENDPOINT, match.getId());
        }
    }

    /**
     * Attempts to deliver a {@code match_found} event to every pending participant for the
     * given match. If any participant fails to receive the event, an exception is thrown so
     * Camel's redelivery policy can retry. When all participants are delivered, the match is
     * promoted to {@link MatchmakingMatch.Status#READY}.
     *
     * @throws ForgeEventBus.EventDeliveryException if any participant could not be notified.
     */
    @Transactional
    public void deliverMatchNotification(UUID matchId) {
        MatchmakingMatch match = matchRepository.findById(matchId).orElse(null);
        if (match == null || match.getStatus() != MatchmakingMatch.Status.PENDING_NOTIFY) {
            return;
        }

        List<MatchmakingMatchPlayer> rows = matchPlayerRepository.findByIdMatchId(matchId);
        List<MatchFoundEvent.Participant> participants = new ArrayList<>(rows.size());
        for (MatchmakingMatchPlayer row : rows) {
            participants.add(new MatchFoundEvent.Participant(row.getPlayerId()));
        }

        MatchFoundEvent event = MatchFoundEvent.of(
                match.getId(),
                match.getMode(),
                List.copyOf(participants),
                match.getCreatedAt(),
                match.getExpiresAt(),
                null);

        boolean anyFailed = false;
        String firstFailureMessage = null;
        for (MatchmakingMatchPlayer row : rows) {
            if (row.getDeliveryStatus() == MatchmakingMatchPlayer.DeliveryStatus.DELIVERED) {
                continue;
            }
            try {
                eventBus.pushMatchFound(row.getPlayerId(), event);
                row.setDeliveryStatus(MatchmakingMatchPlayer.DeliveryStatus.DELIVERED);
            } catch (ForgeEventBus.EventDeliveryException ex) {
                anyFailed = true;
                if (firstFailureMessage == null) {
                    firstFailureMessage = ex.getMessage();
                }
                log.debug("match_found delivery failed for player {} in match {}: {}",
                        row.getPlayerId(), matchId, ex.getMessage());
            }
        }

        if (anyFailed) {
            throw new ForgeEventBus.EventDeliveryException(
                    firstFailureMessage != null ? firstFailureMessage
                            : "At least one participant could not be notified for match " + matchId);
        }

        match.setStatus(MatchmakingMatch.Status.READY);
        log.info("Match {} promoted to READY after successful notification of {} players",
                matchId, rows.size());
    }

    /**
     * Invoked by Camel after notification retries are exhausted. Cancels the match, marks any
     * still-pending participants as {@code FAILED}, and returns the original queue entries to
     * {@code QUEUED} so they can be matched again.
     */
    @Transactional
    public void onDeliveryExhausted(UUID matchId) {
        MatchmakingMatch match = matchRepository.findById(matchId).orElse(null);
        if (match == null) {
            return;
        }
        match.setStatus(MatchmakingMatch.Status.CANCELLED);

        List<MatchmakingMatchPlayer> rows = matchPlayerRepository.findByIdMatchId(matchId);
        Instant now = Instant.now(clock);
        for (MatchmakingMatchPlayer row : rows) {
            if (row.getDeliveryStatus() != MatchmakingMatchPlayer.DeliveryStatus.DELIVERED) {
                row.setDeliveryStatus(MatchmakingMatchPlayer.DeliveryStatus.FAILED);
            }
            requeueForPlayer(match.getGame().getId(), row.getPlayerId(), now);
        }
        log.warn("Match {} cancelled after exhausting notification retries", matchId);
    }

    /**
     * Returns a MATCHED queue entry to QUEUED so the matchmaker can pair it again. Only the
     * most recent MATCHED entry for the player is considered to avoid touching unrelated rows.
     */
    private void requeueForPlayer(UUID gameId, UUID playerId, Instant now) {
        Optional<MatchmakingQueueEntry> candidate = queueRepository
                .findActiveEntriesForPlayer(gameId, playerId, org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().findFirst();
        candidate.ifPresent(entry -> {
            if (entry.getStatus() == MatchmakingQueueEntry.Status.MATCHED) {
                entry.setStatus(MatchmakingQueueEntry.Status.QUEUED);
                entry.markHeartbeat(now);
            }
        });
    }

    /**
     * Evicts queue entries whose timeout moment has passed or whose heartbeat has gone stale,
     * and pushes a {@code queue_timeout} event for each timed-out ticket.
     */
    @Transactional
    public void evictExpired() {
        Instant now = Instant.now(clock);

        for (MatchmakingQueueEntry entry : queueRepository.findTimedOut(now)) {
            entry.setStatus(MatchmakingQueueEntry.Status.TIMED_OUT);
            eventBus.pushQueueTimeout(entry.getPlayer().getId(),
                    QueueTimeoutEvent.of(entry.getId(),
                            "Queue timeout reached after "
                                    + matchmakingProperties.queueTimeoutSeconds() + " seconds"));
            log.info("Queue entry {} for player {} timed out", entry.getId(), entry.getPlayer().getId());
        }

        Duration staleWindow = Duration.ofSeconds(
                (long) matchmakingProperties.heartbeatIntervalSeconds()
                        * matchmakingProperties.staleHeartbeatThreshold());
        Instant staleThreshold = now.minus(staleWindow);
        for (MatchmakingQueueEntry entry : queueRepository.findStaleHeartbeats(staleThreshold)) {
            entry.setStatus(MatchmakingQueueEntry.Status.STALE_REMOVED);
            log.info("Queue entry {} for player {} stale-removed (last heartbeat {})",
                    entry.getId(), entry.getPlayer().getId(), entry.getLastHeartbeatAt());
        }
    }
}
