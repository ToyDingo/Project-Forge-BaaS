-- Manual helper script (not a Flyway migration).
-- Safe to run multiple times: inserts only when the key is not already present.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO games (
    id,
    name,
    api_key_hash,
    api_key_lookup_hash,
    steam_app_id,
    steam_web_api_key,
    created_at
)
SELECT
    gen_random_uuid(),
    'Dev Game',
    crypt('forge-test-harness-key', gen_salt('bf', 10)),            -- BCrypt
    encode(digest('forge-test-harness-key', 'sha256'), 'hex'),      -- SHA-256 lowercase hex
    480,
    'dev-steam-web-api-key',
    now()
WHERE NOT EXISTS (
    SELECT 1
    FROM games
    WHERE api_key_lookup_hash = encode(digest('forge-test-harness-key', 'sha256'), 'hex')
);
