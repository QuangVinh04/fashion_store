package com.fashionstore.identity.service;

import org.springframework.security.oauth2.jwt.Jwt;

public interface UserProvisioningService {

    /**
     * Đảm bảo user của token Keycloak tồn tại trong identity (id = sub) và email khớp với Keycloak.
     */
    void provision(Jwt jwt);
}
