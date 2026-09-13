package com.fashionstore.identity.util;


import org.springframework.http.ResponseCookie;

import java.time.Duration;

public final class CookieUtils {
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final String AUTH_PATH = "/api/v1/auth";
    private CookieUtils() {}
    // Tạo HttpOnly Cookie chứa refreshToken (TTL 7 ngày)
    public static ResponseCookie createRefreshTokenCookie(String token, long durationDays, boolean secure) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, token)
                .httpOnly(true)                      // Chống XSS (JS không đọc được)
                .secure(secure)                      // Chỉ gửi qua HTTPS ở Production
                .path(AUTH_PATH)                     // Chỉ gửi cookie khi gọi API /api/v1/auth/**
                .maxAge(Duration.ofDays(durationDays))
                .sameSite("Lax")                     // Chống CSRF
                .build();
    }
    // Xóa Cookie khi Đăng xuất (Set MaxAge = 0)
    public static ResponseCookie deleteRefreshTokenCookie(boolean secure) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .path(AUTH_PATH)
                .maxAge(0)                           // Xóa ngay lập tức trên trình duyệt
                .sameSite("Lax")
                .build();
    }
}
