package com.fashionstore.order.client;

import com.fashionstore.common.security.InternalServiceTokenInterceptor;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

public class IdentityFeignClientConfig {

    // Token service account (client_credentials, role internal-caller) cho lời gọi sang identity-service
    @Bean
    public RequestInterceptor internalServiceTokenInterceptor(OAuth2AuthorizedClientManager authorizedClientManager) {
        return new InternalServiceTokenInterceptor(authorizedClientManager);
    }
}
