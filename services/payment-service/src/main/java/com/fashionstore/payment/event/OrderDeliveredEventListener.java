package com.fashionstore.payment.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.order.OrderDeliveredEvent;
import com.fashionstore.payment.config.messaging.RabbitMQNames;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.PaymentStatus;
import com.fashionstore.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Settles a COD payment only after the carrier confirms successful delivery. */
@Component
@RequiredArgsConstructor
public class OrderDeliveredEventListener {

    private final PaymentRepository paymentRepository;
    private final ProcessedMessageService processedMessageService;
    private final ObjectMapper objectMapper;

    /** Processes a delivered-order event exactly once. */
    @Transactional
    @RabbitListener(queues = RabbitMQNames.PAYMENT_ORDER_DELIVERED_QUEUE)
    public void handle(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        processedMessageService.processOnce(messageId, "payment-order-delivered-v1", () -> settleCod(envelope));
    }

    private void settleCod(EventEnvelope<?> envelope) {
        OrderDeliveredEvent delivered = objectMapper.convertValue(envelope.payload(), OrderDeliveredEvent.class);
        Payment payment = paymentRepository.findByOrderIdForUpdate(delivered.orderId()).orElse(null);
        if (payment == null
                || payment.getMethod() != PaymentMethod.COD
                || payment.getStatus() != PaymentStatus.COD_PENDING) {
            return;
        }

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(delivered.deliveredAt() != null ? delivered.deliveredAt() : LocalDateTime.now());
        payment.setFailureReason(null);
        paymentRepository.save(payment);
    }
}
