package com.fashionstore.payment.event;

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
import com.fashionstore.contracts.payment.event.PaymentInitiatedEvent;
import com.fashionstore.contracts.payment.event.PaymentRefundRejectedEvent;
import com.fashionstore.contracts.payment.event.PaymentRefundedEvent;
import com.fashionstore.contracts.payment.event.PaymentSuccessEvent;
import com.fashionstore.payment.config.messaging.RabbitMQNames;
import com.fashionstore.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.dto.PaymentRefundResult;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.PaymentRefund;
import com.fashionstore.payment.entity.enumeration.PaymentRefundStatus;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import com.fashionstore.payment.outbox.OutboxService;
import com.fashionstore.payment.repository.PaymentRefundRepository;
import com.fashionstore.payment.repository.PaymentRepository;
import com.fashionstore.payment.service.provider.PaymentHandlerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PaymentRequestedEventListener {

    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final ProcessedMessageService processedMessageService;
    private final ObjectMapper objectMapper;
    private final OutboxService outboxService;
    private final PaymentHandlerRegistry paymentHandlerRegistry;

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
                    refundPayment(envelope, messageId));
            return;
        }
        throw new IllegalArgumentException("Unsupported payment saga command " + envelope.eventType());
    }

    private void createPayment(EventEnvelope<?> envelope) {
        AuthorizePaymentCommand request = objectMapper.convertValue(envelope.payload(), AuthorizePaymentCommand.class);
        Payment existing = paymentRepository.findByOrderIdForUpdate(request.orderId()).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == PaymentStatus.COMPLETED
                    || existing.getStatus() == PaymentStatus.COD_PENDING) {
                publishCompleted(existing, envelope.correlationId());
            } else if (existing.getStatus() == PaymentStatus.PENDING
                    && existing.getMethod() != PaymentMethod.COD) {
                // Saga timeout đã phát lại AUTHORIZE_PAYMENT (PAYMENT_INITIATED lần trước có thể đã thất
                // lạc) — thử khởi tạo lại, cùng merchantReference nên vẫn idempotent phía cổng thanh toán.
                initiateOnlinePayment(existing, request.clientIp(), envelope.correlationId());
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
                .status(method == PaymentMethod.COD ? PaymentStatus.COD_PENDING : PaymentStatus.PENDING)
                .build();
        Payment saved = paymentRepository.save(payment);
        if (saved.getStatus() == PaymentStatus.COD_PENDING) {
            publishCompleted(saved, envelope.correlationId());
        } else {
            initiateOnlinePayment(saved, request.clientIp(), envelope.correlationId());
        }
    }

    /**
     * Khởi tạo giao dịch với cổng ngay khi vừa xin thanh toán, thay vì đợi client gọi
     * {@code POST /payments/{id}/initiate} riêng — bớt 2 vòng round-trip cho frontend. Endpoint initiate
     * cũ vẫn giữ nguyên, dùng khi cần tạo lại link đã hết hạn.
     */
    private void initiateOnlinePayment(Payment payment, String clientIp, String correlationId) {
        if (payment.getMerchantReference() == null) {
            payment.setMerchantReference(payment.getId().replace("-", ""));
        }
        PaymentInitiationResult result = paymentHandlerRegistry.get(payment.getProvider()).initiate(payment, clientIp);
        if (result.getProviderTransactionId() != null) {
            payment.setTransactionId(result.getProviderTransactionId());
        }
        payment.setProviderAmount(result.getProviderAmount());
        payment.setProviderCurrency(result.getProviderCurrency());
        payment.setProviderTransactionDate(result.getProviderTransactionDate());
        Payment saved = paymentRepository.save(payment);

        outboxService.saveMessage(saved.getOrderId(), EventTypes.PAYMENT_INITIATED, EventEnvelope.v1(
                EventTypes.PAYMENT_INITIATED,
                saved.getOrderId(),
                correlationId,
                new PaymentInitiatedEvent(saved.getOrderId(), saved.getId(), result.getPaymentUrl())
        ));
    }

    private void cancelPayment(EventEnvelope<?> envelope) {
        CancelPaymentCommand request = objectMapper.convertValue(
                envelope.payload(),
                CancelPaymentCommand.class
        );
        Payment payment = paymentRepository.findByOrderIdForUpdate(request.orderId())
                .orElseThrow(() -> new IllegalStateException(
                        "Payment not created yet for order " + request.orderId()));

        if (payment.getStatus() == PaymentStatus.PENDING
                || payment.getStatus() == PaymentStatus.COD_PENDING) {
            payment.setStatus(PaymentStatus.CANCELLED);
            payment.setFailureReason(request.reason());
            paymentRepository.save(payment);
            publishCancelled(payment, request.reason(), envelope.correlationId());
            return;
        }

        if (payment.getStatus() == PaymentStatus.COMPLETED
                || payment.getStatus() == PaymentStatus.REFUND_PENDING
                || payment.getStatus() == PaymentStatus.REFUNDED
                || payment.getStatus() == PaymentStatus.REFUND_FAILED) {
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
    private void refundPayment(EventEnvelope<?> envelope, String idempotencyKey) {
        RefundPaymentCommand request = objectMapper.convertValue(envelope.payload(), RefundPaymentCommand.class);
        Payment payment = paymentRepository.findByOrderIdForUpdate(request.orderId()).orElse(null);
        if (payment == null) {
            publishRefundRejected(request.orderId(), request.paymentId(), "PAYMENT_NOT_FOUND",
                    "Không tìm thấy payment cho đơn hàng này", envelope.correlationId());
            return;
        }

        PaymentRefund existingRefund = paymentRefundRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existingRefund != null) {
            replayRefundResult(existingRefund, envelope.correlationId());
            return;
        }

        if (request.paymentId() != null && !request.paymentId().equals(payment.getId())) {
            publishRefundRejected(payment.getOrderId(), payment.getId(), "PAYMENT_MISMATCH",
                    "Refund payment id does not belong to this order", envelope.correlationId());
            return;
        }

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            publishRefunded(payment, request.reason(), envelope.correlationId());
            return;
        }
        if (payment.getStatus() != PaymentStatus.COMPLETED
                && payment.getStatus() != PaymentStatus.REFUND_FAILED) {
            publishRefundRejected(payment.getOrderId(), payment.getId(), "PAYMENT_NOT_CAPTURED",
                    "Payment đang ở trạng thái " + payment.getStatus() + ", không thể hoàn tiền",
                    envelope.correlationId());
            return;
        }

        if (payment.getMethod() == PaymentMethod.COD) {
            publishRefundRejected(payment.getOrderId(), payment.getId(), "COD_REFUND_REQUIRES_MANUAL_PAYOUT",
                    "COD refunds require a separate customer payout method", envelope.correlationId());
            return;
        }

        BigDecimal amount = request.amount();
        BigDecimal refundedAmount = paymentRefundRepository.sumCompletedAmountByPaymentId(payment.getId());
        BigDecimal remainingAmount = payment.getAmount().subtract(refundedAmount);
        if (amount == null || amount.signum() <= 0 || amount.compareTo(remainingAmount) > 0) {
            publishRefundRejected(payment.getOrderId(), payment.getId(), "REFUND_AMOUNT_INVALID",
                    "Refund amount must be positive and not exceed the remaining captured amount",
                    envelope.correlationId());
            return;
        }

        PaymentRefund refund = PaymentRefund.builder()
                .payment(payment)
                .orderId(payment.getOrderId())
                .amount(amount)
                .provider(payment.getProvider())
                .status(PaymentRefundStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .reason(request.reason())
                .build();
        paymentRefundRepository.save(refund);
        payment.setStatus(PaymentStatus.REFUND_PENDING);
        payment.setFailureReason(null);
        paymentRepository.save(payment);

        try {
            PaymentRefundResult result = paymentHandlerRegistry
                    .get(payment.getProvider())
                    .refund(payment, refund);
            applyRefundResult(payment, refund, result, refundedAmount, envelope.correlationId());
        } catch (RuntimeException exception) {
            String failureReason = safeFailureReason(exception);
            refund.setStatus(PaymentRefundStatus.FAILED);
            refund.setFailureReason(failureReason);
            paymentRefundRepository.save(refund);
            payment.setStatus(PaymentStatus.REFUND_FAILED);
            payment.setFailureReason(failureReason);
            paymentRepository.save(payment);
            publishRefundRejected(payment.getOrderId(), payment.getId(), "PROVIDER_REFUND_FAILED",
                    failureReason, envelope.correlationId());
        }
    }

    private void applyRefundResult(
            Payment payment,
            PaymentRefund refund,
            PaymentRefundResult result,
            BigDecimal previouslyRefunded,
            String correlationId
    ) {
        refund.setProviderRefundId(result.providerRefundId());
        refund.setStatus(result.status());
        refund.setFailureReason(result.failureReason());

        if (result.status() == PaymentRefundStatus.COMPLETED) {
            refund.setCompletedAt(LocalDateTime.now());
            BigDecimal totalRefunded = previouslyRefunded.add(refund.getAmount());
            payment.setStatus(totalRefunded.compareTo(payment.getAmount()) >= 0
                    ? PaymentStatus.REFUNDED
                    : PaymentStatus.COMPLETED);
            payment.setFailureReason(null);
            paymentRefundRepository.save(refund);
            paymentRepository.save(payment);
            publishRefunded(payment, refund.getReason(), correlationId);
            return;
        }

        if (result.status() == PaymentRefundStatus.FAILED) {
            payment.setStatus(PaymentStatus.REFUND_FAILED);
            payment.setFailureReason(result.failureReason());
            paymentRefundRepository.save(refund);
            paymentRepository.save(payment);
            publishRefundRejected(payment.getOrderId(), payment.getId(), "PROVIDER_REFUND_FAILED",
                    result.failureReason(), correlationId);
            return;
        }

        paymentRefundRepository.save(refund);
        paymentRepository.save(payment);
    }

    private void replayRefundResult(PaymentRefund refund, String correlationId) {
        if (refund.getStatus() == PaymentRefundStatus.COMPLETED) {
            publishRefunded(refund.getPayment(), refund.getReason(), correlationId);
        } else if (refund.getStatus() == PaymentRefundStatus.FAILED) {
            publishRefundRejected(refund.getOrderId(), refund.getPayment().getId(), "PROVIDER_REFUND_FAILED",
                    refund.getFailureReason(), correlationId);
        }
    }

    private String safeFailureReason(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Payment provider rejected the refund";
        }
        return message.substring(0, Math.min(message.length(), 500));
    }

    private void publishRefunded(Payment payment, String reason, String correlationId) {
        outboxService.saveMessage(payment.getOrderId(), EventTypes.PAYMENT_REFUNDED, EventEnvelope.v1(
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
        outboxService.saveMessage(orderId, EventTypes.PAYMENT_REFUND_REJECTED, EventEnvelope.v1(
                EventTypes.PAYMENT_REFUND_REJECTED,
                orderId,
                correlationId,
                new PaymentRefundRejectedEvent(orderId, paymentId, failureCode, failureMessage)
        ));
    }

    private void publishCompleted(Payment payment, String correlationId) {
        outboxService.saveMessage(payment.getOrderId(), EventTypes.PAYMENT_COMPLETED, EventEnvelope.v1(
                EventTypes.PAYMENT_COMPLETED,
                payment.getOrderId(),
                correlationId,
                new PaymentSuccessEvent(payment.getOrderId(), payment.getId())
        ));
    }

    private void publishCancelled(Payment payment, String reason, String correlationId) {
        outboxService.saveMessage(payment.getOrderId(), EventTypes.PAYMENT_CANCELLED, EventEnvelope.v1(
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
        outboxService.saveMessage(payment.getOrderId(), EventTypes.PAYMENT_CANCELLATION_REJECTED, EventEnvelope.v1(
                EventTypes.PAYMENT_CANCELLATION_REJECTED,
                payment.getOrderId(),
                correlationId,
                new PaymentCancellationRejectedEvent(payment.getOrderId(), failureCode, failureMessage)
        ));
    }
}
