package com.fashionstore.common.redis;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;

/**
 * Khai báo một vùng cache dùng với {@link RedisService#getOrLoad(CacheRegion, java.util.function.Supplier, String...)}:
 * tên, kiểu giá trị và TTL gom một chỗ, khai báo như hằng số của service. Sai cấu hình ném lỗi ngay khi
 * class nạp thay vì lúc có request. Đổi cấu trúc giá trị thì đổi tên (vd {@code dashboard-v2}) để bỏ qua
 * dữ liệu cũ thay vì chờ TTL.
 */
public record CacheRegion<T>(String name, TypeReference<T> type, Duration ttl) {

    public CacheRegion {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A cache region needs a name");
        }
        if (type == null) {
            throw new IllegalArgumentException("A cache region needs a value type");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("A cache region TTL must be positive, got " + ttl);
        }
    }
}
