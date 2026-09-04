package com.fashionstore.common.messaging.processed;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcOperations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessedMessageServiceTest {

    private final JdbcOperations jdbcOperations = mock(JdbcOperations.class);

    @Test
    void runsActionExactlyOnceForANewMessage() {
        insertReturns(1);
        AtomicInteger runs = new AtomicInteger();

        new ProcessedMessageService(null, jdbcOperations)
                .processOnce("message-1", "consumer-1", runs::incrementAndGet);

        assertEquals(1, runs.get());
    }

    @Test
    void skipsMessageAlreadyInsertedByAnEarlierDelivery() {
        insertReturns(0);   // on conflict do nothing
        AtomicInteger runs = new AtomicInteger();

        new ProcessedMessageService(null, jdbcOperations)
                .processOnce("message-1", "consumer-1", runs::incrementAndGet);

        assertEquals(0, runs.get());
    }

    @Test
    void skipsMessageAlreadyMarkedInRedisWithoutTouchingTheDatabase() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        AtomicInteger runs = new AtomicInteger();

        new ProcessedMessageService(redisTemplate, jdbcOperations)
                .processOnce("message-1", "consumer-1", runs::incrementAndGet);

        assertEquals(0, runs.get());
        verify(jdbcOperations, never()).update(anyString(), any(Object[].class));
    }

    /**
     * Ghi key trước khi action chạy xong thì một lần crash giữa chừng sẽ khiến message bị bỏ qua vĩnh viễn
     * dù DB đã rollback — nên thứ tự này là một phần của tính đúng đắn, không phải chi tiết cài đặt.
     */
    @Test
    void writesRedisMarkerOnlyAfterTheActionSucceeded() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        insertReturns(1);
        AtomicInteger runs = new AtomicInteger();

        new ProcessedMessageService(redisTemplate, jdbcOperations)
                .processOnce("message-1", "consumer-1", runs::incrementAndGet);

        assertEquals(1, runs.get());
        inOrder(redisTemplate, valueOperations)
                .verify(valueOperations)
                .set(eq("msg_processed:consumer-1:message-1"), eq("1"), any(Duration.class));
    }

    private void insertReturns(int rows) {
        when(jdbcOperations.update(
                anyString(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(rows);
    }
}
