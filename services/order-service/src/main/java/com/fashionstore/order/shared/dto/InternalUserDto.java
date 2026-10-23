package com.fashionstore.order.shared.dto;

public record InternalUserDto(
        String id,
        String email,
        String fullName,
        String phone
) {
}
