package com.fashionstore.common.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cách duy nhất để service dùng Redis (trừ session do Spring Session quản lý). Ba quy tắc:
 *
 * <ul>
 *   <li><b>Fail-open:</b> Redis chỉ là cache/bộ lọc nhanh, không phải nguồn sự thật. Mọi hàm tự bắt lỗi
 *       Redis, ghi WARN kèm key rồi coi như miss, nên caller không phải viết {@code try/catch}. Không
 *       có Redis (template null) thì mọi hàm là no-op.
 *   <li><b>TTL bắt buộc:</b> không có overload thiếu TTL, key không bao giờ sống vĩnh viễn. TTL thiếu
 *       hoặc không dương là lỗi lập trình nên ném {@link IllegalArgumentException}, không bị nuốt.
 *   <li><b>Key có namespace:</b> {@link #key} dựng {@code fashion-store:<application>:<phần...>}, vì
 *       Redis dùng chung cho nhiều service.
 * </ul>
 *
 * Giá trị là chuỗi hoặc JSON (Jackson); không dùng serialization Java hay header {@code @class}.
 */
@Slf4j
public class RedisService {
    private static final String ROOT = "fashion-store";

    private final StringRedisTemplate template;
    private final ObjectMapper mapper;
    private final String keyPrefix;

    public RedisService(StringRedisTemplate template, ObjectMapper mapper, String applicationName) {
        this.template = template;
        this.mapper = mapper;
        this.keyPrefix = ROOT + ":" + applicationName;
    }

    /** Dùng khi không có Redis (test, hoặc service chưa cấu hình): mọi hàm là no-op. */
    public static RedisService disabled() {
        return new RedisService(null, new ObjectMapper(), "disabled");
    }

    public String key(String... parts) {
        if (parts == null || parts.length == 0) {
            throw new IllegalArgumentException("A Redis key needs at least one part");
        }
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                throw new IllegalArgumentException("A Redis key part must not be blank");
            }
        }
        return keyPrefix + ":" + String.join(":", parts);
    }

    public Optional<String> get(String key) {
        return attempt("get", key, () -> Optional.ofNullable(template.opsForValue().get(key)), Optional.empty());
    }

    public void set(String key, String value, Duration ttl) {
        requireTtl(ttl);
        attempt("set", key, () -> {
            template.opsForValue().set(key, value, ttl);
            return null;
        }, null);
    }

    /** @return true nếu lời gọi này là người đặt được key (chưa có key trước đó). */
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        requireTtl(ttl);
        return attempt(
                "setIfAbsent",
                key,
                () -> Boolean.TRUE.equals(template.opsForValue().setIfAbsent(key, value, ttl)),
                false);
    }

    public boolean exists(String key) {
        return attempt("exists", key, () -> Boolean.TRUE.equals(template.hasKey(key)), false);
    }

    public void delete(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        attempt("delete", String.join(",", keys), () -> {
            template.delete(List.of(keys));
            return null;
        }, null);
    }

    public <T> Optional<T> getJson(String key, TypeReference<T> type) {
        return get(key).flatMap(json -> {
            try {
                return Optional.ofNullable(mapper.readValue(json, type));
            } catch (Exception corrupted) {
                log.warn("Redis value at {} is not valid JSON, treating as a miss: {}", key, corrupted.getMessage());
                return Optional.empty();
            }
        });
    }

    public void setJson(String key, Object value, Duration ttl) {
        requireTtl(ttl);
        String json;
        try {
            json = mapper.writeValueAsString(value);
        } catch (Exception unserializable) {
            log.warn("Cannot serialize value for Redis key {}: {}", key, unserializable.getMessage());
            return;
        }
        set(key, json, ttl);
    }

    /**
     * Cache đọc-xuyên: có trong Redis thì trả luôn, miss thì gọi {@code loader} rồi lưu với {@code ttl}.
     * Kết quả null không được lưu; loader ném lỗi thì lỗi đó đi tiếp và không lưu gì.
     */
    public <T> T getOrLoad(String key, Duration ttl, TypeReference<T> type, Supplier<T> loader) {
        requireTtl(ttl);
        Optional<T> cached = getJson(key, type);
        if (cached.isPresent()) {
            return cached.get();
        }
        T loaded = loader.get();
        if (loaded != null) {
            setJson(key, loaded, ttl);
        }
        return loaded;
    }

    /**
     * Ghi sau khi transaction DB đã commit. Ghi trước commit thì một lần crash giữa chừng khiến marker
     * tồn tại dù DB rollback. Không có transaction đang chạy thì ghi ngay.
     */
    public void setAfterCommit(String key, String value, Duration ttl) {
        requireTtl(ttl);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            set(key, value, ttl);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                set(key, value, ttl);
            }
        });
    }

    private static void requireTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("A Redis TTL must be positive, got " + ttl);
        }
    }

    private <T> T attempt(String operation, String key, Supplier<T> action, T fallback) {
        if (template == null) {
            return fallback;
        }
        try {
            return action.get();
        } catch (Exception failure) {
            log.warn("Redis {} failed for {}, continuing without it: {}", operation, key, failure.getMessage());
            return fallback;
        }
    }
}
