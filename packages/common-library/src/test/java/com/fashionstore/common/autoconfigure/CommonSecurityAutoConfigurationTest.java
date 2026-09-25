package com.fashionstore.common.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommonSecurityAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, CommonSecurityAutoConfiguration.class));

    @Test
    void registersKeycloakJwtAuthenticationConverterForResourceServers() {
        runner.run(context -> {
            JwtAuthenticationConverter converter = context.getBean(JwtAuthenticationConverter.class);
            Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), Map.of(
                    "sub", "kc-user-1",
                    "realm_access", Map.of("roles", List.of("ADMIN")),
                    "resource_access", Map.of("fashion-api", Map.of("roles", List.of("product:write")))));

            var authentication = converter.convert(jwt);

            assertThat(authentication.getName()).isEqualTo("kc-user-1");
            assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                    .containsExactlyInAnyOrder("ROLE_ADMIN", "product:write");
        });
    }

    @Test
    void backsOffWhenServiceDefinesItsOwnConverter() {
        runner.withBean("custom", JwtAuthenticationConverter.class, JwtAuthenticationConverter::new)
                .run(context -> assertThat(context).getBeans(JwtAuthenticationConverter.class).containsOnlyKeys("custom"));
    }

    @Test
    void registersClientCredentialsManagerWhenServiceHasClientRegistration() {
        runner.withBean(ClientRegistrationRepository.class, () -> new InMemoryClientRegistrationRepository(
                        ClientRegistration.withRegistrationId("keycloak")
                                .clientId("order-service").clientSecret("secret")
                                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                                .tokenUri("http://keycloak/token").build()))
                .run(context -> assertThat(context).hasSingleBean(OAuth2AuthorizedClientManager.class));
    }

    @Test
    void noClientCredentialsManagerWithoutClientRegistration() {
        runner.run(context -> assertThat(context).doesNotHaveBean(OAuth2AuthorizedClientManager.class));
    }

    @Test
    void clientCredentialsManagerWinsOverSpringSecurityDefaultInRealServiceSetup() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class,
                        SecurityAutoConfiguration.class,
                        OAuth2ClientAutoConfiguration.class,
                        CommonSecurityAutoConfiguration.class))
                .withPropertyValues(
                        "spring.security.oauth2.client.registration.keycloak.provider=keycloak",
                        "spring.security.oauth2.client.registration.keycloak.client-id=order-service",
                        "spring.security.oauth2.client.registration.keycloak.client-secret=secret",
                        "spring.security.oauth2.client.registration.keycloak.authorization-grant-type=client_credentials",
                        "spring.security.oauth2.client.provider.keycloak.token-uri=http://keycloak/token")
                .run(context -> assertThat(context.getBean(OAuth2AuthorizedClientManager.class))
                        .isInstanceOf(AuthorizedClientServiceOAuth2AuthorizedClientManager.class));
    }
}
