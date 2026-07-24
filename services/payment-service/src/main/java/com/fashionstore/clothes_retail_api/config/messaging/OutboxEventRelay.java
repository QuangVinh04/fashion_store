package com.fashionstore.product.config.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.product.common.outbox.OutboxEvent;
import com.fashionstore.product.common.outbox.OutboxEventRepository;
import com.fashionstore.common.messaging.outbox.OutboxEventStatus;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OutboxEventRelay {

    static int BATCH_SIZE = 50;

    OutboxEventRepository outboxEventRepository;
    RabbitTemplate rabbitTemplate;
    ObjectMapper objectMapper;

    @Transactional
    @Scheduled(fixedDelayString = "${app.outbox.relay-delay-ms:5000}")
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findPublishable(LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));
        events.forEach(this::publish);
    }

    private void publish(OutboxEvent event) {
        try {
            Object payload = objectMapper.readValue(event.getPayload(), Class.forName(event.getEventType()));
            rabbitTemplate.convertAndSend(
                    RabbitMQNames.EXCHANGE,
                    event.getRoutingKey(),
                    payload,
                    message -> {
                        message.getMessageProperties().setHeader(RabbitMQNames.OUTBOX_EVENT_ID_HEADER, event.getId());
                        return message;
                    }
            );
            event.setStatus(OutboxEventStatus.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());
            event.setLastError(null);
        } catch (Exception e) {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            event.setStatus(OutboxEventStatus.FAILED);
            event.setNextAttemptAt(LocalDateTime.now().plusSeconds(Math.min(300, attempts * 10L)));
            event.setLastError(e.getMessage());
            log.warn("Failed to publish outbox event {}", event.getId(), e);
        }
    }
}
