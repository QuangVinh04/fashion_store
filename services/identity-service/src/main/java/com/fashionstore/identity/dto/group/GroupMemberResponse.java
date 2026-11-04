package com.fashionstore.identity.dto.group;

/** email / fullName / isActive null khi id chưa có trong identity database (chưa từng đăng nhập). */
public record GroupMemberResponse(String userId, String email, String fullName, Boolean isActive) {
}
