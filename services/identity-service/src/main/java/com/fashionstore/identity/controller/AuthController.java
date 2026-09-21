package com.fashionstore.identity.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.identity.client.OrderServiceClient;
import com.fashionstore.identity.dto.auth.AuthResponse;
import com.fashionstore.identity.dto.auth.AuthResult;
import com.fashionstore.identity.dto.auth.LoginRequest;
import com.fashionstore.identity.dto.auth.LogoutRequest;
import com.fashionstore.identity.dto.auth.RefreshTokenRequest;
import com.fashionstore.identity.dto.auth.RegisterRequest;
import com.fashionstore.identity.dto.auth.VerifyEmailRequest;
import com.fashionstore.identity.service.AuthService;
import com.fashionstore.identity.util.CookieUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Đăng ký, đăng nhập, xác minh email, refresh và logout")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthController {

    AuthService authService;
    OrderServiceClient orderServiceClient;

    @NonFinal
    @Value("${security.jwt.refresh-token-ttl-days:7}")
    long refreshTokenTtlDays = 7;

    @NonFinal
    @Value("${security.cookie-secure:false}")
    boolean secureCookies = false;

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
            @CookieValue(value = "anonymous_id", required = false) String anonCookie,
            @RequestHeader(value = "X-Anonymous-Id", required = false) String anonHeader,
            HttpServletRequest httpRequest,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor) {
        String clientIp = forwardedFor == null || forwardedFor.isBlank()
                ? httpRequest.getRemoteAddr()
                : forwardedFor.split(",", 2)[0].trim();
        AuthResult result = authService.login(request, clientIp);

        String anonymousId = (anonHeader != null && !anonHeader.isBlank()) ? anonHeader.trim() : anonCookie;
        boolean mergeSuccess = false;
        if (anonymousId != null && !anonymousId.isBlank() && result.response() != null && result.response().getUserId() != null) {
            mergeSuccess = orderServiceClient.triggerCartMerge(result.response().getUserId(), anonymousId);
        }

        var responseBuilder = ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        CookieUtils.createRefreshTokenCookie(result.refreshToken(), refreshTokenTtlDays, secureCookies).toString());

        // Chỉ dọn cookie guest khi merge xác nhận thành công
        if (mergeSuccess && anonCookie != null && !anonCookie.isBlank()) {
            ResponseCookie clearAnonCookie = ResponseCookie.from("anonymous_id", "")
                    .path("/")
                    .maxAge(0)
                    .sameSite("Lax")
                    .build();
            responseBuilder.header(HttpHeaders.SET_COOKIE, clearAnonCookie.toString());
        }

        return responseBuilder.body(ApiResponse.<AuthResponse>builder()
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
