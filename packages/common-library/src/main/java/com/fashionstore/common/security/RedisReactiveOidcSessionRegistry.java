package com.fashionstore.common.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken;
import org.springframework.security.oauth2.client.oidc.server.session.ReactiveOidcSessionRegistry;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionInformation;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Registry sid (phiên SSO Keycloak) -> session BFF, lưu trong Redis thay cho {@code InMemoryReactiveOidcSessionRegistry}.
 * Trong RAM, bảng này mất khi BFF restart hoặc không thấy được ở instance khác, khiến back-channel logout
 * (admin khoá tài khoản, đăng xuất ở nơi khác) không huỷ được session còn sống.
 *
 * <pre>
 * {ns}:session:{clientSessionId}  JSON StoredSession
 * {ns}:sid:{sid}                  SET clientSessionId   (logout token có sid)
 * {ns}:sub:{subject}              SET clientSessionId   (logout token chỉ có sub)
 * </pre>
 * Mọi key có TTL (≥ thời gian sống tối đa của phiên SSO). ID token thô không được lưu.
 */
@Slf4j
public class RedisReactiveOidcSessionRegistry implements ReactiveOidcSessionRegistry {

    private static final String SID_CLAIM = "sid";
    // Chỉ để dựng lại OidcIdToken hợp lệ khi đọc ra: token thật không bao giờ nằm trong Redis
    private static final String REDACTED_TOKEN_VALUE = "redacted";

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String namespace;
    private final Duration ttl;

    /**
     * Dựng template bên trong common-library: code của service/platform không được import template của Spring Data
     * Redis (RedisUsageTest), nên BFF chỉ truyền {@link ReactiveRedisConnectionFactory} do Spring Boot cấu hình sẵn.
     */
    public static RedisReactiveOidcSessionRegistry create(
            ReactiveRedisConnectionFactory connectionFactory, ObjectMapper objectMapper, String namespace, Duration ttl) {
        return new RedisReactiveOidcSessionRegistry(new ReactiveStringRedisTemplate(connectionFactory), objectMapper, namespace, ttl);
    }

    RedisReactiveOidcSessionRegistry(
            ReactiveStringRedisTemplate redis, ObjectMapper objectMapper, String namespace, Duration ttl) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.namespace = namespace;
        this.ttl = ttl;
    }

    @Override
    public Mono<Void> saveSessionInformation(OidcSessionInformation information) {
        StoredSession stored = StoredSession.from(information);
        String clientSessionId = stored.sessionId();
        Mono<Boolean> saveSession = redis.opsForValue().set(sessionKey(clientSessionId), toJson(stored), ttl);
        return saveSession
                .then(index(subjectKey(stored.subject()), clientSessionId))
                .then(stored.sid() == null ? Mono.empty() : index(sidKey(stored.sid()), clientSessionId));
    }

    @Override
    public Mono<OidcSessionInformation> removeSessionInformation(String clientSessionId) {
        return load(clientSessionId).flatMap(this::remove);
    }

    @Override
    public Flux<OidcSessionInformation> removeSessionInformation(OidcLogoutToken logoutToken) {
        String providerSessionId = logoutToken.getSessionId();
        String indexKey = providerSessionId != null ? sidKey(providerSessionId) : subjectKey(logoutToken.getSubject());
        String issuer = logoutToken.getIssuer().toString();
        List<String> audience = logoutToken.getAudience();
        return redis.opsForSet().members(indexKey)
                .flatMap(this::load)
                // Cùng điều kiện với InMemoryOidcSessionRegistry: issuer + audience khớp, rồi sid (hoặc sub)
                .filter(stored -> stored.issuer().equals(issuer)
                        && !Collections.disjoint(stored.audience(), audience)
                        && (providerSessionId != null
                                ? providerSessionId.equals(stored.sid())
                                : logoutToken.getSubject().equals(stored.subject())))
                .flatMap(this::remove);
    }

    private Mono<Void> index(String indexKey, String clientSessionId) {
        return redis.opsForSet().add(indexKey, clientSessionId)
                .then(redis.expire(indexKey, ttl))
                .then();
    }

    private Mono<StoredSession> load(String clientSessionId) {
        return redis.opsForValue().get(sessionKey(clientSessionId)).flatMap(this::fromJson);
    }

    private Mono<OidcSessionInformation> remove(StoredSession stored) {
        String clientSessionId = stored.sessionId();
        Mono<Long> dropSubjectIndex = redis.opsForSet().remove(subjectKey(stored.subject()), clientSessionId);
        Mono<Long> dropSidIndex = stored.sid() == null
                ? Mono.empty()
                : redis.opsForSet().remove(sidKey(stored.sid()), clientSessionId);
        return redis.delete(sessionKey(clientSessionId))
                .then(dropSubjectIndex)
                .then(dropSidIndex)
                .thenReturn(stored.toInformation());
    }

    private String toJson(StoredSession stored) {
        try {
            return objectMapper.writeValueAsString(stored);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize OIDC session " + stored.sessionId(), e);
        }
    }

    private Mono<StoredSession> fromJson(String json) {
        try {
            return Mono.just(objectMapper.readValue(json, StoredSession.class));
        } catch (JsonProcessingException e) {
            // Bản ghi hỏng chỉ làm session đó không bị back-channel logout; không để nó làm hỏng cả luồng logout
            log.warn("Ignoring unreadable OIDC session record: {}", e.getOriginalMessage());
            return Mono.empty();
        }
    }

    private String sessionKey(String clientSessionId) {
        return namespace + ":session:" + clientSessionId;
    }

    private String sidKey(String sid) {
        return namespace + ":sid:" + sid;
    }

    private String subjectKey(String subject) {
        return namespace + ":sub:" + subject;
    }

    /** Phần của OidcSessionInformation đủ để khớp logout token và dựng lại khi đọc ra. */
    record StoredSession(
            String sessionId,
            Map<String, String> authorities,
            String issuer,
            List<String> audience,
            String subject,
            String sid) {

        static StoredSession from(OidcSessionInformation information) {
            var user = information.getPrincipal();
            return new StoredSession(
                    information.getSessionId(),
                    information.getAuthorities(),
                    user.getIssuer().toString(),
                    user.getAudience(),
                    user.getSubject(),
                    user.getClaimAsString(SID_CLAIM));
        }

        OidcSessionInformation toInformation() {
            Instant now = Instant.now();
            OidcIdToken.Builder token = OidcIdToken.withTokenValue(REDACTED_TOKEN_VALUE)
                    .issuer(issuer)
                    .audience(audience)
                    .subject(subject)
                    .issuedAt(now)
                    .expiresAt(now.plus(Duration.ofMinutes(5)));
            if (sid != null) {
                token.claim(SID_CLAIM, sid);
            }
            return new OidcSessionInformation(sessionId, authorities, new DefaultOidcUser(List.of(), token.build()));
        }
    }
}
