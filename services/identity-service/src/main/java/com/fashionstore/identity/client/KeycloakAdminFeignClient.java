package com.fashionstore.identity.client;

import com.fashionstore.common.config.feign.FeignGlobalConfig;
import com.fashionstore.identity.dto.user.KeycloakUserRepresentation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Keycloak Admin REST API của realm fashion-store; id = Keycloak sub = users.id. */
@FeignClient(
        name = "keycloak-admin",
        url = "${app.keycloak.admin-url}",
        configuration = {FeignGlobalConfig.class, KeycloakAdminFeignClientConfig.class}
)
public interface KeycloakAdminFeignClient {

    @PutMapping("/users/{id}")
    void updateUser(@PathVariable("id") String id, @RequestBody KeycloakUserRepresentation user);

    /** Huỷ mọi SSO session + refresh token của user; Keycloak bắn back-channel logout sang các BFF. */
    @PostMapping("/users/{id}/logout")
    void logout(@PathVariable("id") String id);
}
