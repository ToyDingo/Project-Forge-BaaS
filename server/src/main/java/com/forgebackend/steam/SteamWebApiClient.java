package com.forgebackend.steam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebackend.config.ForgeSteamProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Production implementation of {@link SteamClient} using the Steam Web HTTP API.
 * <p>
 * Not loaded when {@code forge.steam.dev-stub-enabled=true} (see {@link DevOnlySteamClientStub}).
 */
@Component
@ConditionalOnProperty(prefix = "forge.steam", name = "dev-stub-enabled", havingValue = "false", matchIfMissing = true)
public class SteamWebApiClient implements SteamClient {

    private static final String AUTH_TICKET_PATH = "/ISteamUserAuth/AuthenticateUserTicket/v1/";

    private final RestTemplate steamRestTemplate;
    private final ForgeSteamProperties steamProperties;
    private final ObjectMapper objectMapper;

    public SteamWebApiClient(
            RestTemplate steamRestTemplate,
            ForgeSteamProperties steamProperties,
            ObjectMapper objectMapper) {
        this.steamRestTemplate = steamRestTemplate;
        this.steamProperties = steamProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SteamTicketValidationResult validateTicket(long steamAppId, String steamWebApiKey, String steamTicketHex) {
        String url = UriComponentsBuilder
                .fromHttpUrl(steamProperties.baseUrl() + AUTH_TICKET_PATH)
                .queryParam("key", steamWebApiKey)
                .queryParam("appid", steamAppId)
                .queryParam("ticket", steamTicketHex)
                .encode()
                .toUriString();
        try {
            String json = steamRestTemplate.getForObject(url, String.class);
            return parseAuthenticateUserTicketResponse(json);
        } catch (RestClientResponseException ex) {
            HttpStatusCode status = ex.getStatusCode();
            if (status.is5xxServerError()) {
                return new SteamTicketValidationResult.SteamTransportFailure(
                        "Steam HTTP " + status.value(), ex);
            }
            return new SteamTicketValidationResult.InvalidSteamTicket(
                    "Steam HTTP " + status.value() + ": " + ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            return new SteamTicketValidationResult.SteamTransportFailure(
                    "Forge could not connect to Steam: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            return new SteamTicketValidationResult.SteamTransportFailure(ex.getMessage(), ex);
        }
    }

    private SteamTicketValidationResult parseAuthenticateUserTicketResponse(String json) {
        if (json == null || json.isBlank()) {
            return new SteamTicketValidationResult.InvalidSteamTicket("Empty Steam response");
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode params = root.path("response").path("params");
            if (params.isMissingNode() || params.isEmpty()) {
                JsonNode response = root.path("response");
                if (response.has("error")) {
                    return new SteamTicketValidationResult.InvalidSteamTicket(response.get("error").asText());
                }
                return new SteamTicketValidationResult.InvalidSteamTicket("Unexpected Steam response shape");
            }
            String result = textOrEmpty(params, "result");
            if (!"OK".equalsIgnoreCase(result)) {
                return new SteamTicketValidationResult.InvalidSteamTicket("Steam result: " + result);
            }
            String steamId = textOrEmpty(params, "steamid");
            if (steamId.isEmpty()) {
                return new SteamTicketValidationResult.InvalidSteamTicket("Missing steamid in Steam response");
            }
            return new SteamTicketValidationResult.ValidSteamIdentity(steamId);
        } catch (Exception ex) {
            return new SteamTicketValidationResult.InvalidSteamTicket("Failed to parse Steam response: " + ex.getMessage());
        }
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText();
    }
}
