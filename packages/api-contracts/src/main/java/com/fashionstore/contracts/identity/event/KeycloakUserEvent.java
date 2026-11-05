package com.fashionstore.contracts.identity.event;

/**
 * Payload của {@code keycloak.user.registered} và {@code keycloak.user.email-updated}.
 * {@code userId} là {@code sub} của Keycloak (= {@code users.id} của identity). Event cập nhật email không mang tên.
 */
public record KeycloakUserEvent(
        String userId,
        String email,
        String firstName,
        String lastName
) {
}
