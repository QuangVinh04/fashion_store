package com.fashionstore.backofficebff;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.OidcLoginMutator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin;

// WebSession trong RAM thay cho Redis
@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "com.fashionstore.common.autoconfigure.CommonRedisAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration")
@AutoConfigureWebTestClient
class BackofficeBffSecurityTest {

    private static final AtomicReference<String> relayedAuthorization = new AtomicReference<>();
    private static final HttpServer apiGateway = startStubApiGateway();

    @Autowired
    WebTestClient client;

    @Autowired
    ReactiveClientRegistrationRepository clientRegistrations;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("API_GATEWAY_URL", () -> "http://localhost:" + apiGateway.getAddress().getPort());
    }

    @BeforeEach
    void reset() {
        relayedAuthorization.set(null);
    }

    @AfterAll
    static void stop() {
        apiGateway.stop(0);
    }

    @Test
    void loginRedirectsToKeycloakWithPkce() {
        String location = client.get().uri("/oauth2/authorization/keycloak")
                .exchange()
                .expectStatus().is3xxRedirection()
                .returnResult(Void.class).getResponseHeaders().getFirst(HttpHeaders.LOCATION);

        assertThat(location)
                .startsWith("http://localhost:8180/realms/fashion-store/protocol/openid-connect/auth")
                .contains("client_id=backoffice-bff")
                .contains("code_challenge_method=S256")
                .contains("code_challenge=");
    }

    @Test
    void anonymousApiCallIsUnauthorizedAndNotProxied() {
        client.get().uri("/api/v1/admin/orders").exchange().expectStatus().isUnauthorized();

        assertThat(relayedAuthorization.get()).isNull();
    }

    @Test
    void anonymousPageRedirectsToLogin() {
        client.get().uri("/products").exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().location("/oauth2/authorization/keycloak");
    }

    @Test
    void customerIsForbiddenAndNotProxied() {
        client.mutateWith(login("ROLE_USER"))
                .get().uri("/api/v1/admin/orders").exchange()
                .expectStatus().isForbidden();

        assertThat(relayedAuthorization.get()).isNull();
    }

    @Test
    void adminApiCallRelaysAccessToken() {
        client.mutateWith(login("ROLE_ADMIN"))
                .get().uri("/api/v1/admin/orders").exchange()
                .expectStatus().isOk();

        assertThat(relayedAuthorization.get()).isEqualTo("Bearer access-token");
    }

    @Test
    void adminUnsafeRequestWithoutCsrfTokenIsRejected() {
        client.mutateWith(login("ROLE_ADMIN"))
                .post().uri("/api/v1/admin/promotions").exchange()
                .expectStatus().isForbidden();

        assertThat(relayedAuthorization.get()).isNull();
    }

    @Test
    void adminUnsafeRequestWithCsrfTokenIsProxied() {
        client.mutateWith(login("ROLE_ADMIN")).mutateWith(csrf())
                .post().uri("/api/v1/admin/promotions").exchange()
                .expectStatus().isOk();

        assertThat(relayedAuthorization.get()).isEqualTo("Bearer access-token");
    }

    @Test
    void legacyAuthEndpointsAreNotExposedEvenForAdmin() {
        client.mutateWith(login("ROLE_ADMIN"))
                .get().uri("/api/v1/auth/jwks").exchange()
                .expectStatus().isForbidden();

        assertThat(relayedAuthorization.get()).isNull();
    }

    @Test
    void sessionEndpointReportsAnonymous() {
        client.get().uri("/bff/session").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.authenticated").isEqualTo(false);
    }

    @Test
    void sessionEndpointReportsCustomerSoSpaCanShowForbidden() {
        client.mutateWith(login("ROLE_USER"))
                .get().uri("/bff/session").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.authenticated").isEqualTo(true)
                .jsonPath("$.data.roles[0]").isEqualTo("ROLE_USER");
    }

    @Test
    void customerCanLogOut() {
        String location = client.mutateWith(login("ROLE_USER")).mutateWith(csrf())
                .post().uri("/logout").exchange()
                .expectStatus().is3xxRedirection()
                .returnResult(Void.class).getResponseHeaders().getFirst(HttpHeaders.LOCATION);

        assertThat(location)
                .startsWith("http://localhost:8180/realms/fashion-store/protocol/openid-connect/logout")
                .contains("id_token_hint=")
                .contains("post_logout_redirect_uri=");
    }

    // mockOidcLogin lưu authorized client (access token mặc định "access-token") cho TokenRelay
    private OidcLoginMutator login(String role) {
        ClientRegistration keycloak = clientRegistrations.findByRegistrationId("keycloak").block();
        return mockOidcLogin()
                .clientRegistration(keycloak)
                .idToken(token -> token.subject("kc-user-1").claim("email", "user@fashion.local"))
                .authorities(new SimpleGrantedAuthority(role));
    }

    private static HttpServer startStubApiGateway() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                relayedAuthorization.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
