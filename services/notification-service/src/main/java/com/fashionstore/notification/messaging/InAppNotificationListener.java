package com.fashionstore.notification.messaging;

import com.fashionstore.common.messaging.RabbitTopology;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.notification.InAppNotificationRequested;
import com.fashionstore.notification.config.RabbitMQConfig;
import com.fashionstore.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class InAppNotificationListener {
    static final String CONSUMER_NAME = "notification-in-app-v1";
    private final ObjectMapper objectMapper;
    private final ProcessedMessageService processedMessages;
    private final NotificationService notifications;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.IN_APP_QUEUE)
    public void handle(EventEnvelope<?> envelope, @Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId) {
        if (!EventTypes.NOTIFICATION_IN_APP_REQUESTED.equals(envelope.eventType()))
            throw new IllegalArgumentException("Unsupported eventType on in-app queue: " + envelope.eventType());
        processedMessages.processOnce(messageId, CONSUMER_NAME, () ->
                notifications.create(objectMapper.convertValue(envelope.payload(), InAppNotificationRequested.class)));
    }
}
