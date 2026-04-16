-- Mirrors repo root db/003

CREATE TABLE match_reports (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id           UUID NOT NULL,
    game_id            UUID NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    reporter_id        UUID NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    reported_winner_id UUID NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    reported_loser_id  UUID NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_match_reports_match_reporter UNIQUE (match_id, reporter_id)
);

CREATE INDEX idx_match_reports_match_id ON match_reports (match_id);
CREATE INDEX idx_match_reports_game_id ON match_reports (game_id);

COMMENT ON TABLE match_reports IS 'Per-client game result reports; two reports per match_id (one from each player).';
COMMENT ON COLUMN match_reports.match_id IS 'Opaque match identifier shared by both participants; used to correlate report pairs.';
COMMENT ON COLUMN match_reports.game_id IS 'Game this match belongs to (tenant boundary).';
COMMENT ON COLUMN match_reports.reporter_id IS 'Player who submitted this report.';
COMMENT ON COLUMN match_reports.reported_winner_id IS 'Player the reporter claims won the match.';
COMMENT ON COLUMN match_reports.reported_loser_id IS 'Player the reporter claims lost the match.';

CREATE TABLE player_stats (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id            UUID NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    player_id          UUID NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    wins               INTEGER NOT NULL DEFAULT 0 CHECK (wins >= 0),
    losses             INTEGER NOT NULL DEFAULT 0 CHECK (losses >= 0),
    last_win_at        TIMESTAMPTZ,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_player_stats_game_player UNIQUE (game_id, player_id)
);

CREATE INDEX idx_player_stats_ranking ON player_stats (game_id, wins DESC, losses ASC, last_win_at ASC NULLS LAST, player_id ASC);

COMMENT ON TABLE player_stats IS 'Aggregated win/loss record per player per game; drives leaderboard ranking.';
COMMENT ON COLUMN player_stats.wins IS 'Total confirmed wins for this player in this game.';
COMMENT ON COLUMN player_stats.losses IS 'Total confirmed losses for this player in this game.';
COMMENT ON COLUMN player_stats.last_win_at IS 'Timestamp of the most recent win; used as tiebreaker in ranking (earlier wins rank higher).';
COMMENT ON COLUMN player_stats.updated_at IS 'Last time this row was modified (win or loss recorded).';
