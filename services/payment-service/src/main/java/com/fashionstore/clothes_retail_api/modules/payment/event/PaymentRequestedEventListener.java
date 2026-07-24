package com.fashionstore.product.modules.payment.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.contracts.EventEnvelope;
import com.fashionstore.contracts.EventTypes;
import com.fashionstore.contracts.payment.PaymentCancellationRequested;
import com.fashionstore.contracts.payment.PaymentCancellationResult;
import com.fashionstore.contracts.payment.PaymentRequested;
import com.fashionstore.contracts.payment.PaymentResult;
import com.fashionstore.product.config.messaging.RabbitMQNames;
import com.fashionstore.product.modules.payment.entity.Payment;
import com.fashionstore.product.modules.payment.entity.PaymentStatus;
import com.fashionstore.product.modules.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PaymentRequestedEventListener {

    private final PaymentRepository paymentRepository;
    private final ProcessedMessageService processedMessageService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @RabbitListener(queues = RabbitMQNames.PAYMENT_SAGA_COMMAND_QUEUE)
    public void handle(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        if (EventTypes.PAYMENT_REQUESTED.equals(envelope.eventType())) {
            processedMessageService.processOnce(messageId, "payment-requested-v2", () ->
                    createPayment(envelope));
            return;
        }
        if (EventTypes.PAYMENT_CANCELLATION_REQUESTED.equals(envelope.eventType())) {
            processedMessageService.processOnce(messageId, "payment-cancellation-v2", () ->
                    cancelPayment(envelope));
            return;
        }
        throw new IllegalArgumentException("Unsupported payment saga command " + envelope.eventType());
    }

    private void createPayment(EventEnvelope<?> envelope) {
        PaymentRequested request = objectMapper.convertValue(envelope.payload(), PaymentRequested.class);
        Payment existing = paymentRepository.findByOrderIdForUpdate(request.orderId()).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == PaymentStatus.COMPLETED) {
                publishCompleted(existing, envelope.correlationId());
            }
            return;
        }

        PaymentMethod method = PaymentMethod.valueOf(request.method());
        Payment payment = Payment.builder()
                .orderId(request.orderId())
                .userId(request.userId())
                .method(method)
                .provider(PaymentProvider.valueOf(request.provider()))
                .amount(request.amount())
                .status(method == PaymentMethod.COD ? PaymentStatus.COMPLETED : PaymentStatus.PENDING)
                .paidAt(method == PaymentMethod.COD ? LocalDateTime.now() : null)
                .build();
        Payment saved = paymentRepository.save(payment);
        if (saved.getStatus() == PaymentStatus.COMPLETED) {
            publishCompleted(saved, envelope.correlationId());
        }
    }

    private void cancelPayment(EventEnvelope<?> envelope) {
        PaymentCancellationRequested request = objectMapper.convertValue(
                envelope.payload(),
                PaymentCancellationRequested.class
        );
        Payment payment = paymentRepository.findByOrderIdForUpdate(request.orderId())
                .orElseThrow(() -> new IllegalStateException(
                        "Payment not created yet for order " + request.orderId()));

        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.CANCELLED);
            payment.setFailureReason(request.reason());
            paymentRepository.save(payment);
            publishCancellationResult(
                    EventTypes.PAYMENT_CANCELLED,
                    payment,
                    request.reason(),
                    envelope.correlationId()
            );
            return;
        }

        if (payment.getStatus() == PaymentStatus.COMPLETED
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            publishCancellationResult(
                    EventTypes.PAYMENT_CANCELLATION_REJECTED,
                    payment,
                    "Payment was already completed",
                    envelope.correlationId()
            );
            return;
        }

        publishCancellationResult(
                EventTypes.PAYMENT_CANCELLED,
                payment,
                payment.getFailureReason(),
                envelope.correlationId()
        );
    }

    private void publishCompleted(Payment payment, String correlationId) {
        eventPublisher.publishEvent(EventEnvelope.v1(
                EventTypes.PAYMENT_COMPLETED,
                payment.getOrderId(),
                correlationId,
                new PaymentResult(payment.getOrderId(), payment.getId(), null)
        ));
    }

    private void publishCancellationResult(
            String eventType,
            Payment payment,
            String reason,
            String correlationId
    ) {
        eventPublisher.publishEvent(EventEnvelope.v1(
                eventType,
                payment.getOrderId(),
                correlationId,
                new PaymentCancellationResult(payment.getOrderId(), payment.getId(), reason)
        ));
    }
}
