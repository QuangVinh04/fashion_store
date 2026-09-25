package com.fashionstore.backofficebff.dto;

import java.util.List;

public record SessionResponse(
        boolean authenticated,
        String userId,
        String email,
        String name,
        List<String> roles
) {

    public static SessionResponse anonymous() {
        return new SessionResponse(false, null, null, null, List.of());
    }
}
