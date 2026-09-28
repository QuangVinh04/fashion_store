package com.fashionstore.identity.dto.user;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "active không được để trống")
        Boolean active
) {
}
