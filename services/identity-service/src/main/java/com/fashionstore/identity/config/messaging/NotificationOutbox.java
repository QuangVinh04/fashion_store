package com.fashionstore.identity.config.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.contracts.EventEnvelope;
import com.fashionstore.contracts.EventTypes;
import com.fashionstore.identity.entity.OutboxEvent;
import com.fashionstore.identity.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class NotificationOutbox {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    @EventListener
    public void record(EventEnvelope<?> event) {
        if (!EventTypes.NOTIFICATION_EMAIL_REQUESTED.equals(event.eventType())) {
            return;
        }
        try {
            repository.save(new OutboxEvent(event.eventType(), objectMapper.writeValueAsString(event)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize notification event", exception);
        }
    }

    @Transactional
    @Scheduled(fixedDelayString = "${app.outbox.relay-delay-ms:3000}")
    public void relay() {
        repository.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, 50)).forEach(event -> {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setHeader("outboxEventId", event.getId());
            rabbitTemplate.send(
                    RabbitMQConfig.EXCHANGE,
                    event.getRoutingKey(),
                    new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties)
            );
            event.setPublishedAt(LocalDateTime.now());
        });
    }
}
