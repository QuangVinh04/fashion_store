package com.fashionstore.payment.config.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.payment.common.outbox.OutboxEvent;
import com.fashionstore.payment.common.outbox.OutboxEventRepository;
import com.fashionstore.contracts.common.EventEnvelope;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OutboxEventRecorder {

    OutboxEventRepository outboxEventRepository;
    ObjectMapper objectMapper;

    @EventListener
    public void record(EventEnvelope<?> event) {
        record(event.eventType(), event);
    }

    private void record(String routingKey, Object event) {
        try {
            outboxEventRepository.save(OutboxEvent.builder()
                    .eventType(event.getClass().getName())
                    .routingKey(routingKey)
                    .payload(objectMapper.writeValueAsString(event))
                    .nextAttemptAt(LocalDateTime.now())
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize outbox event", e);
        }
    }
}
