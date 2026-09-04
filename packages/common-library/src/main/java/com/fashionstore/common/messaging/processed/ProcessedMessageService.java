package com.fashionstore.common.messaging.processed;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Chốt chặn idempotency tầng message: cùng một messageId chỉ chạy action đúng một lần cho mỗi consumer.
 *
 * <p>Nguồn sự thật là unique index {@code (message_id, consumer_name)} của bảng processed_message. Bản ghi
 * được insert trong chính transaction nghiệp vụ, nên nếu action thất bại thì dấu vết cũng biến mất và
 * message được xử lý lại khi Rabbit redeliver.
 *
 * <p>Redis chỉ là bộ lọc nhanh để khỏi phải chạm DB với message trùng. Key chỉ được ghi <b>sau khi commit</b>:
 * ghi trước commit thì một lần crash giữa chừng sẽ khiến message bị bỏ qua vĩnh viễn dù DB đã rollback.
 */
@Slf4j
public class ProcessedMessageService {
    private static final String REDIS_KEY_PREFIX = "msg_processed:";
    private static final Duration REDIS_TTL = Duration.ofDays(7);
    private static final String INSERT_SQL = """
            insert into processed_message (
                id,
                message_id,
                consumer_name,
                processed_at,
                created_at,
                updated_at
            ) values (?, ?, ?, ?, ?, ?)
            on conflict (message_id, consumer_name) do nothing
            """;

    private final StringRedisTemplate redisTemplate;
    private final JdbcOperations jdbcOperations;

    public ProcessedMessageService(StringRedisTemplate redisTemplate, JdbcOperations jdbcOperations) {
        this.redisTemplate = redisTemplate;
        this.jdbcOperations = jdbcOperations;
    }

    public void processOnce(String messageId, String consumerName, Runnable action) {
        String redisKey = REDIS_KEY_PREFIX + consumerName + ":" + messageId;

        // 1. BỘ LỌC NHANH: đã có key nghĩa là một transaction trước đã commit thành công
        if (seenInRedis(redisKey)) {
            log.debug("Message [ID: {}, Consumer: {}] duplicate detected by REDIS, skipping.", messageId, consumerName);
            return;
        }

        // 2. CHỐT CHẶN THẬT: unique index quyết định ai là người đầu tiên.
        //    Dùng ON CONFLICT DO NOTHING thay vì bắt DuplicateKeyException — với Postgres, một constraint
        //    violation sẽ abort cả transaction, làm hỏng luôn phần nghiệp vụ chạy sau đó.
        LocalDateTime now = LocalDateTime.now();
        int inserted = jdbcOperations.update(
                INSERT_SQL,
                UUID.randomUUID().toString(),
                messageId,
                consumerName,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );

        if (inserted == 0) {
            log.info("Message [ID: {}, Consumer: {}] duplicate detected by DB, skipping.", messageId, consumerName);
            markSeen(redisKey);
            return;
        }

        // 3. Logic nghiệp vụ. Ném exception ở đây thì transaction rollback, kể cả bản ghi vừa insert ở trên.
        action.run();

        markSeenAfterCommit(redisKey);
    }

    private boolean seenInRedis(String redisKey) {
        if (redisTemplate == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
        } catch (Exception ex) {
            log.warn("Redis unavailable while checking {}, falling back to DB guard: {}", redisKey, ex.getMessage());
            return false;
        }
    }

    private void markSeenAfterCommit(String redisKey) {
        if (redisTemplate == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            markSeen(redisKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                markSeen(redisKey);
            }
        });
    }

    private void markSeen(String redisKey) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(redisKey, "1", REDIS_TTL);
        } catch (Exception ex) {
            // Mất key chỉ làm chậm lần trùng kế tiếp, không ảnh hưởng tính đúng đắn.
            log.warn("Failed to write Redis marker {}: {}", redisKey, ex.getMessage());
        }
    }
}
