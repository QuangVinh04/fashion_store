package com.fashionstore.common.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Một khuôn dùng Redis cho cả repo: fail-open, TTL bắt buộc, key có namespace, giá trị là chuỗi/JSON. */
class RedisServiceTest {

    static final Duration TTL = Duration.ofMinutes(5);
    static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    StringRedisTemplate template = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> values = mock(ValueOperations.class);
    ObjectMapper mapper = new ObjectMapper();
    RedisService redis;

    @BeforeEach
    void setUp() {
        when(template.opsForValue()).thenReturn(values);
        redis = new RedisService(template, mapper, "order-service");
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ----- key -----

    @Test
    void keysAreNamespacedByTheApplicationSoServicesNeverCollideInTheSharedRedis() {
        assertThat(redis.key("ghn", "staging", "locations", "v2"))
                .isEqualTo("fashion-store:order-service:ghn:staging:locations:v2");
    }

    @Test
    void aKeyPartMustNotBeBlank() {
        assertThatThrownBy(() -> redis.key("ghn", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> redis.key()).isInstanceOf(IllegalArgumentException.class);
    }

    // ----- chuỗi -----

    @Test
    void setAlwaysCarriesATtl() {
        redis.set("k", "v", TTL);

        verify(values).set("k", "v", TTL);
    }

    @Test
    void aMissingOrNonPositiveTtlIsAProgrammingErrorNotSomethingToSwallow() {
        assertThatThrownBy(() -> redis.set("k", "v", Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> redis.set("k", "v", Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> redis.set("k", "v", null)).isInstanceOf(IllegalArgumentException.class);
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void getReturnsTheStoredValueOrEmpty() {
        when(values.get("hit")).thenReturn("v");

        assertThat(redis.get("hit")).contains("v");
        assertThat(redis.get("miss")).isEmpty();
    }

    @Test
    void setIfAbsentTellsWhetherThisCallerWon() {
        when(values.setIfAbsent("won", "1", TTL)).thenReturn(true);
        when(values.setIfAbsent("lost", "1", TTL)).thenReturn(false);

        assertThat(redis.setIfAbsent("won", "1", TTL)).isTrue();
        assertThat(redis.setIfAbsent("lost", "1", TTL)).isFalse();
        assertThat(redis.setIfAbsent("unknown", "1", TTL)).isFalse(); // null từ Redis = không thắng
    }

    @Test
    void existsAndDeleteDelegate() {
        when(template.hasKey("here")).thenReturn(true);

        assertThat(redis.exists("here")).isTrue();
        assertThat(redis.exists("gone")).isFalse();
        redis.delete("a", "b");
        verify(template).delete(List.of("a", "b"));
    }

    // ----- fail-open -----

    @Test
    void everyOperationFailsOpenWhenRedisIsDown() {
        var down = new RedisConnectionFailureException("down");
        when(values.get(anyString())).thenThrow(down);
        doThrow(down).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenThrow(down);
        when(template.hasKey(anyString())).thenThrow(down);
        doThrow(down).when(template).delete(any(java.util.Collection.class));

        assertThat(redis.get("k")).isEmpty();
        assertThat(redis.exists("k")).isFalse();
        assertThat(redis.setIfAbsent("k", "v", TTL)).isFalse();
        assertThat(redis.getJson("k", STRINGS)).isEmpty();
        assertThatCode(() -> redis.set("k", "v", TTL)).doesNotThrowAnyException();
        assertThatCode(() -> redis.setJson("k", List.of("a"), TTL)).doesNotThrowAnyException();
        assertThatCode(() -> redis.delete("k")).doesNotThrowAnyException();
        assertThatCode(() -> redis.setAfterCommit("k", "v", TTL)).doesNotThrowAnyException();
    }

    @Test
    void withoutARedisConnectionEverythingIsANoOp() {
        RedisService off = new RedisService(null, mapper, "order-service");
        AtomicInteger loads = new AtomicInteger();

        assertThat(off.get("k")).isEmpty();
        assertThat(off.exists("k")).isFalse();
        assertThat(off.setIfAbsent("k", "v", TTL)).isFalse();
        assertThatCode(() -> off.set("k", "v", TTL)).doesNotThrowAnyException();
        assertThatCode(() -> off.delete("k")).doesNotThrowAnyException();
        off.getOrLoad("k", TTL, STRINGS, () -> { loads.incrementAndGet(); return List.of("x"); });
        off.getOrLoad("k", TTL, STRINGS, () -> { loads.incrementAndGet(); return List.of("x"); });
        assertThat(loads).hasValue(2);
    }

    // ----- JSON -----

    @Test
    void jsonRoundTripsThroughTheSharedMapper() {
        redis.setJson("k", List.of("a", "b"), TTL);
        verify(values).set("k", "[\"a\",\"b\"]", TTL);

        when(values.get("k")).thenReturn("[\"a\",\"b\"]");
        assertThat(redis.getJson("k", STRINGS)).contains(List.of("a", "b"));
    }

    @Test
    void aCorruptedJsonValueIsAMissNotAnError() {
        when(values.get("k")).thenReturn("{not json");

        assertThat(redis.getJson("k", STRINGS)).isEmpty();
    }

    // ----- getOrLoad (cache đọc-xuyên) -----

    @Test
    void getOrLoadServesTheCachedValueWithoutCallingTheLoader() {
        when(values.get("k")).thenReturn("[\"cached\"]");
        AtomicInteger loads = new AtomicInteger();

        List<String> result = redis.getOrLoad("k", TTL, STRINGS, () -> { loads.incrementAndGet(); return List.of("fresh"); });

        assertThat(result).containsExactly("cached");
        assertThat(loads).hasValue(0);
    }

    @Test
    void getOrLoadLoadsOnAMissAndStoresTheResultWithTheTtl() {
        List<String> result = redis.getOrLoad("k", TTL, STRINGS, () -> List.of("fresh"));

        assertThat(result).containsExactly("fresh");
        verify(values).set("k", "[\"fresh\"]", TTL);
    }

    @Test
    void getOrLoadNeverCachesNull() {
        List<String> result = redis.getOrLoad("k", TTL, STRINGS, () -> null);

        assertThat(result).isNull();
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void aLoaderFailurePropagatesAndNothingIsCached() {
        assertThatThrownBy(() -> redis.getOrLoad("k", TTL, STRINGS, () -> { throw new IllegalStateException("upstream"); }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("upstream");
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void getOrLoadStillAnswersWhenRedisIsDown() {
        var down = new RedisConnectionFailureException("down");
        when(values.get(anyString())).thenThrow(down);
        doThrow(down).when(values).set(anyString(), anyString(), any(Duration.class));

        assertThat(redis.getOrLoad("k", TTL, STRINGS, () -> List.of("fresh"))).containsExactly("fresh");
    }

    // ----- ghi sau commit -----

    @Test
    void setAfterCommitWritesImmediatelyWhenThereIsNoTransaction() {
        redis.setAfterCommit("k", "1", TTL);

        verify(values).set("k", "1", TTL);
    }

    @Test
    void setAfterCommitWaitsForTheTransactionToCommit() {
        TransactionSynchronizationManager.initSynchronization();

        redis.setAfterCommit("k", "1", TTL);
        verify(values, never()).set(anyString(), anyString(), any(Duration.class)); // chưa commit

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(values, times(1)).set(eq("k"), eq("1"), eq(TTL));
    }

    @Test
    void aRolledBackTransactionNeverWritesTheMarker() {
        TransactionSynchronizationManager.initSynchronization();

        redis.setAfterCommit("k", "1", TTL);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }
}
