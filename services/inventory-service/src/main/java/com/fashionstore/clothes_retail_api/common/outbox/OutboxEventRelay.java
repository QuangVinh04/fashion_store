package com.fashionstore.clothes_retail_api.common.outbox;

import com.fashionstore.common.messaging.outbox.OutboxEventStatus;

import com.fashionstore.clothes_retail_api.config.messaging.RabbitMQNames;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OutboxEventRelay {

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    @Scheduled(fixedDelayString = "${app.outbox.relay-delay-ms:3000}")
    public void relay() {
        repository.findPublishable(LocalDateTime.now(), PageRequest.of(0, 50)).forEach(event -> {
            try {
                MessageProperties properties = new MessageProperties();
                properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                properties.setHeader(RabbitMQNames.OUTBOX_EVENT_ID_HEADER, event.getId());
                rabbitTemplate.send(
                        RabbitMQNames.EXCHANGE,
                        event.getRoutingKey(),
                        new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties)
                );
                event.setStatus(OutboxEventStatus.PUBLISHED);
                event.setPublishedAt(LocalDateTime.now());
                event.setLastError(null);
            } catch (Exception exception) {
                event.setStatus(OutboxEventStatus.FAILED);
                event.setAttempts(event.getAttempts() + 1);
                event.setNextAttemptAt(LocalDateTime.now().plusSeconds(Math.min(300, event.getAttempts() * 10L)));
                event.setLastError(exception.getMessage());
            }
        });
    }
}
