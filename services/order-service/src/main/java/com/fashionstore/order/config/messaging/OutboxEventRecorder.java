package com.fashionstore.order.config.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.contracts.EventEnvelope;
import com.fashionstore.order.entity.OutboxEvent;
import com.fashionstore.order.repository.OutboxEventRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OutboxEventRecorder {

    OutboxEventRepository outboxEventRepository;
    ObjectMapper objectMapper;

    @Transactional
    @EventListener
    public void handle(EventEnvelope<?> event) {
        try {
            outboxEventRepository.save(OutboxEvent.builder()
                    .eventType(event.eventType())
                    .routingKey(event.eventType())
                    .payload(objectMapper.writeValueAsString(event))
                    .nextAttemptAt(LocalDateTime.now())
                    .build());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
