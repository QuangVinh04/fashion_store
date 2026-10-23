package com.fashionstore.payment.payment.event;

import com.fashionstore.common.messaging.RabbitTopology;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.payment.command.AuthorizePaymentCommand;
import com.fashionstore.contracts.payment.command.CancelPaymentCommand;
import com.fashionstore.contracts.payment.command.RefundWalletSagaCommand;
import com.fashionstore.contracts.payment.command.RefundOrderFundsCommand;
import com.fashionstore.payment.shared.config.messaging.RabbitMQNames;
import com.fashionstore.payment.payment.service.PaymentService;
import com.fashionstore.payment.wallet.service.WalletService;
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
    private final WalletService walletService;

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
        if (EventTypes.WALLET_SAGA_COMPENSATION_REQUESTED.equals(envelope.eventType())) {
            RefundWalletSagaCommand command = objectMapper.convertValue(envelope.payload(), RefundWalletSagaCommand.class);
            walletService.compensate(command, messageId, envelope.correlationId());
            return;
        }
        if (EventTypes.ORDER_FUNDS_REFUND_REQUESTED.equals(envelope.eventType())) {
            RefundOrderFundsCommand command = objectMapper.convertValue(envelope.payload(), RefundOrderFundsCommand.class);
            walletService.refundOrderFunds(command, messageId, envelope.correlationId());
            return;
        }
        if (EventTypes.PAYMENT_CANCELLATION_REQUESTED.equals(envelope.eventType())) {
            CancelPaymentCommand request = objectMapper.convertValue(envelope.payload(), CancelPaymentCommand.class);
            paymentService.cancel(request, messageId, envelope.correlationId());
            return;
        }
        log.warn("Ignored payment saga command {}", envelope.eventType());
    }
}
