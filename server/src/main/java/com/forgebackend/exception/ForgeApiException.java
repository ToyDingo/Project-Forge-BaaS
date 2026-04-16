package com.forgebackend.exception;

/**
 * Business or infrastructure failure mapped to HTTP error responses by {@link com.forgebackend.exception.GlobalExceptionHandler}.
 */
public class ForgeApiException extends RuntimeException {

    private final ForgeErrorCode errorCode;

    public ForgeApiException(ForgeErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public ForgeApiException(ForgeErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ForgeErrorCode getErrorCode() {
        return errorCode;
    }
}
