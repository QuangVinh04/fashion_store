package com.fashionstore.identity.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.redis.RedisService;
import com.fashionstore.identity.config.ErrorCode;
import com.fashionstore.identity.repository.RoleRepository;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.service.EmailService;
import com.fashionstore.identity.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplLogoutTest {

    @Mock
    UserRepository userRepository;
    @Mock
    RoleRepository roleRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    AuthenticationManager authenticationManager;
    @Mock
    JwtService jwtService;
    @Mock
    EmailService emailService;
    @Mock
    RedisService redisService;
    @Mock
    JwtDecoder jwtDecoder;

    AuthServiceImpl authService;

    private static final String REFRESH = "refresh-token-value";
    private static final String REFRESH_HASH = "e65009f6e0ae9fc204adcb73a208f63c582b7839bde7fe836da36a3df6ae7a85";

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(7, userRepository, roleRepository,
                passwordEncoder, authenticationManager, jwtService, emailService, redisService, jwtDecoder);
    }

    @Test
    void logoutDeletesRefreshTokenAndBlacklistsAccessJti() {
        Instant expiresAt = Instant.now().plusSeconds(600);
        Jwt jwt = new Jwt("access-token-value", Instant.now(), expiresAt,
                Map.of("alg", "RS256"), Map.of("sub", "user-1", "jti", "jti-123"));

        authService.logout(REFRESH, jwt);

        verify(redisService).deleteValue(eq("auth:refresh:" + REFRESH_HASH));
        verify(redisService).setUntil(eq("auth:blacklist:jti-123"), eq("1"),
                eq(expiresAt.toEpochMilli()));
    }

    @Test
    void logoutWithoutRefreshTokenOnlyBlacklistsAccessJti() {
        Instant expiresAt = Instant.now().plusSeconds(600);
        Jwt jwt = new Jwt("access-token-value", Instant.now(), expiresAt,
                Map.of("alg", "RS256"), Map.of("sub", "user-1", "jti", "jti-123"));

        authService.logout(null, jwt);

        verify(redisService, never()).deleteValue(anyString());
        verify(redisService).setUntil(eq("auth:blacklist:jti-123"), eq("1"),
                eq(expiresAt.toEpochMilli()));
    }

    @Test
    void logoutWithBlankRefreshTokenIgnoresIt() {
        Instant expiresAt = Instant.now().plusSeconds(600);
        Jwt jwt = new Jwt("access-token-value", Instant.now(), expiresAt,
                Map.of("alg", "RS256"), Map.of("sub", "user-1", "jti", "jti-123"));

        authService.logout("   ", jwt);

        verify(redisService, never()).deleteValue(anyString());
        verify(redisService).setUntil(eq("auth:blacklist:jti-123"), eq("1"),
                eq(expiresAt.toEpochMilli()));
    }
}
