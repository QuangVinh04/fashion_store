package com.fashionstore.identity.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.redis.RedisService;
import com.fashionstore.identity.entity.User;
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
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplRefreshTest {

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

    private static final String RAW_REFRESH = "raw-refresh-token";
    private static final String USER_ID = "user-1";

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, roleRepository,
                passwordEncoder, authenticationManager, jwtService, emailService, redisService, jwtDecoder);
        ReflectionTestUtils.setField(authService, "refreshTokenTtlDays", 7L);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Jwt jwtWithType(String tokenType, String subject) {
        return new Jwt(RAW_REFRESH, Instant.now(), Instant.now().plusSeconds(600),
                Map.of("alg", "RS256"),
                Map.of("sub", subject, "token_type", tokenType));
    }

    @Test
    void refreshRotatesTokenAndStoresHashedKeys() {
        User user = new User();
        user.setId(USER_ID);
        user.setIsActive(true);
        when(jwtDecoder.decode(RAW_REFRESH)).thenReturn(jwtWithType("REFRESH_TOKEN", USER_ID));
        when(redisService.getValue("auth:refresh:" + sha256Hex(RAW_REFRESH), String.class)).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("new-access");
        when(jwtService.generateRefreshToken(user)).thenReturn("new-refresh");

        authService.refresh(RAW_REFRESH);

        // token cũ bị thu hồi
        verify(redisService).deleteValue("auth:refresh:" + sha256Hex(RAW_REFRESH));
        // token mới lưu bằng hash + index per-user
        verify(redisService).setWithTTL("auth:refresh:" + sha256Hex("new-refresh"),
                USER_ID, 7L, TimeUnit.DAYS);
        verify(redisService).setWithTTL("auth:user-refresh:" + USER_ID,
                sha256Hex("new-refresh"), 7L, TimeUnit.DAYS);
    }

    @Test
    void refreshRejectsTokenThatFailsSignatureVerification() {
        when(jwtDecoder.decode(RAW_REFRESH)).thenThrow(new JwtException("bad signature"));

        assertThrows(AppException.class, () -> authService.refresh(RAW_REFRESH));

        verify(redisService, never()).getValue(any(), any());
    }

    @Test
    void refreshRejectsAccessTokenPresentedAsRefreshToken() {
        when(jwtDecoder.decode(RAW_REFRESH)).thenReturn(jwtWithType("ACCESS_TOKEN", USER_ID));

        assertThrows(AppException.class, () -> authService.refresh(RAW_REFRESH));

        verify(redisService, never()).getValue(any(), any());
    }

    @Test
    void refreshDetectsReuseAndRevokesSession() {
        String currentHash = sha256Hex("current-refresh-token");
        when(jwtDecoder.decode(RAW_REFRESH)).thenReturn(jwtWithType("REFRESH_TOKEN", USER_ID));
        when(redisService.getValue("auth:refresh:" + sha256Hex(RAW_REFRESH), String.class)).thenReturn(null);
        when(redisService.getValue("auth:user-refresh:" + USER_ID, String.class)).thenReturn(currentHash);

        assertThrows(AppException.class, () -> authService.refresh(RAW_REFRESH));

        verify(redisService).deleteValue("auth:refresh:" + currentHash);
        verify(redisService).deleteValue("auth:user-refresh:" + USER_ID);
        verify(redisService, never()).setWithTTL(any(), any(), anyLong(), any());
    }

    @Test
    void refreshRejectsUnknownTokenWithoutSessionToRevoke() {
        when(jwtDecoder.decode(RAW_REFRESH)).thenReturn(jwtWithType("REFRESH_TOKEN", USER_ID));
        when(redisService.getValue("auth:refresh:" + sha256Hex(RAW_REFRESH), String.class)).thenReturn(null);
        when(redisService.getValue("auth:user-refresh:" + USER_ID, String.class)).thenReturn(null);

        assertThrows(AppException.class, () -> authService.refresh(RAW_REFRESH));

        verify(redisService, never()).deleteValue(any());
    }

    @Test
    void refreshRejectsBlankToken() {
        assertThrows(AppException.class, () -> authService.refresh("  "));
        verify(jwtDecoder, never()).decode(any());
    }

    @Test
    void refreshRejectsWhenStoredUserIdDoesNotMatchSubject() {
        when(jwtDecoder.decode(RAW_REFRESH)).thenReturn(jwtWithType("REFRESH_TOKEN", USER_ID));
        when(redisService.getValue("auth:refresh:" + sha256Hex(RAW_REFRESH), String.class))
                .thenReturn("another-user");

        assertThrows(AppException.class, () -> authService.refresh(RAW_REFRESH));

        verify(jwtService, never()).generateAccessToken(any());
        verify(userRepository, never()).findById(eq("another-user"));
    }
}
