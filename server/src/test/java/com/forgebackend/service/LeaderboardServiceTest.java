package com.forgebackend.service;

import com.forgebackend.dto.MatchReportResponse;
import com.forgebackend.dto.MatchResultRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaderboardServiceTest {

    @Mock
    private GameRepository gameRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private MatchReportRepository matchReportRepository;
    @Mock
    private PlayerStatsRepository playerStatsRepository;
    @Mock
    private CompletedMatchRepository completedMatchRepository;

    private LeaderboardService leaderboardService;

    private final UUID gameId = UUID.randomUUID();
    private final UUID matchId = UUID.randomUUID();
    private final UUID winnerId = UUID.randomUUID();
    private final UUID loserId = UUID.randomUUID();

    private Game game;
    private Player winner;
    private Player loser;

    @BeforeEach
    void setUp() {
        leaderboardService = new LeaderboardService(
                gameRepository, playerRepository, matchReportRepository, playerStatsRepository, completedMatchRepository);

        game = new Game(gameId, "Test Game", "hash", "lookup", 480L, "steam-web-api-key");
        winner = new Player(winnerId, game, "steam", "steamWinner", "Winner");
        loser = new Player(loserId, game, "steam", "steamLoser", "Loser");

        when(playerRepository.findById(winnerId)).thenReturn(Optional.of(winner));
        when(playerRepository.findById(loserId)).thenReturn(Optional.of(loser));
        when(playerRepository.getReferenceById(winnerId)).thenReturn(winner);
        when(playerRepository.getReferenceById(loserId)).thenReturn(loser);
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
    }

    @Test
    void reportMatchResult_savesReportAndReturnsPending_whenFirstReport() {
        when(completedMatchRepository.existsByGame_IdAndMatchId(gameId, matchId)).thenReturn(false);
        when(matchReportRepository.existsByGame_IdAndMatchIdAndReporter_Id(gameId, matchId, winnerId)).thenReturn(false);
        when(matchReportRepository.save(any(MatchReport.class))).thenAnswer(inv -> inv.getArgument(0));

        MatchReport existingReport = new MatchReport(UUID.randomUUID(), matchId, game, winner, winner, loser);
        when(matchReportRepository.findByGame_IdAndMatchId(gameId, matchId)).thenReturn(List.of(existingReport));

        MatchReportResponse response = leaderboardService.reportMatchResult(
                gameId, winnerId, new MatchResultRequest(matchId, winnerId, loserId));

        assertThat(response.status()).isEqualTo("pending");

        ArgumentCaptor<MatchReport> captor = ArgumentCaptor.forClass(MatchReport.class);
        verify(matchReportRepository).save(captor.capture());
        MatchReport saved = captor.getValue();
        assertThat(saved.getMatchId()).isEqualTo(matchId);
        assertThat(saved.getReporter().getId()).isEqualTo(winnerId);
        assertThat(saved.getReportedWinner().getId()).isEqualTo(winnerId);
        assertThat(saved.getReportedLoser().getId()).isEqualTo(loserId);
    }

    @Test
    void reportMatchResult_reconciles_whenBothReportsAgree() {
        when(completedMatchRepository.existsByGame_IdAndMatchId(gameId, matchId)).thenReturn(false);
        when(matchReportRepository.existsByGame_IdAndMatchIdAndReporter_Id(gameId, matchId, loserId)).thenReturn(false);
        when(matchReportRepository.save(any(MatchReport.class))).thenAnswer(inv -> inv.getArgument(0));
        when(completedMatchRepository.save(any(CompletedMatch.class))).thenAnswer(inv -> inv.getArgument(0));

        MatchReport firstReport = new MatchReport(UUID.randomUUID(), matchId, game, winner, winner, loser);
        MatchReport secondReport = new MatchReport(UUID.randomUUID(), matchId, game, loser, winner, loser);
        when(matchReportRepository.findByGame_IdAndMatchId(gameId, matchId)).thenReturn(List.of(firstReport, secondReport));

        when(playerStatsRepository.findByGameIdAndPlayerId(gameId, winnerId)).thenReturn(Optional.empty());
        when(playerStatsRepository.findByGameIdAndPlayerId(gameId, loserId)).thenReturn(Optional.empty());
        when(playerStatsRepository.save(any(PlayerStats.class))).thenAnswer(inv -> inv.getArgument(0));

        MatchReportResponse response = leaderboardService.reportMatchResult(
                gameId, loserId, new MatchResultRequest(matchId, winnerId, loserId));

        assertThat(response.status()).isEqualTo("completed");

        ArgumentCaptor<PlayerStats> statsCaptor = ArgumentCaptor.forClass(PlayerStats.class);
        verify(playerStatsRepository, times(2)).save(statsCaptor.capture());
        List<PlayerStats> savedStats = statsCaptor.getAllValues();

        PlayerStats winnerStats = savedStats.stream()
                .filter(s -> s.getPlayer().getId().equals(winnerId))
                .findFirst().orElseThrow();
        PlayerStats loserStats = savedStats.stream()
                .filter(s -> s.getPlayer().getId().equals(loserId))
                .findFirst().orElseThrow();

        assertThat(winnerStats.getWins()).isEqualTo(1);
        assertThat(winnerStats.getLosses()).isEqualTo(0);
        assertThat(loserStats.getWins()).isEqualTo(0);
        assertThat(loserStats.getLosses()).isEqualTo(1);
        verify(completedMatchRepository, times(1)).save(any(CompletedMatch.class));
        verify(matchReportRepository, times(1)).deleteByGame_IdAndMatchId(gameId, matchId);
    }

    @Test
    void reportMatchResult_throwsDuplicate_whenSameReporterReportsAgain() {
        when(completedMatchRepository.existsByGame_IdAndMatchId(gameId, matchId)).thenReturn(false);
        when(matchReportRepository.existsByGame_IdAndMatchIdAndReporter_Id(gameId, matchId, winnerId)).thenReturn(true);

        assertThatThrownBy(() -> leaderboardService.reportMatchResult(
                gameId, winnerId, new MatchResultRequest(matchId, winnerId, loserId)))
                .isInstanceOf(ForgeApiException.class)
                .extracting(ex -> ((ForgeApiException) ex).getErrorCode())
                .isEqualTo(ForgeErrorCode.LEADERBOARD_DUPLICATE_REPORT);

        verify(matchReportRepository, never()).save(any());
    }

    @Test
    void reportMatchResult_throwsDuplicate_whenMatchAlreadyFinalized() {
        when(completedMatchRepository.existsByGame_IdAndMatchId(gameId, matchId)).thenReturn(true);

        assertThatThrownBy(() -> leaderboardService.reportMatchResult(
                gameId, winnerId, new MatchResultRequest(matchId, winnerId, loserId)))
                .isInstanceOf(ForgeApiException.class)
                .extracting(ex -> ((ForgeApiException) ex).getErrorCode())
                .isEqualTo(ForgeErrorCode.LEADERBOARD_DUPLICATE_REPORT);

        verify(matchReportRepository, never()).save(any());
        verify(playerStatsRepository, never()).save(any());
    }

    @Test
    void reportMatchResult_throwsInvalidResult_whenWinnerEqualsLoser() {
        assertThatThrownBy(() -> leaderboardService.reportMatchResult(
                gameId, winnerId, new MatchResultRequest(matchId, winnerId, winnerId)))
                .isInstanceOf(ForgeApiException.class)
                .extracting(ex -> ((ForgeApiException) ex).getErrorCode())
                .isEqualTo(ForgeErrorCode.LEADERBOARD_INVALID_RESULT);

        verify(matchReportRepository, never()).save(any());
    }

    @Test
    void reportMatchResult_throwsInvalidResult_whenReporterNotInMatch() {
        UUID outsiderId = UUID.randomUUID();
        Player outsider = new Player(outsiderId, game, "steam", "steamOutsider", "Outsider");
        when(playerRepository.findById(outsiderId)).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> leaderboardService.reportMatchResult(
                gameId, outsiderId, new MatchResultRequest(matchId, winnerId, loserId)))
                .isInstanceOf(ForgeApiException.class)
                .extracting(ex -> ((ForgeApiException) ex).getErrorCode())
                .isEqualTo(ForgeErrorCode.LEADERBOARD_INVALID_RESULT);

        verify(matchReportRepository, never()).save(any());
    }
}
