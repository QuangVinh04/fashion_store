package com.fashionstore.order.event;

import com.fashionstore.common.messaging.RabbitTopology;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.common.*;
import com.fashionstore.contracts.inventory.event.*;
import com.fashionstore.contracts.payment.event.*;
import com.fashionstore.order.service.OrderCancellationSagaService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class OrderCancellationEventListener {
    public static final String QUEUE = "order.cancellation.workflow";
    private final ObjectMapper mapper;
    private final ProcessedMessageService processed;
    private final OrderCancellationSagaService cancellation;

    @Transactional
    @RabbitListener(queues = QUEUE)
    public void onReply(
            EventEnvelope<?> envelope,
            @Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId) {
        if (envelope.version() != 1 || messageId == null || messageId.isBlank())
            throw new IllegalArgumentException("Invalid cancellation envelope/message id");
        switch (envelope.eventType()) {
            case EventTypes.ORDER_INVENTORY_CANCELLED -> {
                var reply =
                        mapper.convertValue(envelope.payload(), OrderInventoryCancelledEvent.class);
                if (!Objects.equals(envelope.aggregateId(), reply.orderId())
                        || !Objects.equals(envelope.correlationId(), reply.cancellationId())
                        || reply.cancellationId() == null)
                    throw new IllegalArgumentException("Cancellation inventory envelope mismatch");
                processed.processOnce(
                        messageId,
                        "order.cancellation.inventory.v1",
                        () -> cancellation.inventoryCancelled(reply, envelope.correlationId()));
            }
            case EventTypes.ORDER_INVENTORY_CANCELLATION_REJECTED -> {
                var reply =
                        mapper.convertValue(
                                envelope.payload(), OrderInventoryCancellationRejectedEvent.class);
                if (!Objects.equals(envelope.aggregateId(), reply.orderId())
                        || !Objects.equals(envelope.correlationId(), reply.cancellationId())
                        || reply.cancellationId() == null)
                    throw new IllegalArgumentException(
                            "Cancellation inventory rejection envelope mismatch");
                processed.processOnce(
                        messageId,
                        "order.cancellation.inventory-rejected.v1",
                        () -> cancellation.inventoryRejected(reply, envelope.correlationId()));
            }
            case EventTypes.ORDER_FUNDS_REFUND_CREDITED -> {
                var reply =
                        mapper.convertValue(
                                envelope.payload(), OrderFundsRefundCreditedEvent.class);
                if ("PAID_CANCELLATION".equals(reply.reasonType())) {
                    if (!Objects.equals(envelope.aggregateId(), reply.orderId())
                            || !Objects.equals(envelope.correlationId(), reply.sourceId())
                            || reply.sourceId() == null)
                        throw new IllegalArgumentException("Cancellation refund envelope mismatch");
                    processed.processOnce(
                            messageId,
                            "order.cancellation.refund.v1",
                            () -> cancellation.refundCredited(reply, envelope.correlationId()));
                }
            }
            case EventTypes.ORDER_FUNDS_REFUND_REJECTED -> {
                var reply =
                        mapper.convertValue(
                                envelope.payload(), OrderFundsRefundRejectedEvent.class);
                if ("PAID_CANCELLATION".equals(reply.reasonType())) {
                    if (!Objects.equals(envelope.aggregateId(), reply.orderId())
                            || !Objects.equals(envelope.correlationId(), reply.sourceId())
                            || reply.sourceId() == null)
                        throw new IllegalArgumentException(
                                "Cancellation refund rejection envelope mismatch");
                    processed.processOnce(
                            messageId,
                            "order.cancellation.refund-rejected.v1",
                            () -> cancellation.refundRejected(reply, envelope.correlationId()));
                }
            }
            default -> throw new IllegalArgumentException("Unsupported cancellation reply");
        }
    }
}
