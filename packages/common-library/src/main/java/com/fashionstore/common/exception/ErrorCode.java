package com.fashionstore.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public enum ErrorCode implements BaseErrorCode {
    UNAUTHENTICATED(2005, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(2006, "Unauthorized", HttpStatus.FORBIDDEN),
    FORBIDDEN(2007, "Forbidden", HttpStatus.FORBIDDEN),
    VALIDATION_FAILED(9001, "Validation failed", HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(9002, "Malformed request", HttpStatus.BAD_REQUEST),
    ACCESS_DENIED(9003, "Access denied", HttpStatus.FORBIDDEN),
    METHOD_NOT_ALLOWED(9004, "Method not allowed", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(9005, "Unsupported media type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    UPSTREAM_SERVICE_ERROR(9006, "Upstream service request failed", HttpStatus.BAD_GATEWAY),
    RESOURCE_NOT_FOUND(9007, "Resource not found", HttpStatus.NOT_FOUND),
    INTERNAL_ERROR(9999, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public HttpStatusCode getStatusCode() {
        return statusCode;
    }
}
