package com.fashionstore.common.messaging.processed;

import com.fashionstore.common.redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcOperations;

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

    private final RedisService redis;
    private final JdbcOperations jdbcOperations;

    public ProcessedMessageService(RedisService redis, JdbcOperations jdbcOperations) {
        this.redis = redis;
        this.jdbcOperations = jdbcOperations;
    }

    public void processOnce(String messageId, String consumerName, Runnable action) {
        String redisKey = redis.key("msg", consumerName, messageId);

        // 1. BỘ LỌC NHANH: đã có key nghĩa là một transaction trước đã commit thành công
        if (redis.exists(redisKey)) {
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
            // Mất key chỉ làm chậm lần trùng kế tiếp, không ảnh hưởng tính đúng đắn.
            redis.set(redisKey, "1", REDIS_TTL);
            return;
        }

        // 3. Logic nghiệp vụ. Ném exception ở đây thì transaction rollback, kể cả bản ghi vừa insert ở trên.
        action.run();

        // Chỉ ghi sau commit: ghi trước thì một lần crash giữa chừng khiến message bị bỏ qua vĩnh viễn.
        redis.setAfterCommit(redisKey, "1", REDIS_TTL);
    }
}
