package com.forgebackend.service;

import com.forgebackend.dto.LeaderboardEntryResponse;
import com.forgebackend.dto.LeaderboardPageResponse;
import com.forgebackend.dto.MatchReportResponse;
import com.forgebackend.dto.MatchResultRequest;
import com.forgebackend.dto.PlayerRankResponse;
import com.forgebackend.entity.CompletedMatch;
import com.forgebackend.entity.Game;
import com.forgebackend.entity.MatchReport;
import com.forgebackend.entity.Player;
import com.forgebackend.entity.PlayerStats;
import com.forgebackend.exception.ForgeApiException;
import com.forgebackend.exception.ForgeErrorCode;
import com.forgebackend.repository.CompletedMatchRepository;
import com.forgebackend.repository.GameRepository;
import com.forgebackend.repository.MatchReportRepository;
import com.forgebackend.repository.PlayerRepository;
import com.forgebackend.repository.PlayerStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages match result reporting, reconciliation, and leaderboard queries.
 */
@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);
    private static final int MAX_PAGE_SIZE = 10;

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final MatchReportRepository matchReportRepository;
    private final PlayerStatsRepository playerStatsRepository;
    private final CompletedMatchRepository completedMatchRepository;

    public LeaderboardService(
            GameRepository gameRepository,
            PlayerRepository playerRepository,
            MatchReportRepository matchReportRepository,
            PlayerStatsRepository playerStatsRepository,
            CompletedMatchRepository completedMatchRepository) {
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
        this.matchReportRepository = matchReportRepository;
        this.playerStatsRepository = playerStatsRepository;
        this.completedMatchRepository = completedMatchRepository;
    }

    /**
     * Records a match report from a single client. When both players have reported,
     * the results are reconciled and {@code player_stats} rows are updated.
     */
    @Transactional
    public MatchReportResponse reportMatchResult(UUID gameId, UUID reporterId, MatchResultRequest request) {
        if (completedMatchRepository.existsByGame_IdAndMatchId(gameId, request.matchId())) {
            throw new ForgeApiException(
                    ForgeErrorCode.LEADERBOARD_DUPLICATE_REPORT,
                    "This match has already been finalized");
        }

        if (request.winnerId().equals(request.loserId())) {
            throw new ForgeApiException(ForgeErrorCode.LEADERBOARD_INVALID_RESULT,
                    "winner_id and loser_id must be different players");
        }

        if (!request.winnerId().equals(reporterId) && !request.loserId().equals(reporterId)) {
            throw new ForgeApiException(ForgeErrorCode.LEADERBOARD_INVALID_RESULT,
                    "Reporter must be either the winner or the loser");
        }

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ForgeApiException(ForgeErrorCode.FORGE_GAME_NOT_FOUND));

        Player winner = playerRepository.findById(request.winnerId())
                .filter(p -> p.getGame().getId().equals(gameId))
                .orElseThrow(() -> new ForgeApiException(ForgeErrorCode.LEADERBOARD_INVALID_RESULT,
                        "winner_id does not belong to this game"));

        Player loser = playerRepository.findById(request.loserId())
                .filter(p -> p.getGame().getId().equals(gameId))
                .orElseThrow(() -> new ForgeApiException(ForgeErrorCode.LEADERBOARD_INVALID_RESULT,
                        "loser_id does not belong to this game"));

        Player reporter = reporterId.equals(winner.getId()) ? winner : loser;

        if (matchReportRepository.existsByGame_IdAndMatchIdAndReporter_Id(gameId, request.matchId(), reporterId)) {
            throw new ForgeApiException(ForgeErrorCode.LEADERBOARD_DUPLICATE_REPORT);
        }

        MatchReport report = new MatchReport(
                UUID.randomUUID(), request.matchId(), game, reporter, winner, loser);
        matchReportRepository.save(report);

        List<MatchReport> reports = matchReportRepository.findByGame_IdAndMatchId(gameId, request.matchId());
        if (reports.size() < 2) {
            return new MatchReportResponse("pending",
                    ForgeErrorCode.LEADERBOARD_MATCH_NOT_READY.defaultMessage());
        }

        MatchReport first = reports.get(0);
        MatchReport second = reports.get(1);
        UUID reconciledWinnerId = first.getReportedWinner().getId();
        UUID reconciledLoserId = first.getReportedLoser().getId();

        if (!first.getReportedWinner().getId().equals(second.getReportedWinner().getId())) {
            log.warn("Match {} has conflicting reports — using first reporter's version (reporter={})",
                    request.matchId(), first.getReporter().getId());
        }

        PlayerStats winnerStats = playerStatsRepository
                .findByGameIdAndPlayerId(gameId, reconciledWinnerId)
                .orElseGet(() -> playerStatsRepository.save(
                        new PlayerStats(UUID.randomUUID(), game,
                                playerRepository.getReferenceById(reconciledWinnerId))));
        winnerStats.incrementWins();

        PlayerStats loserStats = playerStatsRepository
                .findByGameIdAndPlayerId(gameId, reconciledLoserId)
                .orElseGet(() -> playerStatsRepository.save(
                        new PlayerStats(UUID.randomUUID(), game,
                                playerRepository.getReferenceById(reconciledLoserId))));
        loserStats.incrementLosses();

        completedMatchRepository.save(new CompletedMatch(
                UUID.randomUUID(),
                game,
                request.matchId(),
                playerRepository.getReferenceById(reconciledWinnerId),
                playerRepository.getReferenceById(reconciledLoserId)));
        matchReportRepository.deleteByGame_IdAndMatchId(gameId, request.matchId());

        return new MatchReportResponse("completed", "Match reconciled and stats updated");
    }

    /**
     * Returns a paginated leaderboard for the given game, ranked by wins descending.
     */
    @Transactional(readOnly = true)
    public LeaderboardPageResponse getLeaderboard(UUID gameId, int page, int size) {
        int cappedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 1);
        int offset = (safePage - 1) * cappedSize;

        List<Object[]> rows = playerStatsRepository.findRankedByGameId(gameId, cappedSize, offset);
        long totalPlayers = playerStatsRepository.countByGameId(gameId);
        int totalPages = (int) Math.ceil((double) totalPlayers / cappedSize);

        List<LeaderboardEntryResponse> items = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            UUID playerId = (UUID) row[2];
            int wins = ((Number) row[3]).intValue();
            int losses = ((Number) row[4]).intValue();
            long rank = ((Number) row[7]).longValue();

            String displayName = playerRepository.findById(playerId)
                    .map(Player::getDisplayName)
                    .orElse(null);

            items.add(new LeaderboardEntryResponse(rank, playerId, displayName, wins, losses));
        }

        return new LeaderboardPageResponse(safePage, cappedSize, totalPlayers, totalPages, items);
    }

    /**
     * Returns the rank and stats for a single player within a game's leaderboard.
     */
    @Transactional(readOnly = true)
    public PlayerRankResponse getPlayerRank(UUID gameId, UUID playerId) {
        PlayerStats stats = playerStatsRepository.findByGameIdAndPlayerId(gameId, playerId)
                .orElseThrow(() -> new ForgeApiException(ForgeErrorCode.LEADERBOARD_PLAYER_NOT_FOUND));

        long rank = playerStatsRepository.findPlayerRank(gameId, playerId)
                .orElseThrow(() -> new ForgeApiException(ForgeErrorCode.LEADERBOARD_PLAYER_NOT_FOUND));

        String displayName = playerRepository.findById(playerId)
                .map(Player::getDisplayName)
                .orElse(null);

        return new PlayerRankResponse(rank, playerId, displayName, stats.getWins(), stats.getLosses());
    }
}
