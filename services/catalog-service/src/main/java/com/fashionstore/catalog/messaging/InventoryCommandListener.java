package com.fashionstore.catalog.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.catalog.config.RabbitMQNames;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.inventory.command.ConfirmInventoryCommand;
import com.fashionstore.contracts.inventory.command.ReleaseInventoryCommand;
import com.fashionstore.contracts.inventory.command.ReservationInventoryCommand;
import com.fashionstore.catalog.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryCommandListener {

    private final InventoryService inventoryService;
    private final ProcessedMessageService processedMessageService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQNames.INVENTORY_RESERVATION_REQUESTED_QUEUE)
    public void onReservationRequested(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId) {
        if (!EventTypes.INVENTORY_RESERVATION_REQUESTED.equals(envelope.eventType())) {
            throw new IllegalArgumentException("Unsupported eventType on reservation queue: " + envelope.eventType());
        }
        processedMessageService.processOnce(
                messageId,
                RabbitMQNames.INVENTORY_RESERVATION_CONSUMER + "-v1",
                () -> handleReservation(envelope));
    }

    @RabbitListener(queues = RabbitMQNames.INVENTORY_CONFIRMATION_REQUESTED_QUEUE)
    public void onConfirmationRequested(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId) {
        if (!EventTypes.INVENTORY_CONFIRMATION_REQUESTED.equals(envelope.eventType())) {
            throw new IllegalArgumentException("Unsupported eventType on confirmation queue: " + envelope.eventType());
        }
        processedMessageService.processOnce(
                messageId,
                RabbitMQNames.INVENTORY_RESERVATION_CONSUMER + "-confirm-v1",
                () -> handleConfirm(envelope));
    }

    @RabbitListener(queues = RabbitMQNames.INVENTORY_RELEASE_REQUESTED_QUEUE)
    public void onReleaseRequested(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId) {
        if (!EventTypes.INVENTORY_RELEASE_REQUESTED.equals(envelope.eventType())) {
            throw new IllegalArgumentException("Unsupported eventType on release queue: " + envelope.eventType());
        }
        processedMessageService.processOnce(
                messageId,
                RabbitMQNames.INVENTORY_RESERVATION_CONSUMER + "-release-v1",
                () -> handleRelease(envelope));
    }

    private void handleReservation(EventEnvelope<?> envelope) {
        ReservationInventoryCommand cmd =
                objectMapper.convertValue(envelope.payload(), ReservationInventoryCommand.class);
        inventoryService.reserveSaga(cmd, envelope.correlationId());
    }

    private void handleConfirm(EventEnvelope<?> envelope) {
        ConfirmInventoryCommand cmd =
                objectMapper.convertValue(envelope.payload(), ConfirmInventoryCommand.class);
        inventoryService.confirmSaga(cmd);
    }

    private void handleRelease(EventEnvelope<?> envelope) {
        ReleaseInventoryCommand cmd =
                objectMapper.convertValue(envelope.payload(), ReleaseInventoryCommand.class);
        inventoryService.releaseSaga(cmd);
    }
}
