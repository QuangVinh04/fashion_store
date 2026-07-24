package com.fashionstore.common.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.security.ApiAccessDeniedHandler;
import com.fashionstore.common.security.ApiAuthenticationEntryPoint;
import com.fashionstore.common.security.CurrentAccessTokenProvider;
import com.fashionstore.common.security.CurrentUserProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@AutoConfiguration
@ConditionalOnClass(JwtAuthenticationToken.class)
public class CommonSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    CurrentUserProvider currentUserProvider() {
        return new CurrentUserProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    CurrentAccessTokenProvider currentAccessTokenProvider() {
        return new CurrentAccessTokenProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    ApiAuthenticationEntryPoint apiAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new ApiAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    ApiAccessDeniedHandler apiAccessDeniedHandler(ObjectMapper objectMapper) {
        return new ApiAccessDeniedHandler(objectMapper);
    }
}
