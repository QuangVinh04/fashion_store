package com.fashionstore.identity.dto.auth;

public record AuthResult(
        AuthResponse response,
        String refreshToken
) {}
