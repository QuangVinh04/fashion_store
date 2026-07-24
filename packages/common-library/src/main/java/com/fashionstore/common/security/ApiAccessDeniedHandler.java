package com.fashionstore.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.exception.ApiErrorResponse;
import com.fashionstore.common.exception.ApiErrorResponseFactory;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.web.CorrelationIdFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ApiAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException, ServletException {
        ApiErrorResponse body = ApiErrorResponseFactory.create(
                ErrorCode.ACCESS_DENIED,
                request,
                null
        );
        response.setStatus(ErrorCode.ACCESS_DENIED.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        if (body.correlationId() != null) {
            response.setHeader(CorrelationIdFilter.HEADER, body.correlationId());
        }
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
