package com.forgebackend.repository;

import com.forgebackend.entity.MatchReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchReportRepository extends JpaRepository<MatchReport, UUID> {

    List<MatchReport> findByGame_IdAndMatchId(UUID gameId, UUID matchId);

    boolean existsByGame_IdAndMatchIdAndReporter_Id(UUID gameId, UUID matchId, UUID reporterId);

    void deleteByGame_IdAndMatchId(UUID gameId, UUID matchId);
}
