package com.fashionstore.product.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.contracts.common.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OutboxEventRecorder {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    @EventListener
    public void record(EventEnvelope<?> event) {
        try {
            repository.save(OutboxEvent.builder()
                    .eventType(event.eventType())
                    .routingKey(event.eventType())
                    .payload(objectMapper.writeValueAsString(event))
                    .nextAttemptAt(LocalDateTime.now())
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize inventory event", exception);
        }
    }
}
