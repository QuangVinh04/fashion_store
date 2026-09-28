package com.fashionstore.backofficebff;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.oidc.server.session.ReactiveOidcSessionRegistry;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionInformation;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.web.server.session.WebSessionManager;
import org.springframework.web.server.session.WebSessionStore;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin khoá tài khoản → Keycloak gọi back-channel logout → BFF phải xoá đúng session của user đó.
 * Chạy server thật (handler tự gọi lại chính BFF qua HTTP) với JWKS giả thay cho Keycloak.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.autoconfigure.exclude="
                + "com.fashionstore.common.autoconfigure.CommonRedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration")
class BackofficeBackChannelLogoutTest {

    private static final String ISSUER = "http://localhost:8180/realms/fashion-store";
    private static final String SESSION_COOKIE = "FS_BACKOFFICE_SESSION";
    private static final RSAKey SIGNING_KEY = generateKey();
    private static final HttpServer keycloakJwks = startJwksServer();

    @LocalServerPort
    int port;

    @Autowired
    ReactiveOidcSessionRegistry oidcSessionRegistry;

    @Autowired
    WebSessionManager webSessionManager;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("KEYCLOAK_INTERNAL_URL", () -> "http://localhost:" + keycloakJwks.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        keycloakJwks.stop(0);
    }

    @Test
    void keycloakBackChannelLogoutInvalidatesOnlyTheLoggedOutUsersSession() {
        String lockedUserSession = openSession();
        String otherUserSession = openSession();
        registerOidcSession(lockedUserSession, "kc-locked", "sid-locked");
        registerOidcSession(otherUserSession, "kc-other", "sid-other");

        client().post().uri("/logout/connect/back-channel/keycloak")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("logout_token", logoutToken("kc-locked", "sid-locked")))
                .exchange()
                .expectStatus().isOk();

        assertThat(sessionStore().retrieveSession(lockedUserSession).block()).isNull();
        assertThat(sessionStore().retrieveSession(otherUserSession).block()).isNotNull();
    }

    @Test
    void backChannelLogoutWithForgedTokenIsRejected() {
        String session = openSession();
        registerOidcSession(session, "kc-victim", "sid-victim");

        client().post().uri("/logout/connect/back-channel/keycloak")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("logout_token", signedBy(generateKey(), "kc-victim", "sid-victim")))
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(sessionStore().retrieveSession(session).block()).isNotNull();
    }

    /** Bắt đầu đăng nhập tạo WebSession thật (lưu authorization request) và trả session id từ cookie. */
    private String openSession() {
        var cookie = client().get().uri("/oauth2/authorization/keycloak")
                .exchange()
                .returnResult(Void.class)
                .getResponseCookies().getFirst(SESSION_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }

    /** Việc oauth2Login làm sau khi đăng nhập thành công: gắn sid của Keycloak với session của BFF. */
    private void registerOidcSession(String sessionId, String subject, String sid) {
        Instant now = Instant.now();
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token")
                .issuer(ISSUER).audience(List.of("backoffice-bff")).subject(subject).claim("sid", sid)
                .issuedAt(now).expiresAt(now.plusSeconds(300))
                .build();
        oidcSessionRegistry.saveSessionInformation(
                new OidcSessionInformation(sessionId, Map.of(), new DefaultOidcUser(List.of(), idToken))).block();
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private WebSessionStore sessionStore() {
        return ((DefaultWebSessionManager) webSessionManager).getSessionStore();
    }

    private static String logoutToken(String subject, String sid) {
        return signedBy(SIGNING_KEY, subject, sid);
    }

    private static String signedBy(RSAKey key, String subject, String sid) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .audience("backoffice-bff")
                    .subject(subject)
                    .claim("sid", sid)
                    .issueTime(new Date())
                    .jwtID(UUID.randomUUID().toString())
                    .claim("events", Map.of("http://schemas.openid.net/event/backchannel-logout", Map.of()))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(key.getKeyID()).type(new JOSEObjectType("logout+jwt")).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer startJwksServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            byte[] jwks = new JWKSet(SIGNING_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/protocol/openid-connect/certs", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                exchange.getResponseBody().write(jwks);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
