package com.fashionstore.identity.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Body cập nhật một phần UserRepresentation của Keycloak Admin API — chỉ gửi các field khác null. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeycloakUserRepresentation(Boolean enabled) {
}
