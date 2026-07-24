package com.fashionstore.common.exception;

import com.fashionstore.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;

public final class ApiErrorResponseFactory {

    private ApiErrorResponseFactory() {
    }

    public static ApiErrorResponse create(
            BaseErrorCode error,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        return new ApiErrorResponse(
                error.getCode(),
                error.getStatusCode().value(),
                error.getMessage(),
                request.getRequestURI(),
                correlationId(request),
                Instant.now(),
                fieldErrors
        );
    }

    public static String correlationId(HttpServletRequest request) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader(CorrelationIdFilter.HEADER);
        }
        return correlationId;
    }
}
