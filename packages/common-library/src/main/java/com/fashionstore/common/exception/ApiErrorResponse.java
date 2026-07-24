package com.fashionstore.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
        int code,
        int status,
        String message,
        String path,
        String correlationId,
        Instant timestamp,
        Map<String, String> fieldErrors
) {
}
