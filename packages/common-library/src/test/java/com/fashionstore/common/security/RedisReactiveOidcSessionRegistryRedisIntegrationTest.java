package com.fashionstore.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionInformation;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Registry sid -> session BFF trên một Redis thật: phải sống sót qua restart và dùng chung giữa các instance. */
@Tag("external-infrastructure")
class RedisReactiveOidcSessionRegistryRedisIntegrationTest {

    static final String ISSUER = "http://localhost:8180/realms/fashion-store";
    static final String CLIENT = "storefront-bff";

    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7").withExposedPorts(6379).waitingFor(Wait.forListeningPort());
    static final LettuceConnectionFactory CONNECTION;
    static final ReactiveStringRedisTemplate TEMPLATE;

    static {
        REDIS.start();
        CONNECTION = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        CONNECTION.afterPropertiesSet();
        TEMPLATE = new ReactiveStringRedisTemplate(CONNECTION);
    }

    final String namespace = "it:" + UUID.randomUUID();
    final RedisReactiveOidcSessionRegistry registry = newRegistry();

    @AfterAll
    static void stop() {
        CONNECTION.destroy();
        REDIS.stop();
    }

    private RedisReactiveOidcSessionRegistry newRegistry() {
        return new RedisReactiveOidcSessionRegistry(TEMPLATE, new ObjectMapper(), namespace, Duration.ofDays(7));
    }

    @Test
    void logoutTokenWithSidRemovesOnlyThatProviderSession() {
        save("client-1", "user-a", "sid-a");
        save("client-2", "user-b", "sid-b");

        List<OidcSessionInformation> removed = registry.removeSessionInformation(logoutToken("user-a", "sid-a")).collectList().block();

        assertThat(removed).extracting(OidcSessionInformation::getSessionId).containsExactly("client-1");
        assertThat(registry.removeSessionInformation(logoutToken("user-b", "sid-b")).collectList().block())
                .extracting(OidcSessionInformation::getSessionId).containsExactly("client-2");
    }

    @Test
    void theRemovedInformationCarriesTheAuthoritiesTheLogoutHandlerNeeds() {
        registry.saveSessionInformation(information("client-1", "user-a", "sid-a", Map.of("X-XSRF-TOKEN", "csrf-1"))).block();

        OidcSessionInformation removed = registry.removeSessionInformation(logoutToken("user-a", "sid-a")).blockFirst();

        assertThat(removed).isNotNull();
        assertThat(removed.getAuthorities()).containsEntry("X-XSRF-TOKEN", "csrf-1");
        assertThat(removed.getPrincipal().getSubject()).isEqualTo("user-a");
    }

    @Test
    void logoutTokenWithoutSidRemovesEverySessionOfThatSubject() {
        save("client-1", "user-a", "sid-a1");
        save("client-2", "user-a", "sid-a2");
        save("client-3", "user-b", "sid-b");

        List<OidcSessionInformation> removed = registry.removeSessionInformation(logoutToken("user-a", null)).collectList().block();

        assertThat(removed).extracting(OidcSessionInformation::getSessionId).containsExactlyInAnyOrder("client-1", "client-2");
        assertThat(registry.removeSessionInformation(logoutToken("user-b", null)).collectList().block()).hasSize(1);
    }

    @Test
    void aSecondInstanceOfTheBffSeesSessionsRegisteredByTheFirst() {
        save("client-1", "user-a", "sid-a");

        RedisReactiveOidcSessionRegistry otherInstanceAfterRestart = newRegistry();

        assertThat(otherInstanceAfterRestart.removeSessionInformation(logoutToken("user-a", "sid-a")).collectList().block())
                .extracting(OidcSessionInformation::getSessionId).containsExactly("client-1");
    }

    @Test
    void aTokenForAnotherAudienceOrIssuerRemovesNothing() {
        save("client-1", "user-a", "sid-a");

        OidcLogoutToken otherAudience = token(ISSUER, "backoffice-bff", "user-a", "sid-a");
        OidcLogoutToken otherIssuer = token("http://evil.example/realms/x", CLIENT, "user-a", "sid-a");

        assertThat(registry.removeSessionInformation(otherAudience).collectList().block()).isEmpty();
        assertThat(registry.removeSessionInformation(otherIssuer).collectList().block()).isEmpty();
        assertThat(registry.removeSessionInformation(logoutToken("user-a", "sid-a")).collectList().block()).hasSize(1);
    }

    @Test
    void removingByClientSessionIdClearsTheIndexesToo() {
        save("client-1", "user-a", "sid-a");

        OidcSessionInformation removed = registry.removeSessionInformation("client-1").block();

        assertThat(removed).isNotNull();
        assertThat(removed.getSessionId()).isEqualTo("client-1");
        assertThat(registry.removeSessionInformation("client-1").block()).isNull();
        assertThat(registry.removeSessionInformation(logoutToken("user-a", "sid-a")).collectList().block()).isEmpty();
    }

    @Test
    void everyKeyExpiresSoRedisDoesNotFillUpWithDeadSessions() {
        save("client-1", "user-a", "sid-a");

        List<String> keys = TEMPLATE.keys(namespace + ":*").collectList().block();

        assertThat(keys).isNotEmpty();
        for (String key : keys) {
            assertThat(TEMPLATE.getExpire(key).block()).as(key).isPositive();
        }
    }

    @Test
    void storedValuesNeverContainTheIdTokenValue() {
        registry.saveSessionInformation(information("client-1", "user-a", "sid-a", Map.of())).block();

        for (String key : TEMPLATE.keys(namespace + ":session:*").collectList().block()) {
            assertThat(TEMPLATE.opsForValue().get(key).block()).doesNotContain("secret-id-token-value");
        }
    }

    private void save(String clientSessionId, String subject, String sid) {
        registry.saveSessionInformation(information(clientSessionId, subject, sid, Map.of())).block();
    }

    private static OidcSessionInformation information(String clientSessionId, String subject, String sid, Map<String, String> authorities) {
        Instant now = Instant.now();
        OidcIdToken idToken = OidcIdToken.withTokenValue("secret-id-token-value")
                .issuer(ISSUER).audience(List.of(CLIENT)).subject(subject).claim("sid", sid)
                .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new OidcSessionInformation(clientSessionId, authorities, new DefaultOidcUser(List.of(), idToken));
    }

    private static OidcLogoutToken logoutToken(String subject, String sid) {
        return token(ISSUER, CLIENT, subject, sid);
    }

    private static OidcLogoutToken token(String issuer, String audience, String subject, String sid) {
        OidcLogoutToken.Builder builder = OidcLogoutToken.withTokenValue("logout-token")
                .issuer(issuer).audience(List.of(audience)).subject(subject)
                .issuedAt(Instant.now()).jti(UUID.randomUUID().toString())
                .events(Map.of("http://schemas.openid.net/event/backchannel-logout", Map.of()));
        if (sid != null) {
            builder.sessionId(sid);
        }
        return builder.build();
    }
}
