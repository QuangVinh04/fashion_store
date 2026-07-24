package com.fashionstore.common.messaging.processed;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcOperations;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessedMessageServiceTest {

    @Test
    void storesMessageBeforeRunningAction() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        when(jdbcOperations.update(
                anyString(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(1);
        AtomicBoolean executed = new AtomicBoolean();

        new ProcessedMessageService(jdbcOperations)
                .processOnce("message-1", "consumer-1", () -> executed.set(true));

        assertTrue(executed.get());
    }

    @Test
    void skipsMessageAlreadyProcessed() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        when(jdbcOperations.update(
                anyString(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenThrow(new DuplicateKeyException("duplicate"));
        AtomicBoolean executed = new AtomicBoolean();

        new ProcessedMessageService(jdbcOperations)
                .processOnce("message-1", "consumer-1", () -> executed.set(true));

        assertFalse(executed.get());
    }
}
