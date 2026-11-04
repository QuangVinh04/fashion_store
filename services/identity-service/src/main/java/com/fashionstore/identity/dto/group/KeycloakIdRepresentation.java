package com.fashionstore.identity.dto.group;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Phần đọc được của client / user trả về từ Keycloak khi chỉ cần id. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakIdRepresentation(String id) {
}
