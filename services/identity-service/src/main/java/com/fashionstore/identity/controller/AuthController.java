package com.fashionstore.identity.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.identity.dto.auth.AuthResponse;
import com.fashionstore.identity.dto.auth.AuthResult;
import com.fashionstore.identity.dto.auth.LoginRequest;
import com.fashionstore.identity.dto.auth.LogoutRequest;
import com.fashionstore.identity.dto.auth.RefreshTokenRequest;
import com.fashionstore.identity.dto.auth.RegisterRequest;
import com.fashionstore.identity.dto.auth.VerifyEmailRequest;
import com.fashionstore.identity.service.AuthService;
import com.fashionstore.identity.util.CookieUtils;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthController {

    AuthService authService;

    @Value("${security.jwt.refresh-token-ttl-days:7}")
    long refreshTokenTtlDays;

        @Value("${security.cookie-secure:false}")
        boolean secureCookies;

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.<Void>builder()
                        .message("Đăng ký thành công")
                        .build();
    }
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
                        HttpServletRequest httpRequest,
                        @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor) {
                String clientIp = forwardedFor == null || forwardedFor.isBlank()
                                ? httpRequest.getRemoteAddr()
                                : forwardedFor.split(",", 2)[0].trim();
                AuthResult result = authService.login(request, clientIp);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        CookieUtils.createRefreshTokenCookie(result.refreshToken(), refreshTokenTtlDays, secureCookies).toString())
                .body(ApiResponse.<AuthResponse>builder()
                        .message("Đăng nhập thành công")
                        .data(result.response())
                        .build());
    }

    @PostMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ApiResponse.<Void>builder()
                .message("Xác nhận email thành công, bạn có thể đăng nhập")
                .build();
    }
    @PostMapping("/resend-verification")
    public ApiResponse<Void> resendVerification(@RequestParam String email) {
        authService.resendVerification(email);
        return ApiResponse.<Void>builder()
                .message("Email xác nhận đã được gửi lại")
                .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody(required = false) RefreshTokenRequest body,
            @CookieValue(name = "refreshToken", required = false) String cookieToken) {
        String token = body != null ? body.refreshToken() : null;
        if (token == null || token.isBlank()) {
            token = cookieToken;
        }
        AuthResult result = authService.refresh(token);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        CookieUtils.createRefreshTokenCookie(result.refreshToken(), refreshTokenTtlDays, secureCookies).toString())
                .body(ApiResponse.<AuthResponse>builder()
                        .message("Làm mới token thành công")
                        .data(result.response())
                        .build());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody(required = false) LogoutRequest body,
            @CookieValue(name = "refreshToken", required = false) String cookieToken,
            @AuthenticationPrincipal Jwt jwt) {
        String token = body != null ? body.refreshToken() : null;
        if (token == null || token.isBlank()) {
            token = cookieToken;
        }
        authService.logout(token, jwt);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, CookieUtils.deleteRefreshTokenCookie(secureCookies).toString())
                .body(ApiResponse.<Void>builder()
                        .message("Đăng xuất thành công")
                        .build());
    }
}
