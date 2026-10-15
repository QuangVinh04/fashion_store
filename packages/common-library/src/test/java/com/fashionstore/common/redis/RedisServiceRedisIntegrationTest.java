package com.fashionstore.common.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

/** RedisService trên một Redis thật: TTL, SET NX và cache đọc-xuyên hành xử đúng như mock giả định. */
@Tag("external-infrastructure")
class RedisServiceRedisIntegrationTest {

    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7").withExposedPorts(6379).waitingFor(Wait.forListeningPort());
    static final LettuceConnectionFactory CONNECTION;
    static final StringRedisTemplate TEMPLATE;

    static {
        REDIS.start();
        CONNECTION = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        CONNECTION.afterPropertiesSet();
        TEMPLATE = new StringRedisTemplate(CONNECTION);
        TEMPLATE.afterPropertiesSet();
    }

    final RedisService redis = new RedisService(TEMPLATE, new ObjectMapper(), "it-service");

    @AfterAll
    static void stop() {
        CONNECTION.destroy();
        REDIS.stop();
    }

    private String uniqueKey() {
        return redis.key("it", UUID.randomUUID().toString());
    }

    @Test
    void aValueIsStoredWithItsTtlAndIsReadBack() {
        String key = uniqueKey();

        redis.set(key, "v", Duration.ofMinutes(10));

        assertThat(redis.get(key)).contains("v");
        assertThat(TEMPLATE.getExpire(key, TimeUnit.SECONDS)).isBetween(1L, 600L);
    }

    @Test
    void setIfAbsentLetsExactlyOneCallerWin() {
        String key = uniqueKey();

        assertThat(redis.setIfAbsent(key, "first", Duration.ofMinutes(1))).isTrue();
        assertThat(redis.setIfAbsent(key, "second", Duration.ofMinutes(1))).isFalse();
        assertThat(redis.get(key)).contains("first");
    }

    @Test
    void existsAndDeleteReflectTheRealKeyspace() {
        String key = uniqueKey();
        redis.set(key, "v", Duration.ofMinutes(1));
        assertThat(redis.exists(key)).isTrue();

        redis.delete(key);

        assertThat(redis.exists(key)).isFalse();
    }

    @Test
    void aKeyExpiresOnItsOwn() throws Exception {
        String key = uniqueKey();
        redis.set(key, "v", Duration.ofMillis(200));

        Thread.sleep(500);

        assertThat(redis.exists(key)).isFalse();
    }

    @Test
    void getOrLoadCallsTheLoaderOnceAndServesTheRestFromRedis() {
        String key = uniqueKey();
        AtomicInteger loads = new AtomicInteger();
        TypeReference<List<String>> type = new TypeReference<>() {};

        for (int i = 0; i < 3; i++) {
            List<String> result = redis.getOrLoad(key, Duration.ofMinutes(1), type, () -> {
                loads.incrementAndGet();
                return List.of("a", "b");
            });
            assertThat(result).containsExactly("a", "b");
        }

        assertThat(loads).hasValue(1);
    }

    @Test
    void aRedisThatIsUnreachableNeverBreaksTheCaller() {
        var broken = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1));
        broken.afterPropertiesSet();
        var template = new StringRedisTemplate(broken);
        template.afterPropertiesSet();
        RedisService down = new RedisService(template, new ObjectMapper(), "it-service");

        assertThat(down.get("k")).isEmpty();
        assertThat(down.exists("k")).isFalse();
        assertThat(down.getOrLoad("k", Duration.ofMinutes(1), new TypeReference<List<String>>() {}, () -> List.of("fresh")))
                .containsExactly("fresh");
        broken.destroy();
    }
}
