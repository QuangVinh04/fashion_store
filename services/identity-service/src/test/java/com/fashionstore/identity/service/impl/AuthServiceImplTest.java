package com.fashionstore.identity.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.identity.config.ErrorCode;
import com.fashionstore.identity.constant.PredefinedRole;
import com.fashionstore.identity.dto.AuthResponse;
import com.fashionstore.identity.dto.LoginRequest;
import com.fashionstore.identity.dto.RegisterRequest;
import com.fashionstore.identity.entity.Role;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.entity.VerificationToken;
import com.fashionstore.identity.repository.RoleRepository;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.repository.VerificationTokenRepository;
import com.fashionstore.identity.service.EmailService;
import com.fashionstore.identity.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private VerificationTokenRepository verificationTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void registerPersistsUserAndVerificationTokenBeforeRequestingEmail() {
        RegisterRequest request = RegisterRequest.builder()
                .email("customer@example.com")
                .password("secret123")
                .fullName("Customer")
                .phone("0900000000")
                .build();
        Role userRole = Role.builder().name(PredefinedRole.USER_ROLE).build();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(roleRepository.findByName(PredefinedRole.USER_ROLE)).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals(request.getEmail(), savedUser.getEmail());
        assertEquals("encoded-password", savedUser.getPassword());
        assertEquals(Set.of(userRole), savedUser.getRoles());
        assertFalse(savedUser.getIsEmailVerified());

        ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(verificationTokenRepository).save(tokenCaptor.capture());
        VerificationToken savedToken = tokenCaptor.getValue();
        assertEquals(savedUser, savedToken.getUser());
        assertFalse(savedToken.getUsed());
        assertTrue(savedToken.getExpiresAt().isAfter(LocalDateTime.now().plusHours(23)));
        verify(emailService).sendVerificationEmail(
                request.getEmail(),
                request.getFullName(),
                savedToken.getToken()
        );
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = RegisterRequest.builder()
                .email("customer@example.com")
                .password("secret123")
                .fullName("Customer")
                .build();
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        AppException exception = assertThrows(AppException.class, () -> authService.register(request));

        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, exception.getErrorCode());
        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(any(), any(), any());
    }

    @Test
    void verifyEmailMarksUserAndTokenAsVerified() {
        User user = User.builder()
                .email("customer@example.com")
                .password("encoded-password")
                .fullName("Customer")
                .isEmailVerified(false)
                .build();
        VerificationToken token = VerificationToken.builder()
                .token("123456")
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        when(verificationTokenRepository.findByToken("123456")).thenReturn(Optional.of(token));

        authService.verifyEmail("123456");

        assertTrue(user.getIsEmailVerified());
        assertTrue(token.getUsed());
        verify(userRepository).save(user);
        verify(verificationTokenRepository).save(token);
    }

    @Test
    void verifyEmailRejectsExpiredToken() {
        VerificationToken token = VerificationToken.builder()
                .token("123456")
                .user(User.builder().build())
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(verificationTokenRepository.findByToken("123456")).thenReturn(Optional.of(token));

        AppException exception = assertThrows(AppException.class, () -> authService.verifyEmail("123456"));

        assertEquals(ErrorCode.VERIFICATION_TOKEN_EXPIRED, exception.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginRejectsUnverifiedEmailAfterCredentialValidation() {
        LoginRequest request = LoginRequest.builder()
                .email("customer@example.com")
                .password("secret123")
                .build();
        User user = User.builder()
                .email(request.getEmail())
                .password("encoded-password")
                .fullName("Customer")
                .isEmailVerified(false)
                .build();
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));

        AppException exception = assertThrows(AppException.class, () -> authService.login(request));

        assertEquals(ErrorCode.EMAIL_NOT_VERIFIED, exception.getErrorCode());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void loginReturnsTokenAndRoleSnapshot() {
        LoginRequest request = LoginRequest.builder()
                .email("customer@example.com")
                .password("secret123")
                .build();
        Role role = Role.builder().name(PredefinedRole.USER_ROLE).build();
        User user = User.builder()
                .email(request.getEmail())
                .password("encoded-password")
                .fullName("Customer")
                .isEmailVerified(true)
                .roles(Set.of(role))
                .build();
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");

        AuthResponse response = authService.login(request);

        assertEquals("access-token", response.getAccessToken());
        assertEquals(request.getEmail(), response.getEmail());
        assertEquals(Set.of(PredefinedRole.USER_ROLE), response.getRoles());
    }
}
