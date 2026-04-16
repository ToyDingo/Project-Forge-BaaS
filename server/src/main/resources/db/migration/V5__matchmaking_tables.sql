-- Matchmaking vertical slice: queue entries, formed matches, and per-player delivery tracking.

CREATE TABLE matchmaking_queue_entries (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id                  UUID NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    player_id                UUID NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    mode                     VARCHAR(64) NOT NULL,
    client_version           VARCHAR(64) NOT NULL,
    platform                 VARCHAR(32) NOT NULL,
    region                   VARCHAR(64),
    latency_by_region_json   TEXT,
    status                   VARCHAR(32) NOT NULL,
    joined_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    timeout_at               TIMESTAMPTZ NOT NULL,
    last_heartbeat_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_matchmaking_queue_entries_game_player ON matchmaking_queue_entries (game_id, player_id);
CREATE INDEX idx_matchmaking_queue_entries_game_mode_status ON matchmaking_queue_entries (game_id, mode, status);
CREATE INDEX idx_matchmaking_queue_entries_timeout_at ON matchmaking_queue_entries (timeout_at);
CREATE INDEX idx_matchmaking_queue_entries_heartbeat ON matchmaking_queue_entries (last_heartbeat_at);

CREATE UNIQUE INDEX uq_matchmaking_queue_entries_active_per_player_mode
    ON matchmaking_queue_entries (game_id, player_id, mode)
    WHERE status IN ('queued', 'matched');

COMMENT ON TABLE matchmaking_queue_entries IS 'Per-player queue tickets; one active row per (game, player, mode) at a time.';
COMMENT ON COLUMN matchmaking_queue_entries.mode IS 'Game mode identifier, e.g. ranked_1v1. Opaque to the backend beyond equality matching.';
COMMENT ON COLUMN matchmaking_queue_entries.client_version IS 'Client build version reported at join time; used for compatibility matching.';
COMMENT ON COLUMN matchmaking_queue_entries.platform IS 'Identity platform the player authenticated with, e.g. steam.';
COMMENT ON COLUMN matchmaking_queue_entries.region IS 'Preferred region for the player at join time.';
COMMENT ON COLUMN matchmaking_queue_entries.latency_by_region_json IS 'Optional map of region code to measured latency in milliseconds.';
COMMENT ON COLUMN matchmaking_queue_entries.status IS 'Lifecycle: queued, matched, timed_out, left_queue, stale_removed.';
COMMENT ON COLUMN matchmaking_queue_entries.timeout_at IS 'Moment at which the queue ticket will expire if no match is found.';
COMMENT ON COLUMN matchmaking_queue_entries.last_heartbeat_at IS 'Most recent heartbeat received from the client for this queue ticket.';

CREATE TABLE matchmaking_matches (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id      UUID NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    mode         VARCHAR(64) NOT NULL,
    status       VARCHAR(32) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_matchmaking_matches_game_status ON matchmaking_matches (game_id, status);

COMMENT ON TABLE matchmaking_matches IS 'Formed matches awaiting or completing delivery to participants.';
COMMENT ON COLUMN matchmaking_matches.status IS 'Lifecycle: pending_notify, ready, cancelled.';
COMMENT ON COLUMN matchmaking_matches.expires_at IS 'Deadline after which a match that has not reached ready is abandoned.';

CREATE TABLE matchmaking_match_players (
    match_id          UUID NOT NULL REFERENCES matchmaking_matches (id) ON DELETE CASCADE,
    player_id         UUID NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    delivery_status   VARCHAR(32) NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (match_id, player_id)
);

CREATE INDEX idx_matchmaking_match_players_player ON matchmaking_match_players (player_id);

COMMENT ON TABLE matchmaking_match_players IS 'Per-player delivery tracking for a formed match; used by the notification retry loop.';
COMMENT ON COLUMN matchmaking_match_players.delivery_status IS 'Notification state: pending, delivered, failed.';
COMMENT ON COLUMN matchmaking_match_players.updated_at IS 'Last time the delivery status changed for this participant.';
