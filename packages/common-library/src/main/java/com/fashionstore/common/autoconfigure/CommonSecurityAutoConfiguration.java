package com.fashionstore.common.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.security.ApiAccessDeniedHandler;
import com.fashionstore.common.security.ApiAuthenticationEntryPoint;
import com.fashionstore.common.security.CurrentAccessTokenProvider;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.common.security.KeycloakJwtAuthoritiesConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.List;

@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration")
@ConditionalOnClass(Authentication.class)
public class CommonSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CurrentUserProvider.class)
    CurrentUserProvider commonCurrentUserProvider() {
        return new CurrentUserProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    CurrentAccessTokenProvider currentAccessTokenProvider() {
        return new CurrentAccessTokenProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    ApiAuthenticationEntryPoint apiAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new ApiAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    ApiAccessDeniedHandler apiAccessDeniedHandler(ObjectMapper objectMapper) {
        return new ApiAccessDeniedHandler(objectMapper);
    }

    // Resource server của các service: role Keycloak -> authorities (JwtConfigurer tự lấy bean này)
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(JwtAuthenticationConverter.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    JwtAuthenticationConverter keycloakJwtAuthenticationConverter() {
        KeycloakJwtAuthoritiesConverter authorities = new KeycloakJwtAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> List.copyOf(authorities.convert(jwt)));
        return converter;
    }

    // Service gọi /internal/** qua Feign: client_credentials, dùng được cả ngoài request (listener, scheduler)
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager")
    @ConditionalOnBean(type = "org.springframework.security.oauth2.client.registration.ClientRegistrationRepository")
    static class ClientCredentialsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        OAuth2AuthorizedClientManager clientCredentialsAuthorizedClientManager(
                ClientRegistrationRepository clientRegistrationRepository) {
            AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                    new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                            clientRegistrationRepository,
                            new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository));
            manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                    .clientCredentials()
                    .build());
            return manager;
        }
    }
}
