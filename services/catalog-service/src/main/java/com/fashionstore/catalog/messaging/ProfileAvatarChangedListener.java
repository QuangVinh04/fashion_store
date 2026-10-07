package com.fashionstore.catalog.messaging;

import com.fashionstore.common.messaging.RabbitTopology;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.catalog.config.RabbitMQNames;
import com.fashionstore.catalog.service.MediaFileService;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.identity.event.ProfileAvatarChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileAvatarChangedListener {

    private final MediaFileService mediaFileService;
    private final ProcessedMessageService processedMessageService;
    private final ObjectMapper objectMapper;

    @org.springframework.transaction.annotation.Transactional
    @RabbitListener(queues = RabbitMQNames.PROFILE_AVATAR_CHANGED_QUEUE)
    public void onProfileAvatarChanged(
            EventEnvelope<?> envelope,
            @Header(value = RabbitTopology.OUTBOX_EVENT_ID_HEADER, required = false) String messageId) {
        if (!EventTypes.PROFILE_AVATAR_CHANGED.equals(envelope.eventType())) {
            throw new IllegalArgumentException("Unsupported eventType on profile avatar queue: " + envelope.eventType());
        }

        String deduplicationId = (messageId != null && !messageId.isBlank()) ? messageId : envelope.eventId();

        processedMessageService.processOnce(
                deduplicationId,
                RabbitMQNames.PROFILE_AVATAR_CONSUMER + "-v1",
                () -> handleEvent(envelope)
        );
    }

    private void handleEvent(EventEnvelope<?> envelope) {
        ProfileAvatarChangedEvent event =
                objectMapper.convertValue(envelope.payload(), ProfileAvatarChangedEvent.class);
        mediaFileService.activateIfCurrent(event);
    }
}
