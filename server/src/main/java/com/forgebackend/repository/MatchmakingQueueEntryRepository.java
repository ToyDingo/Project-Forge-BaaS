package com.forgebackend.repository;

import com.forgebackend.entity.MatchmakingQueueEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link MatchmakingQueueEntry}.
 * <p>
 * Provides the lookups required by the matchmaking service and the Camel routes:
 * finding a player's active ticket, finding compatible queued pairs to form matches,
 * and sweeping for timed-out or stale-heartbeat tickets.
 */
public interface MatchmakingQueueEntryRepository extends JpaRepository<MatchmakingQueueEntry, UUID> {

    /**
     * Returns the single active ticket for a player in a specific game mode, if one exists.
     * An entry is active while its status is {@code QUEUED} or {@code MATCHED}.
     */
    @Query("""
            SELECT e FROM MatchmakingQueueEntry e
            WHERE e.game.id = :gameId
              AND e.player.id = :playerId
              AND e.mode = :mode
              AND e.status IN (
                com.forgebackend.entity.MatchmakingQueueEntry.Status.QUEUED,
                com.forgebackend.entity.MatchmakingQueueEntry.Status.MATCHED)
            """)
    Optional<MatchmakingQueueEntry> findActiveEntry(
            @Param("gameId") UUID gameId,
            @Param("playerId") UUID playerId,
            @Param("mode") String mode);

    /**
     * Returns the most recent active ticket across all modes for a player; used by the
     * status endpoint where the client does not need to specify a mode.
     */
    @Query("""
            SELECT e FROM MatchmakingQueueEntry e
            WHERE e.game.id = :gameId
              AND e.player.id = :playerId
              AND e.status IN (
                com.forgebackend.entity.MatchmakingQueueEntry.Status.QUEUED,
                com.forgebackend.entity.MatchmakingQueueEntry.Status.MATCHED)
            ORDER BY e.joinedAt DESC
            """)
    List<MatchmakingQueueEntry> findActiveEntriesForPlayer(
            @Param("gameId") UUID gameId,
            @Param("playerId") UUID playerId,
            Pageable pageable);

    /**
     * Returns queued tickets for a specific game and mode, ordered by earliest joined first.
     * Used by the matchmaker to form pairs deterministically.
     */
    @Query("""
            SELECT e FROM MatchmakingQueueEntry e
            WHERE e.game.id = :gameId
              AND e.mode = :mode
              AND e.status = com.forgebackend.entity.MatchmakingQueueEntry.Status.QUEUED
            ORDER BY e.joinedAt ASC
            """)
    List<MatchmakingQueueEntry> findQueuedByGameAndMode(
            @Param("gameId") UUID gameId,
            @Param("mode") String mode);

    /**
     * Returns distinct (game, mode) combinations that currently have at least one queued ticket.
     * Used by the matchmaker to iterate scopes without a separate registry.
     */
    @Query("""
            SELECT DISTINCT e.game.id, e.mode FROM MatchmakingQueueEntry e
            WHERE e.status = com.forgebackend.entity.MatchmakingQueueEntry.Status.QUEUED
            """)
    List<Object[]> findDistinctQueuedGameAndModePairs();

    /**
     * Returns queued tickets whose timeout moment has already passed.
     */
    @Query("""
            SELECT e FROM MatchmakingQueueEntry e
            WHERE e.status = com.forgebackend.entity.MatchmakingQueueEntry.Status.QUEUED
              AND e.timeoutAt <= :now
            """)
    List<MatchmakingQueueEntry> findTimedOut(@Param("now") Instant now);

    /**
     * Returns queued tickets that have not heartbeat since the supplied threshold moment.
     */
    @Query("""
            SELECT e FROM MatchmakingQueueEntry e
            WHERE e.status = com.forgebackend.entity.MatchmakingQueueEntry.Status.QUEUED
              AND e.lastHeartbeatAt < :threshold
            """)
    List<MatchmakingQueueEntry> findStaleHeartbeats(@Param("threshold") Instant threshold);
}
