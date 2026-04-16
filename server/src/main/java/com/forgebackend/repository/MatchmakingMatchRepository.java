package com.forgebackend.repository;

import com.forgebackend.entity.MatchmakingMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for {@link MatchmakingMatch}.
 */
public interface MatchmakingMatchRepository extends JpaRepository<MatchmakingMatch, UUID> {

    /** Returns matches that still have at least one participant waiting to be notified. */
    List<MatchmakingMatch> findByStatus(MatchmakingMatch.Status status);
}
