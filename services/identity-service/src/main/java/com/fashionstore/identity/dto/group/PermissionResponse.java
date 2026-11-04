package com.fashionstore.identity.dto.group;

public record PermissionResponse(String authority, String area, String label, boolean isAssignable) {
}
