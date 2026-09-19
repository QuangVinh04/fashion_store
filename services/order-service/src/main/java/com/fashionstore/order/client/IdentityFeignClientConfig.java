package com.fashionstore.order.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class IdentityFeignClientConfig {

    @Value("${app.internal.secret-token:fashion-store-internal-secret-token}")
    private String internalSecretToken;

    @Bean
    public RequestInterceptor identityInternalTokenRequestInterceptor() {
        return requestTemplate -> {
            if (internalSecretToken != null && !internalSecretToken.isBlank()) {
                requestTemplate.header("X-Internal-Token", internalSecretToken);
            }
        };
    }
}
