package com.fashionstore.identity.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "Refresh token không được để trống")
        String refreshToken
) {}
