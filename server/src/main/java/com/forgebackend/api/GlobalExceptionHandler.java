package com.forgebackend.api;

import com.forgebackend.exception.ForgeApiException;
import com.forgebackend.exception.ForgeErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain exceptions and validation failures to stable JSON error bodies.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ForgeApiException.class)
    public ResponseEntity<ErrorResponse> handleForgeApi(ForgeApiException ex) {
        ForgeErrorCode code = ex.getErrorCode();
        ErrorResponse body = ErrorResponse.of(code.name(), ex.getMessage());
        return ResponseEntity.status(code.httpStatus()).body(body);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        ForgeErrorCode code = ForgeErrorCode.FORGE_INVALID_REQUEST;
        String message = ex.getMessage() != null ? ex.getMessage() : code.defaultMessage();
        ErrorResponse body = ErrorResponse.of(code.name(), message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
