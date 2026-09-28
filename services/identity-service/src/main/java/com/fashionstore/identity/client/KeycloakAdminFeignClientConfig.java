package com.fashionstore.identity.client;

import com.fashionstore.common.security.InternalServiceTokenInterceptor;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

public class KeycloakAdminFeignClientConfig {

    // Token service account của identity-service (client role realm-management: manage-users) — chỉ gửi cho
    // chính Keycloak đã cấp nó, không gắn cho client ra bên ngoài
    @Bean
    public RequestInterceptor keycloakAdminTokenInterceptor(OAuth2AuthorizedClientManager authorizedClientManager) {
        return new InternalServiceTokenInterceptor(authorizedClientManager);
    }
}
