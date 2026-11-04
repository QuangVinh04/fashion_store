package com.fashionstore.identity.dto.group;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Keycloak bắt role-mapping gửi kèm cả id lẫn name của role. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakRoleRepresentation(String id, String name) {
}
