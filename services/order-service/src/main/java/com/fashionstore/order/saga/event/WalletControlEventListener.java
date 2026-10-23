package com.fashionstore.order.saga.event;

import com.fashionstore.common.messaging.RabbitTopology;
import com.fashionstore.contracts.common.*;
import com.fashionstore.order.shared.config.messaging.RabbitMQNames;
import com.fashionstore.order.saga.service.OrderSagaService;

import lombok.RequiredArgsConstructor;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** Điểm vào RabbitMQ cho reply hoàn ví; nghiệp vụ và transaction nằm trong OrderSagaService. */
@Component
@RequiredArgsConstructor
public class WalletControlEventListener {
    private final OrderSagaService orderSagaService;

    @RabbitListener(queues = RabbitMQNames.ORDER_WALLET_CONTROL_QUEUE)
    public void handle(
            EventEnvelope<?> envelope,
            @Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId) {
        switch (envelope.eventType()) {
            case EventTypes.WALLET_SAGA_COMPENSATION_COMPLETED ->
                    orderSagaService.walletCompensated(envelope, messageId);
            case EventTypes.WALLET_SAGA_COMPENSATION_REJECTED ->
                    orderSagaService.walletCompensationRejected(envelope, messageId);
            default -> {}
        }
    }
}
