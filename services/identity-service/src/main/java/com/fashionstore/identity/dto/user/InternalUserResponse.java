package com.fashionstore.identity.dto.user;

public record InternalUserResponse(
        String id,
        String email,
        String fullName,
        String phone
) {
}
