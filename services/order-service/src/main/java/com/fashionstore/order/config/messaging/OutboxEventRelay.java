package com.fashionstore.order.config.messaging;

import com.fashionstore.common.messaging.outbox.OutboxEventStatus;
import com.fashionstore.order.entity.OutboxEvent;
import com.fashionstore.order.repository.OutboxEventRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OutboxEventRelay {

    OutboxEventRepository outboxEventRepository;
    RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelayString = "${app.outbox.relay-delay-ms:5000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> events = outboxEventRepository.findPublishable(LocalDateTime.now(), PageRequest.of(0, 20));
        for (OutboxEvent event : events) {
            try {
                MessageProperties properties = new MessageProperties();
                properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                properties.setHeader(RabbitMQNames.OUTBOX_EVENT_ID_HEADER, event.getId());
                rabbitTemplate.send(RabbitMQNames.EXCHANGE, event.getRoutingKey(), new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties));
                event.setStatus(OutboxEventStatus.PUBLISHED);
                event.setPublishedAt(LocalDateTime.now());
                event.setLastError(null);
            } catch (Exception ex) {
                event.setStatus(OutboxEventStatus.FAILED);
                event.setAttempts(event.getAttempts() + 1);
                event.setNextAttemptAt(LocalDateTime.now().plusSeconds(30));
                event.setLastError(ex.getMessage());
            }
        }
        outboxEventRepository.saveAll(events);
    }
}
