package com.fashionstore.catalog.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class OrderFeignClientConfig {

    @Value("${app.internal.secret-token:fashion-store-internal-secret-token}")
    private String internalSecretToken;

    @Bean
    public RequestInterceptor internalTokenRequestInterceptor() {
        return requestTemplate -> {
            if (internalSecretToken != null && !internalSecretToken.isBlank()) {
                requestTemplate.header("X-Internal-Token", internalSecretToken);
            }
        };
    }
}
