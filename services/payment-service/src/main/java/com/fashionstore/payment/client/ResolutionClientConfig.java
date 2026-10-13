package com.fashionstore.payment.client;
import com.fashionstore.common.security.InternalServiceTokenInterceptor;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
public class ResolutionClientConfig {
    @Bean RequestInterceptor internalServiceTokenInterceptor(OAuth2AuthorizedClientManager manager) {
        return new InternalServiceTokenInterceptor(manager);
    }
}
