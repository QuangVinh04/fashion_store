package com.fashionstore.identity.dto.group;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Bản brief của user trong Keycloak (danh sách thành viên nhóm, tìm user). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUserBrief(String id, String username, String email, String firstName, String lastName,
        Boolean enabled) {

    /** Cùng cách ghép với claim {@code name} trong token (Keycloak: firstName + " " + lastName). */
    public String fullName() {
        String joined = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        return joined.isEmpty() ? null : joined;
    }

    public boolean matches(String lowerCaseKeyword) {
        return contains(email, lowerCaseKeyword) || contains(username, lowerCaseKeyword)
                || contains(fullName(), lowerCaseKeyword);
    }

    private static boolean contains(String value, String lowerCaseKeyword) {
        return value != null && value.toLowerCase().contains(lowerCaseKeyword);
    }
}
