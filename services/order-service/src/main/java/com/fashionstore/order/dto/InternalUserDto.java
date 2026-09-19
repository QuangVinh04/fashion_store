package com.fashionstore.order.dto;

public record InternalUserDto(
        String id,
        String email,
        String fullName,
        String phone
) {
}
