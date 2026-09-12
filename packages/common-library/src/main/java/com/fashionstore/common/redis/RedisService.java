package com.fashionstore.common.redis;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@ConditionalOnBean(RedisTemplate.class) // CHỈ KÍCH HOẠT KHI DỰ ÁN CÓ DÙNG REDIS
public class RedisService {
    RedisTemplate<String, Object> redisTemplate;

    public void setValue(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }
    public void setWithTTL(String key, Object value, long timeout, TimeUnit unit) {
        if (timeout <= 0 || unit == null) {
            throw new IllegalArgumentException("Timeout must be greater than 0 and unit must not be null");
        }
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }
    public void setUntil(String key, Object value, long expirationTimestampMs) {
        long ttl = expirationTimestampMs - System.currentTimeMillis();
        if (ttl > 0) {
            this.setWithTTL(key, value, ttl, TimeUnit.MILLISECONDS);
        } else {
            log.warn("Expiration time for key {} is in the past.", key);
        }
    }
    public Object getValue(String key) {
        return redisTemplate.opsForValue().get(key);
    }
    @SuppressWarnings("unchecked")
    public <T> T getValue(String key, Class<T> clazz) {
        Object val = redisTemplate.opsForValue().get(key);
        return val != null ? (T) val : null;
    }
    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }
    public boolean setExpire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }
    public void deleteValue(String key) {
        redisTemplate.delete(key);
    }
    public void deleteKeys(Collection<String> keys) {
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
    public boolean existsValue(String key) {
        return redisTemplate.hasKey(key);
    }
}
