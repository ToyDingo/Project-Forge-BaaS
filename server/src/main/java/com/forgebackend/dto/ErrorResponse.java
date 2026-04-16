package com.forgebackend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** JSON envelope for error responses. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(String code, String message) {
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ErrorBody(code, message));
    }
}
