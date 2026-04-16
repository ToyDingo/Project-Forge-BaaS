package com.forgebackend.repository;

import com.forgebackend.entity.PlayerStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerStatsRepository extends JpaRepository<PlayerStats, UUID> {

    Optional<PlayerStats> findByGameIdAndPlayerId(UUID gameId, UUID playerId);

    @Query(value = """
            SELECT ps.*, DENSE_RANK() OVER (
                ORDER BY ps.wins DESC, ps.losses ASC, ps.last_win_at ASC NULLS LAST, ps.player_id ASC
            ) AS rank
            FROM player_stats ps
            WHERE ps.game_id = :gameId
            ORDER BY wins DESC, losses ASC, last_win_at ASC NULLS LAST, player_id ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Object[]> findRankedByGameId(@Param("gameId") UUID gameId, @Param("limit") int limit, @Param("offset") int offset);

    @Query(value = """
            SELECT rank FROM (
                SELECT ps.player_id, DENSE_RANK() OVER (
                    ORDER BY ps.wins DESC, ps.losses ASC, ps.last_win_at ASC NULLS LAST, ps.player_id ASC
                ) AS rank
                FROM player_stats ps
                WHERE ps.game_id = :gameId
            ) ranked WHERE ranked.player_id = :playerId
            """, nativeQuery = true)
    Optional<Long> findPlayerRank(@Param("gameId") UUID gameId, @Param("playerId") UUID playerId);

    long countByGameId(UUID gameId);
}
