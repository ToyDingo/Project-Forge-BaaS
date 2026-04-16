package com.forgebackend.repository;

import com.forgebackend.entity.CompletedMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompletedMatchRepository extends JpaRepository<CompletedMatch, UUID> {

    boolean existsByGame_IdAndMatchId(UUID gameId, UUID matchId);
}
