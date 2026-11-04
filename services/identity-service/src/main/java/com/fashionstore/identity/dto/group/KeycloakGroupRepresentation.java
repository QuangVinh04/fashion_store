package com.fashionstore.identity.dto.group;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/** Nhóm Keycloak; {@code clientRoles} khoá theo clientId, chỉ có khi gọi với briefRepresentation=false. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeycloakGroupRepresentation(
        String id,
        String name,
        List<String> realmRoles,
        Map<String, List<String>> clientRoles) {

    public static KeycloakGroupRepresentation named(String name) {
        return new KeycloakGroupRepresentation(null, name, null, null);
    }
}
