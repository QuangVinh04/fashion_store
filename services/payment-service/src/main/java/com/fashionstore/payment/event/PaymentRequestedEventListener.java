package com.fashionstore.payment.event;

import com.fashionstore.common.messaging.RabbitTopology;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.payment.command.AuthorizePaymentCommand;
import com.fashionstore.contracts.payment.command.CancelPaymentCommand;
import com.fashionstore.contracts.payment.command.RefundPaymentCommand;
import com.fashionstore.payment.config.messaging.RabbitMQNames;
import com.fashionstore.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRequestedEventListener {

    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;

    @RabbitListener(queues = RabbitMQNames.PAYMENT_SAGA_COMMAND_QUEUE)
    public void handle(
            EventEnvelope<?> envelope,
            @Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        if (EventTypes.PAYMENT_REQUESTED.equals(envelope.eventType())) {
            AuthorizePaymentCommand request = objectMapper.convertValue(envelope.payload(), AuthorizePaymentCommand.class);
            paymentService.authorize(request, messageId, envelope.correlationId());
            return;
        }
        if (EventTypes.PAYMENT_CANCELLATION_REQUESTED.equals(envelope.eventType())) {
            CancelPaymentCommand request = objectMapper.convertValue(envelope.payload(), CancelPaymentCommand.class);
            paymentService.cancel(request, messageId, envelope.correlationId());
            return;
        }
        if (EventTypes.PAYMENT_REFUND_REQUESTED.equals(envelope.eventType())) {
            RefundPaymentCommand request = objectMapper.convertValue(envelope.payload(), RefundPaymentCommand.class);
            paymentService.refund(request, messageId, envelope.correlationId());
            return;
        }
        throw new IllegalArgumentException("Unsupported payment saga command " + envelope.eventType());
    }
}
