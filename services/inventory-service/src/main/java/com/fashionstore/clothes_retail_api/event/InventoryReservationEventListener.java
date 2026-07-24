package com.fashionstore.product.modules.inventory.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.product.config.messaging.RabbitMQNames;
import com.fashionstore.contracts.EventEnvelope;
import com.fashionstore.contracts.inventory.InventoryReservationCommand;
import com.fashionstore.contracts.inventory.InventoryReservationRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class InventoryReservationEventListener {

    private final InventoryReservationService reservationService;
    private final ProcessedMessageService processedMessageService;
    private final ObjectMapper objectMapper;

    @Transactional
    @RabbitListener(queues = RabbitMQNames.INVENTORY_RESERVATION_REQUESTED_QUEUE)
    public void reserve(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        processedMessageService.processOnce(messageId, "inventory-reserve-v1", () ->
                reservationService.reserve(payload(envelope, InventoryReservationRequested.class), envelope.correlationId()));
    }

    @Transactional
    @RabbitListener(queues = RabbitMQNames.INVENTORY_CONFIRMATION_REQUESTED_QUEUE)
    public void confirm(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        processedMessageService.processOnce(messageId, "inventory-confirm-v1", () ->
                reservationService.confirm(
                        payload(envelope, InventoryReservationCommand.class),
                        envelope.correlationId()));
    }

    @Transactional
    @RabbitListener(queues = RabbitMQNames.INVENTORY_RELEASE_REQUESTED_QUEUE)
    public void release(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        processedMessageService.processOnce(messageId, "inventory-release-v1", () ->
                reservationService.release(
                        payload(envelope, InventoryReservationCommand.class),
                        envelope.correlationId()));
    }

    private <T> T payload(EventEnvelope<?> envelope, Class<T> type) {
        return objectMapper.convertValue(envelope.payload(), type);
    }
}
