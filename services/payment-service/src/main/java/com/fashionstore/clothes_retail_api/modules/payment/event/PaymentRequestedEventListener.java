package com.fashionstore.product.modules.payment.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.payment.command.AuthorizePaymentCommand;
import com.fashionstore.contracts.payment.command.CancelPaymentCommand;
import com.fashionstore.contracts.payment.command.RefundPaymentCommand;
import com.fashionstore.contracts.payment.event.PaymentCancellationRejectedEvent;
import com.fashionstore.contracts.payment.event.PaymentCancelledEvent;
import com.fashionstore.contracts.payment.event.PaymentRefundRejectedEvent;
import com.fashionstore.contracts.payment.event.PaymentRefundedEvent;
import com.fashionstore.contracts.payment.event.PaymentSuccessEvent;
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
        if (EventTypes.PAYMENT_REFUND_REQUESTED.equals(envelope.eventType())) {
            processedMessageService.processOnce(messageId, "payment-refund-v1", () ->
                    refundPayment(envelope));
            return;
        }
        throw new IllegalArgumentException("Unsupported payment saga command " + envelope.eventType());
    }

    private void createPayment(EventEnvelope<?> envelope) {
        AuthorizePaymentCommand request = objectMapper.convertValue(envelope.payload(), AuthorizePaymentCommand.class);
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
                .sagaId(envelope.correlationId())
                .method(method)
                .provider(PaymentProvider.valueOf(request.provider()))
                .amount(request.amount())
                .currency(request.currency())
                .status(method == PaymentMethod.COD ? PaymentStatus.COMPLETED : PaymentStatus.PENDING)
                .paidAt(method == PaymentMethod.COD ? LocalDateTime.now() : null)
                .build();
        Payment saved = paymentRepository.save(payment);
        if (saved.getStatus() == PaymentStatus.COMPLETED) {
            publishCompleted(saved, envelope.correlationId());
        }
    }

    private void cancelPayment(EventEnvelope<?> envelope) {
        CancelPaymentCommand request = objectMapper.convertValue(
                envelope.payload(),
                CancelPaymentCommand.class
        );
        Payment payment = paymentRepository.findByOrderIdForUpdate(request.orderId())
                .orElseThrow(() -> new IllegalStateException(
                        "Payment not created yet for order " + request.orderId()));

        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.CANCELLED);
            payment.setFailureReason(request.reason());
            paymentRepository.save(payment);
            publishCancelled(payment, request.reason(), envelope.correlationId());
            return;
        }

        if (payment.getStatus() == PaymentStatus.COMPLETED
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            // Tiền đã thu: không thể vừa giữ tiền vừa hủy đơn, saga sẽ tự đi tiếp thay vì bù trừ.
            publishCancellationRejected(
                    payment,
                    "PAYMENT_ALREADY_CAPTURED",
                    "Payment was already completed",
                    envelope.correlationId()
            );
            return;
        }

        publishCancelled(payment, payment.getFailureReason(), envelope.correlationId());
    }

    /**
     * Hoàn tiền là yêu cầu độc lập với saga đặt hàng — không có sagaId, correlationId ở đây là orderId.
     * Chỉ hoàn được khi tiền thật sự đã thu ({@code COMPLETED}); đã hoàn rồi thì trả lại đúng reply cũ
     * để phía order-service (đang chờ reply) không bị kẹt vì tưởng message thất lạc.
     */
    private void refundPayment(EventEnvelope<?> envelope) {
        RefundPaymentCommand request = objectMapper.convertValue(envelope.payload(), RefundPaymentCommand.class);
        Payment payment = paymentRepository.findByOrderIdForUpdate(request.orderId()).orElse(null);
        if (payment == null) {
            publishRefundRejected(request.orderId(), request.paymentId(), "PAYMENT_NOT_FOUND",
                    "Không tìm thấy payment cho đơn hàng này", envelope.correlationId());
            return;
        }

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            publishRefunded(payment, request.reason(), envelope.correlationId());
            return;
        }
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            publishRefundRejected(payment.getOrderId(), payment.getId(), "PAYMENT_NOT_CAPTURED",
                    "Payment đang ở trạng thái " + payment.getStatus() + ", không thể hoàn tiền",
                    envelope.correlationId());
            return;
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setFailureReason(request.reason());
        paymentRepository.save(payment);
        publishRefunded(payment, request.reason(), envelope.correlationId());
    }

    private void publishRefunded(Payment payment, String reason, String correlationId) {
        eventPublisher.publishEvent(EventEnvelope.v1(
                EventTypes.PAYMENT_REFUNDED,
                payment.getOrderId(),
                correlationId,
                new PaymentRefundedEvent(payment.getOrderId(), payment.getId(), reason)
        ));
    }

    private void publishRefundRejected(
            String orderId,
            String paymentId,
            String failureCode,
            String failureMessage,
            String correlationId
    ) {
        eventPublisher.publishEvent(EventEnvelope.v1(
                EventTypes.PAYMENT_REFUND_REJECTED,
                orderId,
                correlationId,
                new PaymentRefundRejectedEvent(orderId, paymentId, failureCode, failureMessage)
        ));
    }

    private void publishCompleted(Payment payment, String correlationId) {
        eventPublisher.publishEvent(EventEnvelope.v1(
                EventTypes.PAYMENT_COMPLETED,
                payment.getOrderId(),
                correlationId,
                new PaymentSuccessEvent(payment.getOrderId(), payment.getId())
        ));
    }

    private void publishCancelled(Payment payment, String reason, String correlationId) {
        eventPublisher.publishEvent(EventEnvelope.v1(
                EventTypes.PAYMENT_CANCELLED,
                payment.getOrderId(),
                correlationId,
                new PaymentCancelledEvent(payment.getOrderId(), payment.getId(), reason)
        ));
    }

    private void publishCancellationRejected(
            Payment payment,
            String failureCode,
            String failureMessage,
            String correlationId
    ) {
        eventPublisher.publishEvent(EventEnvelope.v1(
                EventTypes.PAYMENT_CANCELLATION_REJECTED,
                payment.getOrderId(),
                correlationId,
                new PaymentCancellationRejectedEvent(payment.getOrderId(), failureCode, failureMessage)
        ));
    }
}
