CREATE TABLE completed_matches (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id       UUID NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    match_id      UUID NOT NULL,
    winner_id     UUID NOT NULL REFERENCES players (id) ON DELETE RESTRICT,
    loser_id      UUID NOT NULL REFERENCES players (id) ON DELETE RESTRICT,
    completed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_completed_matches_game_match UNIQUE (game_id, match_id)
);

CREATE INDEX idx_completed_matches_game_id ON completed_matches (game_id);

COMMENT ON TABLE completed_matches IS 'Finalized matches tombstone table used to enforce idempotent scoring and prevent replay.';
COMMENT ON COLUMN completed_matches.match_id IS 'Client-provided match identifier scoped by game_id.';
COMMENT ON COLUMN completed_matches.winner_id IS 'Winner resolved by reconciliation when the match was finalized.';
COMMENT ON COLUMN completed_matches.loser_id IS 'Loser resolved by reconciliation when the match was finalized.';
COMMENT ON COLUMN completed_matches.completed_at IS 'Timestamp when leaderboard counters were updated for this match.';
