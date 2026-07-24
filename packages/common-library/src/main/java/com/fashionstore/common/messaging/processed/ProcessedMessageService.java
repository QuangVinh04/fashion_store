package com.fashionstore.common.messaging.processed;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcOperations;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

public class ProcessedMessageService {

    private static final String INSERT_SQL = """
            insert into processed_message (
                id,
                message_id,
                consumer_name,
                processed_at,
                created_at,
                updated_at
            ) values (?, ?, ?, ?, ?, ?)
            """;

    private final JdbcOperations jdbcOperations;

    public ProcessedMessageService(JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    public void processOnce(String messageId, String consumerName, Runnable action) {
        LocalDateTime now = LocalDateTime.now();
        try {
            jdbcOperations.update(
                    INSERT_SQL,
                    UUID.randomUUID().toString(),
                    messageId,
                    consumerName,
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now)
            );
        } catch (DuplicateKeyException ignored) {
            return;
        }

        action.run();
    }
}
