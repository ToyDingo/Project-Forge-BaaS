package com.forgebackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Response after a match result is reported. */
public record MatchReportResponse(
        @JsonProperty("status") String status,
        @JsonProperty("message") String message
) {}
