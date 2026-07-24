package com.fashionstore.common.security;

import com.fashionstore.common.exception.AppException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentUserProviderTest {

    private final CurrentUserProvider provider = new CurrentUserProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsUserIdClaim() {
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "none"),
                Map.of("sub", "subject-1", "userId", "user-1")
        );
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertEquals("user-1", provider.getCurrentUserId());
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThrows(AppException.class, provider::getCurrentUserId);
    }
}
