package com.fashionstore.identity.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Slf4j
public class InternalTokenAuthFilter extends OncePerRequestFilter {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private final String expectedSecretToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InternalTokenAuthFilter(String expectedSecretToken) {
        this.expectedSecretToken = expectedSecretToken;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/internal/")) {
            String token = request.getHeader(INTERNAL_TOKEN_HEADER);
            if (expectedSecretToken == null || expectedSecretToken.isBlank() || !expectedSecretToken.equals(token)) {
                log.warn("[InternalAuth] Unauthorized access to {} with invalid or missing token", uri);
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                Map<String, Object> errorBody = Map.of(
                        "code", 403,
                        "message", "Access denied: Invalid or missing internal service token"
                );
                response.getWriter().write(objectMapper.writeValueAsString(errorBody));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
