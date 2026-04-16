package com.forgebackend.repository;

import com.forgebackend.entity.MatchmakingMatchPlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for {@link MatchmakingMatchPlayer}.
 */
public interface MatchmakingMatchPlayerRepository
        extends JpaRepository<MatchmakingMatchPlayer, MatchmakingMatchPlayer.Id> {

    /** Returns every participant row for the given match. */
    List<MatchmakingMatchPlayer> findByIdMatchId(UUID matchId);
}
