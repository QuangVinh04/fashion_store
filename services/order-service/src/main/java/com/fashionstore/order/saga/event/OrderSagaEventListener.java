package com.fashionstore.order.saga.event;

import com.fashionstore.common.messaging.RabbitTopology;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.order.shared.config.messaging.RabbitMQNames;
import com.fashionstore.order.saga.service.OrderSagaService;

import lombok.RequiredArgsConstructor;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** RabbitMQ entry points; business transitions and transactions belong to OrderSagaService. */
@Component
@RequiredArgsConstructor
public class OrderSagaEventListener {

    private final OrderSagaService orderSagaService;

    @RabbitListener(queues = RabbitMQNames.ORDER_INVENTORY_CONFIRMATION_REJECTED_QUEUE)
    public void inventoryConfirmationRejected(
            EventEnvelope<?> envelope,
            @Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId) {
        orderSagaService.inventoryConfirmationRejected(envelope, messageId);
    }

    @RabbitListener(queues = RabbitMQNames.ORDER_INVENTORY_RESERVED_QUEUE)
    public void inventoryReserved(
            EventEnvelope<?> envelope,
            @Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId) {
        orderSagaService.inventoryReserved(envelope, messageId);
    }

    @RabbitListener(queues = RabbitMQNames.ORDER_PAYMENT_INITIATED_QUEUE)
    public void paymentInitiated(
            EventEnvelope<?> envelope,
            @Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId) {
        orderSagaService.paymentInitiated(envelope, messageId);
    }

    @RabbitListener(queues = RabbitMQNames.ORDER_INVENTORY_REJECTED_QUEUE)
    public void inventoryRejected(
            EventEnvelope<?> envelope,
            @Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId) {
        orderSagaService.inventoryRejected(envelope, messageId);
    }

    @RabbitListener(queues = RabbitMQNames.ORDER_PAYMENT_COMPLETED_QUEUE)
    public void paymentCompleted(
            EventEnvelope<?> envelope,
            @Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId) {
        orderSagaService.paymentCompleted(envelope, messageId);
    }

    @RabbitListener(queues = RabbitMQNames.ORDER_PAYMENT_FAILED_QUEUE)
    public void paymentFailed(
            EventEnvelope<?> envelope,
            @Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId) {
        orderSagaService.paymentFailed(envelope, messageId);
    }

    @RabbitListener(queues = RabbitMQNames.ORDER_PAYMENT_CANCELLED_QUEUE)
    public void paymentCancelled(
            EventEnvelope<?> envelope,
            @Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId) {
        orderSagaService.paymentCancelled(envelope, messageId);
    }

    @RabbitListener(queues = RabbitMQNames.ORDER_PAYMENT_CANCELLATION_REJECTED_QUEUE)
    public void paymentCancellationRejected(
            EventEnvelope<?> envelope,
            @Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId) {
        orderSagaService.paymentCancellationRejected(envelope, messageId);
    }

    @RabbitListener(queues = RabbitMQNames.ORDER_INVENTORY_CONFIRMED_QUEUE)
    public void inventoryConfirmed(
            EventEnvelope<?> envelope,
            @Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId) {
        orderSagaService.inventoryConfirmed(envelope, messageId);
    }

    @RabbitListener(queues = RabbitMQNames.ORDER_INVENTORY_RELEASED_QUEUE)
    public void inventoryReleased(
            EventEnvelope<?> envelope,
            @Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId) {
        orderSagaService.inventoryReleased(envelope, messageId);
    }
}
