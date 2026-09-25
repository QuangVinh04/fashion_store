package com.fashionstore.backofficebff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

import java.util.Map;

/**
 * Endpoint Keycloak khai báo tay thay vì discovery: browser đi qua public-url (localhost:8180),
 * BFF gọi token/jwks/userinfo qua internal-url (keycloak:8080) nhưng iss luôn là public-url.
 */
@Configuration
public class OAuth2ClientConfig {

    public static final String REGISTRATION_ID = "keycloak";

    @Bean
    ReactiveClientRegistrationRepository clientRegistrationRepository(
            @Value("${app.keycloak.public-url}") String publicUrl,
            @Value("${app.keycloak.internal-url}") String internalUrl,
            @Value("${app.keycloak.client-id}") String clientId,
            @Value("${app.keycloak.client-secret}") String clientSecret) {
        String publicOidc = publicUrl + "/protocol/openid-connect";
        String internalOidc = internalUrl + "/protocol/openid-connect";
        ClientRegistration keycloak = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientName("Keycloak")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .issuerUri(publicUrl)
                .authorizationUri(publicOidc + "/auth")
                .tokenUri(internalOidc + "/token")
                .jwkSetUri(internalOidc + "/certs")
                .userInfoUri(internalOidc + "/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .providerConfigurationMetadata(Map.of("end_session_endpoint", publicOidc + "/logout"))
                .build();
        return new InMemoryReactiveClientRegistrationRepository(keycloak);
    }

    // Token nằm trong WebSession (Redis) — mặc định của Spring là in-memory, mất khi restart
    @Bean
    ServerOAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new WebSessionServerOAuth2AuthorizedClientRepository();
    }
}
