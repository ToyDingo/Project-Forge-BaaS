package com.forgebackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebackend.config.ForgeMatchmakingProperties;
import com.forgebackend.dto.HeartbeatResponse;
import com.forgebackend.dto.QueueJoinRequest;
import com.forgebackend.dto.QueueJoinResponse;
import com.forgebackend.dto.QueueLeaveResponse;
import com.forgebackend.entity.Game;
import com.forgebackend.entity.MatchmakingMatch;
import com.forgebackend.entity.MatchmakingMatchPlayer;
import com.forgebackend.entity.MatchmakingQueueEntry;
import com.forgebackend.entity.Player;
import com.forgebackend.exception.ForgeApiException;
import com.forgebackend.exception.ForgeErrorCode;
import com.forgebackend.matchmaking.ForgeEventBus;
import com.forgebackend.matchmaking.MatchFoundEvent;
import com.forgebackend.matchmaking.MatchmakingOrchestrator;
import com.forgebackend.matchmaking.QueueTimeoutEvent;
import com.forgebackend.repository.GameRepository;
import com.forgebackend.repository.MatchmakingMatchPlayerRepository;
import com.forgebackend.repository.MatchmakingMatchRepository;
import com.forgebackend.repository.MatchmakingQueueEntryRepository;
import com.forgebackend.repository.PlayerRepository;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 1 unit tests for the matchmaking vertical slice. Covers the nine scenarios in the plan:
 * <ol>
 *   <li>Join queue happy path.</li>
 *   <li>Duplicate join conflict.</li>
 *   <li>Leave idempotency (with and without active entry).</li>
 *   <li>Heartbeat updates last_heartbeat_at on active entry.</li>
 *   <li>Stale eviction when the heartbeat threshold has elapsed.</li>
 *   <li>Timeout eviction when timeout_at has elapsed.</li>
 *   <li>Compatible 1v1 pair is matched.</li>
 *   <li>Notification delivery failure surfaces an exception so Camel retries.</li>
 *   <li>Retry exhaustion cancels the match and returns participants to the queue.</li>
 * </ol>
 * All collaborators are mocked; no database or Camel context is loaded.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchmakingServiceTest {

    private static final ForgeMatchmakingProperties PROPERTIES =
            new ForgeMatchmakingProperties(60, 10, 2, 3, 5);

    @Mock private GameRepository gameRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private MatchmakingQueueEntryRepository queueRepository;
    @Mock private MatchmakingMatchRepository matchRepository;
    @Mock private MatchmakingMatchPlayerRepository matchPlayerRepository;
    @Mock private ForgeEventBus eventBus;
    @Mock private ProducerTemplate producerTemplate;

    private MatchmakingService matchmakingService;
    private MatchmakingOrchestrator orchestrator;

    private final Instant fixedNow = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);

    private final UUID gameId = UUID.randomUUID();
    private final UUID playerAId = UUID.randomUUID();
    private final UUID playerBId = UUID.randomUUID();

    private Game game;
    private Player playerA;
    private Player playerB;

    @BeforeEach
    void setUp() {
        game = new Game(gameId, "Test Game", "hash", "lookup", 480L, "steam-web-api-key");
        playerA = new Player(playerAId, game, "steam", "steamA", "Alpha");
        playerB = new Player(playerBId, game, "steam", "steamB", "Bravo");

        matchmakingService = new MatchmakingService(
                gameRepository, playerRepository, queueRepository, PROPERTIES, new ObjectMapper(), fixedClock);

        orchestrator = new MatchmakingOrchestrator(
                queueRepository, matchRepository, matchPlayerRepository,
                eventBus, PROPERTIES, fixedClock, producerTemplate);

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(playerRepository.findById(playerAId)).thenReturn(Optional.of(playerA));
        when(playerRepository.findById(playerBId)).thenReturn(Optional.of(playerB));
    }

    // 1. Join queue happy path.
    @Test
    void joinQueue_createsQueuedEntryAndReturnsQueuedResponse() {
        when(queueRepository.findActiveEntry(eq(gameId), eq(playerAId), eq("ranked_1v1")))
                .thenReturn(Optional.empty());
        when(queueRepository.save(any(MatchmakingQueueEntry.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        QueueJoinRequest request = new QueueJoinRequest("ranked_1v1", "1.0.0", "us-east", null);
        QueueJoinResponse response = matchmakingService.joinQueue(gameId, playerAId, "steam", request);

        assertThat(response.queueTicketId()).isNotNull();
        assertThat(response.status()).isEqualTo("queued");
        assertThat(response.timeoutAt()).isEqualTo(fixedNow.plus(Duration.ofSeconds(60)));

        ArgumentCaptor<MatchmakingQueueEntry> captor = ArgumentCaptor.forClass(MatchmakingQueueEntry.class);
        verify(queueRepository).save(captor.capture());
        MatchmakingQueueEntry saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(MatchmakingQueueEntry.Status.QUEUED);
        assertThat(saved.getMode()).isEqualTo("ranked_1v1");
        assertThat(saved.getClientVersion()).isEqualTo("1.0.0");
        assertThat(saved.getPlatform()).isEqualTo("steam");
    }

    // 2. Duplicate join conflict.
    @Test
    void joinQueue_throwsAlreadyQueued_whenActiveEntryExists() {
        MatchmakingQueueEntry existing = new MatchmakingQueueEntry(
                UUID.randomUUID(), game, playerA, "ranked_1v1", "1.0.0", "steam", null, null,
                fixedNow.plusSeconds(60));
        when(queueRepository.findActiveEntry(gameId, playerAId, "ranked_1v1"))
                .thenReturn(Optional.of(existing));

        QueueJoinRequest request = new QueueJoinRequest("ranked_1v1", "1.0.0", null, null);
        assertThatThrownBy(() -> matchmakingService.joinQueue(gameId, playerAId, "steam", request))
                .isInstanceOf(ForgeApiException.class)
                .extracting(ex -> ((ForgeApiException) ex).getErrorCode())
                .isEqualTo(ForgeErrorCode.MATCHMAKING_ALREADY_QUEUED);

        verify(queueRepository, never()).save(any());
    }

    // 3. Leave idempotency: with and without an active entry always returns left_queue.
    @Test
    void leaveQueue_isIdempotent_whenNoActiveEntry() {
        when(queueRepository.findActiveEntriesForPlayer(eq(gameId), eq(playerAId), any(Pageable.class)))
                .thenReturn(List.of());

        QueueLeaveResponse response = matchmakingService.leaveQueue(gameId, playerAId);

        assertThat(response.status()).isEqualTo("left_queue");
    }

    @Test
    void leaveQueue_transitionsActiveEntryToLeftQueue() {
        MatchmakingQueueEntry active = new MatchmakingQueueEntry(
                UUID.randomUUID(), game, playerA, "ranked_1v1", "1.0.0", "steam", null, null,
                fixedNow.plusSeconds(60));
        when(queueRepository.findActiveEntriesForPlayer(eq(gameId), eq(playerAId), any(Pageable.class)))
                .thenReturn(List.of(active));

        QueueLeaveResponse response = matchmakingService.leaveQueue(gameId, playerAId);

        assertThat(response.status()).isEqualTo("left_queue");
        assertThat(active.getStatus()).isEqualTo(MatchmakingQueueEntry.Status.LEFT_QUEUE);
    }

    // 4. Heartbeat updates last_heartbeat_at.
    @Test
    void heartbeat_updatesLastHeartbeatAtOnActiveEntry() {
        MatchmakingQueueEntry active = new MatchmakingQueueEntry(
                UUID.randomUUID(), game, playerA, "ranked_1v1", "1.0.0", "steam", null, null,
                fixedNow.plusSeconds(60));
        active.markHeartbeat(fixedNow.minusSeconds(30));
        when(queueRepository.findActiveEntriesForPlayer(eq(gameId), eq(playerAId), any(Pageable.class)))
                .thenReturn(List.of(active));

        HeartbeatResponse response = matchmakingService.heartbeat(gameId, playerAId);

        assertThat(response.status()).isEqualTo("queued");
        assertThat(response.nextHeartbeatDueInSeconds()).isEqualTo(10);
        assertThat(active.getLastHeartbeatAt()).isEqualTo(fixedNow);
    }

    // 5. Stale eviction.
    @Test
    void evictExpired_transitionsStaleEntryToStaleRemoved() {
        // staleWindow = heartbeatInterval(10) * staleThreshold(2) = 20s; threshold = now - 20s.
        Instant staleThreshold = fixedNow.minus(Duration.ofSeconds(20L));
        MatchmakingQueueEntry stale = new MatchmakingQueueEntry(
                UUID.randomUUID(), game, playerA, "ranked_1v1", "1.0.0", "steam", null, null,
                fixedNow.plusSeconds(60));
        stale.markHeartbeat(staleThreshold.minusSeconds(1));

        when(queueRepository.findTimedOut(fixedNow)).thenReturn(List.of());
        when(queueRepository.findStaleHeartbeats(staleThreshold)).thenReturn(List.of(stale));

        orchestrator.evictExpired();

        assertThat(stale.getStatus()).isEqualTo(MatchmakingQueueEntry.Status.STALE_REMOVED);
    }

    // 6. Timeout eviction.
    @Test
    void evictExpired_transitionsTimedOutEntryAndPushesEvent() {
        MatchmakingQueueEntry expired = new MatchmakingQueueEntry(
                UUID.randomUUID(), game, playerA, "ranked_1v1", "1.0.0", "steam", null, null,
                fixedNow.minusSeconds(5));

        when(queueRepository.findTimedOut(fixedNow)).thenReturn(List.of(expired));
        when(queueRepository.findStaleHeartbeats(any(Instant.class))).thenReturn(List.of());

        orchestrator.evictExpired();

        assertThat(expired.getStatus()).isEqualTo(MatchmakingQueueEntry.Status.TIMED_OUT);
        ArgumentCaptor<QueueTimeoutEvent> captor = ArgumentCaptor.forClass(QueueTimeoutEvent.class);
        verify(eventBus).pushQueueTimeout(eq(playerAId), captor.capture());
        assertThat(captor.getValue().queueTicketId()).isEqualTo(expired.getId());
    }

    // 7. Compatible 1v1 pair is matched.
    @Test
    void formMatches_createsMatchAndTransitionsEntriesToMatched() {
        MatchmakingQueueEntry a = new MatchmakingQueueEntry(
                UUID.randomUUID(), game, playerA, "ranked_1v1", "1.0.0", "steam", null, null,
                fixedNow.plusSeconds(60));
        MatchmakingQueueEntry b = new MatchmakingQueueEntry(
                UUID.randomUUID(), game, playerB, "ranked_1v1", "1.0.0", "steam", null, null,
                fixedNow.plusSeconds(60));

        List<Object[]> scopes = new ArrayList<>();
        scopes.add(new Object[]{gameId, "ranked_1v1"});
        when(queueRepository.findDistinctQueuedGameAndModePairs()).thenReturn(scopes);
        when(queueRepository.findQueuedByGameAndMode(gameId, "ranked_1v1"))
                .thenReturn(new ArrayList<>(List.of(a, b)));
        when(matchRepository.save(any(MatchmakingMatch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(matchPlayerRepository.save(any(MatchmakingMatchPlayer.class))).thenAnswer(inv -> inv.getArgument(0));

        orchestrator.formMatches();

        ArgumentCaptor<MatchmakingMatch> matchCaptor = ArgumentCaptor.forClass(MatchmakingMatch.class);
        verify(matchRepository).save(matchCaptor.capture());
        assertThat(matchCaptor.getValue().getMode()).isEqualTo("ranked_1v1");
        assertThat(matchCaptor.getValue().getStatus()).isEqualTo(MatchmakingMatch.Status.PENDING_NOTIFY);

        verify(matchPlayerRepository, times(2)).save(any(MatchmakingMatchPlayer.class));
        assertThat(a.getStatus()).isEqualTo(MatchmakingQueueEntry.Status.MATCHED);
        assertThat(b.getStatus()).isEqualTo(MatchmakingQueueEntry.Status.MATCHED);

        verify(producerTemplate).sendBody(eq(MatchmakingOrchestrator.DELIVER_MATCH_ENDPOINT), eq(matchCaptor.getValue().getId()));
    }

    // 8. Notification retry: delivery failure surfaces as an exception so Camel retries.
    //    (Camel policy "3 retries at 5s intervals" is verified by ForgeMatchmakingProperties.)
    @Test
    void deliverMatchNotification_throwsEventDeliveryException_whenAnyPlayerFails() {
        UUID matchId = UUID.randomUUID();
        MatchmakingMatch match = new MatchmakingMatch(matchId, game, "ranked_1v1", fixedNow.plusSeconds(60));
        MatchmakingMatchPlayer participantA = new MatchmakingMatchPlayer(matchId, playerAId);
        MatchmakingMatchPlayer participantB = new MatchmakingMatchPlayer(matchId, playerBId);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(matchPlayerRepository.findByIdMatchId(matchId))
                .thenReturn(List.of(participantA, participantB));
        doThrow(new ForgeEventBus.EventDeliveryException("unreachable"))
                .when(eventBus).pushMatchFound(eq(playerBId), any(MatchFoundEvent.class));

        assertThatThrownBy(() -> orchestrator.deliverMatchNotification(matchId))
                .isInstanceOf(ForgeEventBus.EventDeliveryException.class);

        assertThat(participantA.getDeliveryStatus())
                .isEqualTo(MatchmakingMatchPlayer.DeliveryStatus.DELIVERED);
        assertThat(participantB.getDeliveryStatus())
                .isEqualTo(MatchmakingMatchPlayer.DeliveryStatus.PENDING);
        assertThat(match.getStatus()).isEqualTo(MatchmakingMatch.Status.PENDING_NOTIFY);

        assertThat(PROPERTIES.notificationRetryCount()).isEqualTo(3);
        assertThat(PROPERTIES.notificationRetryIntervalSeconds()).isEqualTo(5);
    }

    // 9. Retry exhaustion: match cancelled, both participants re-queued.
    @Test
    void onDeliveryExhausted_cancelsMatchAndReQueuesParticipants() {
        UUID matchId = UUID.randomUUID();
        MatchmakingMatch match = new MatchmakingMatch(matchId, game, "ranked_1v1", fixedNow.plusSeconds(60));
        MatchmakingMatchPlayer participantA = new MatchmakingMatchPlayer(matchId, playerAId);
        MatchmakingMatchPlayer participantB = new MatchmakingMatchPlayer(matchId, playerBId);

        MatchmakingQueueEntry entryA = new MatchmakingQueueEntry(
                UUID.randomUUID(), game, playerA, "ranked_1v1", "1.0.0", "steam", null, null,
                fixedNow.plusSeconds(60));
        entryA.setStatus(MatchmakingQueueEntry.Status.MATCHED);
        MatchmakingQueueEntry entryB = new MatchmakingQueueEntry(
                UUID.randomUUID(), game, playerB, "ranked_1v1", "1.0.0", "steam", null, null,
                fixedNow.plusSeconds(60));
        entryB.setStatus(MatchmakingQueueEntry.Status.MATCHED);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(matchPlayerRepository.findByIdMatchId(matchId))
                .thenReturn(List.of(participantA, participantB));
        when(queueRepository.findActiveEntriesForPlayer(eq(gameId), eq(playerAId), any(Pageable.class)))
                .thenReturn(List.of(entryA));
        when(queueRepository.findActiveEntriesForPlayer(eq(gameId), eq(playerBId), any(Pageable.class)))
                .thenReturn(List.of(entryB));

        orchestrator.onDeliveryExhausted(matchId);

        assertThat(match.getStatus()).isEqualTo(MatchmakingMatch.Status.CANCELLED);
        assertThat(participantA.getDeliveryStatus())
                .isEqualTo(MatchmakingMatchPlayer.DeliveryStatus.FAILED);
        assertThat(participantB.getDeliveryStatus())
                .isEqualTo(MatchmakingMatchPlayer.DeliveryStatus.FAILED);
        assertThat(entryA.getStatus()).isEqualTo(MatchmakingQueueEntry.Status.QUEUED);
        assertThat(entryB.getStatus()).isEqualTo(MatchmakingQueueEntry.Status.QUEUED);
    }
}
