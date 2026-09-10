package com.fashionstore.contracts.common;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        String eventId,
        String eventType,
        int version,
        String aggregateId,
        Instant occurredAt,
        String correlationId,
        T payload
) {

    public static <T> EventEnvelope<T> v1(
            String eventType,
            String aggregateId,
            String correlationId,
            T payload
    ) {
        return new EventEnvelope<>(
                UUID.randomUUID().toString(),
                eventType,
                1,
                aggregateId,
                Instant.now(),
                correlationId,
                payload
        );
    }
}
