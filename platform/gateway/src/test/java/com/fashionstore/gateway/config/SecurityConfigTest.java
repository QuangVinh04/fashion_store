package com.fashionstore.gateway.config;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// Gateway thật + JWKS/backends giả: chỉ token Keycloak hợp lệ đi qua, và Authorization được chuyển tiếp nguyên vẹn
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityConfigTest {

    private static final String KEYCLOAK_ISSUER = "http://localhost:8180/realms/fashion-store";

    private static final RSAKey keycloakKey = generateKey("kc");
    private static final RSAKey otherKey = generateKey("other");
    private static final AtomicReference<String> forwardedAuthorization = new AtomicReference<>();
    private static final AtomicReference<String> forwardedUserId = new AtomicReference<>();
    private static final HttpServer stub = startStub();

    @Autowired
    WebTestClient client;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + stub.getAddress().getPort();
        registry.add("KEYCLOAK_ISSUER", () -> KEYCLOAK_ISSUER);
        registry.add("KEYCLOAK_JWK_SET_URI", () -> base + "/jwks");
        for (String service : new String[]{"IDENTITY", "CATALOG", "ORDER", "PAYMENT"}) {
            registry.add(service + "_BASE_URL", () -> base);
        }
    }

    @BeforeEach
    void reset() {
        forwardedAuthorization.set(null);
        forwardedUserId.set(null);
    }

    @AfterAll
    static void stop() {
        stub.stop(0);
    }

    @Test
    void keycloakTokenIsForwardedUnchanged() throws Exception {
        String token = sign(keycloakKey, claims(KEYCLOAK_ISSUER, "fashion-api"));

        client.get().uri("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk();

        assertThat(forwardedAuthorization.get()).isEqualTo("Bearer " + token);
    }

    @Test
    void clientSentUserHeadersAreNotTrustedAndNotForwarded() {
        client.get().uri("/api/v1/orders")
                .header("X-User-Id", "attacker").header("X-User-Roles", "ROLE_ADMIN")
                .exchange().expectStatus().isUnauthorized();

        assertThat(forwardedUserId.get()).isNull();
    }

    @Test
    void legacyIdentityTokenIsRejected() throws Exception {
        String token = sign(keycloakKey, claims("http://localhost:8080", "fashion-api"));

        client.get().uri("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void tokenWithoutApiAudienceIsRejected() throws Exception {
        String token = sign(keycloakKey, claims(KEYCLOAK_ISSUER, "account"));

        client.get().uri("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void tokenSignedByUnknownKeyIsRejected() throws Exception {
        String token = sign(otherKey, claims(KEYCLOAK_ISSUER, "fashion-api"));

        client.get().uri("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void legacyAuthEndpointsAreGone() {
        client.post().uri("/api/v1/auth/login").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void publicCatalogIsProxiedWithoutToken() {
        client.get().uri("/api/v1/products").exchange().expectStatus().isOk();

        assertThat(forwardedAuthorization.get()).isNull();
    }

    @Test
    void internalEndpointsStayClosedEvenWithValidToken() throws Exception {
        String token = sign(keycloakKey, claims(KEYCLOAK_ISSUER, "fashion-api"));

        client.get().uri("/internal/orders/o-1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isForbidden();
    }

    private static JWTClaimsSet claims(String issuer, String audience) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("kc-user-1")
                .audience(audience)
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(),
                claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static RSAKey generateKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            byte[] jwks = new JWKSet(keycloakKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                exchange.getResponseBody().write(jwks);
                exchange.close();
            });
            server.createContext("/", exchange -> {
                forwardedAuthorization.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                forwardedUserId.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
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
