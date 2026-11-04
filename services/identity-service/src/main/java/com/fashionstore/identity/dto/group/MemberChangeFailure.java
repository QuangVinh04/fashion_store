package com.fashionstore.identity.dto.group;

/** {@code code} là mã lỗi nghiệp vụ của identity-service (xem {@code IdentityErrorCode}). */
public record MemberChangeFailure(String userId, int code, String message) {
}
