package com.fashionstore.common.security;

import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalServiceTokenInterceptorTest {

    private static final ClientRegistration REGISTRATION = ClientRegistration.withRegistrationId("keycloak")
            .clientId("order-service")
            .clientSecret("secret")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri("http://keycloak/token")
            .build();

    @Test
    void addsServiceAccountBearerTokenFromClientCredentials() {
        AtomicReference<OAuth2AuthorizeRequest> seen = new AtomicReference<>();
        OAuth2AuthorizedClientManager manager = request -> {
            seen.set(request);
            return new OAuth2AuthorizedClient(REGISTRATION, "order-service", new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER, "service-token", Instant.now(), Instant.now().plusSeconds(300)));
        };
        RequestTemplate template = new RequestTemplate();

        new InternalServiceTokenInterceptor(manager).apply(template);

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer service-token");
        assertThat(seen.get().getClientRegistrationId()).isEqualTo("keycloak");
    }

    @Test
    void failsClosedWhenNoTokenCanBeObtained() {
        OAuth2AuthorizedClientManager manager = request -> null;

        assertThatThrownBy(() -> new InternalServiceTokenInterceptor(manager).apply(new RequestTemplate()))
                .isInstanceOf(IllegalStateException.class);
    }
}
