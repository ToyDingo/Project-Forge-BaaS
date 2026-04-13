-- Mirrors repo root db/002

ALTER TABLE games
    ADD COLUMN api_key_lookup_hash VARCHAR(64),
    ADD COLUMN steam_app_id BIGINT,
    ADD COLUMN steam_web_api_key TEXT;

COMMENT ON COLUMN games.api_key_lookup_hash IS 'SHA-256 (hex, lowercase) of raw Forge API key for indexed lookup before BCrypt verification.';
COMMENT ON COLUMN games.steam_app_id IS 'Steamworks App ID for this title; required for Steam ticket validation.';
COMMENT ON COLUMN games.steam_web_api_key IS 'Steam Web API key for this game (publisher); keep out of logs; encrypt at rest in production.';

CREATE UNIQUE INDEX uq_games_api_key_lookup_hash ON games (api_key_lookup_hash)
    WHERE api_key_lookup_hash IS NOT NULL;
