-- Forge Backend — initial schema (mirrors repo root db/001)

CREATE TABLE games (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(255) NOT NULL,
    api_key_hash       VARCHAR(255),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE games IS 'Registered games on the service (tenant boundary for players and API keys).';
COMMENT ON COLUMN games.name IS 'Display name for the game (not guaranteed unique across studios).';
COMMENT ON COLUMN games.api_key_hash IS 'Server-side hash of the client Forge API key (BCrypt).';

CREATE TABLE players (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id              UUID NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    platform             VARCHAR(32) NOT NULL,
    platform_user_id     VARCHAR(64) NOT NULL,
    display_name         VARCHAR(255),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_players_game_platform_account UNIQUE (game_id, platform, platform_user_id)
);

COMMENT ON TABLE players IS 'Player identities scoped per game; used for auth and future session binding.';
COMMENT ON COLUMN players.platform IS 'Identity provider, e.g. steam.';
COMMENT ON COLUMN players.platform_user_id IS 'Stable id from the platform (e.g. SteamID64).';
COMMENT ON COLUMN players.display_name IS 'Optional UI label; not used for authentication.';

CREATE INDEX idx_players_game_id ON players (game_id);
