package com.fashionstore.catalog.client;

import com.fashionstore.common.security.InternalServiceTokenInterceptor;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

public class IdentityAvatarClientConfig {

    @Bean
    public RequestInterceptor internalServiceTokenInterceptor(OAuth2AuthorizedClientManager authorizedClientManager) {
        return new InternalServiceTokenInterceptor(authorizedClientManager);
    }
}
