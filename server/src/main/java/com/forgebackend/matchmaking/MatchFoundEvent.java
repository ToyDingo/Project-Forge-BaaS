package com.forgebackend.matchmaking;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Payload for the {@code match_found} WebSocket event pushed to each participant after a
 * match is formed. The field shape is part of the public client contract.
 * <p>
 * Delivery is at-least-once at MVP; clients must deduplicate by {@code match_id}.
 *
 * @param matchId     Server-generated match identifier, opaque to the client.
 * @param mode        Game mode that produced this match.
 * @param players     Participants in the match (MVP is exactly two for 1v1).
 * @param createdAt   Moment the match row was created on the server.
 * @param expiresAt   Moment after which the match will be abandoned if not yet ready.
 * @param connectHint Optional opaque string the client can use to reach the game session; reserved for future use.
 */
public record MatchFoundEvent(
        @JsonProperty("event_type") String eventType,
        @JsonProperty("match_id") UUID matchId,
        @JsonProperty("mode") String mode,
        @JsonProperty("players") List<Participant> players,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("expires_at") Instant expiresAt,
        @JsonProperty("connect_hint") String connectHint
) {

    public static final String EVENT_TYPE = "match_found";

    /** Minimal participant shape included in the match-found payload. */
    public record Participant(@JsonProperty("player_id") UUID playerId) {
    }

    /**
     * Convenience factory that fills in the stable {@code event_type} string so callers
     * never need to repeat the literal.
     */
    public static MatchFoundEvent of(
            UUID matchId,
            String mode,
            List<Participant> players,
            Instant createdAt,
            Instant expiresAt,
            String connectHint) {
        return new MatchFoundEvent(EVENT_TYPE, matchId, mode, players, createdAt, expiresAt, connectHint);
    }
}
