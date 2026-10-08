package com.fashionstore.payment.event;

import com.fashionstore.common.messaging.RabbitTopology;
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
import com.fashionstore.payment.config.messaging.RabbitMQNames;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import com.fashionstore.payment.service.PaymentService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Nhận lệnh thanh toán, hủy và hoàn tiền từ saga.
 * Khởi tạo payment dùng chung flow ba bước trong PaymentService (DB / provider / DB).
 * Refund giữ flow ba bước riêng: chuẩn bị refund, gọi provider ngoài transaction, ghi kết quả.
 * Marker chống trùng chỉ bảo vệ bước chuẩn bị DB; payment redelivery vẫn kiểm tra URL cần phục hồi.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRequestedEventListener {

    /** Refund đã ghi PENDING ở pha 1, chờ gọi cổng. */
    private record RefundCall(Payment payment, PaymentRefund refund, BigDecimal previouslyRefunded, String correlationId) {
    }

    private record RefundOutcome(PaymentRefundResult result, String error) {
    }

    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final ProcessedMessageService processedMessageService;
    private final ObjectMapper objectMapper;
    private final OutboxService outboxService;
    private final PaymentHandlerRegistry paymentHandlerRegistry;
    /** Mở transaction bằng code (thay cho @Transactional) để chia một message thành nhiều transaction ngắn. */
    private final TransactionTemplate transactionTemplate;
    private final PaymentService paymentService;

    @Transactional(propagation = Propagation.NEVER)
    @RabbitListener(queues = RabbitMQNames.PAYMENT_SAGA_COMMAND_QUEUE)
    public void handle(
            EventEnvelope<?> envelope,
            @Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        if (EventTypes.PAYMENT_REQUESTED.equals(envelope.eventType())) {
            // Chỉ lưu payment và dấu chống trùng trong transaction; provider chạy sau commit.
            transactionTemplate.executeWithoutResult(tx -> processedMessageService.processOnce(
                    messageId, "payment-requested-v2", () -> createPayment(envelope)));
            AuthorizePaymentCommand request = objectMapper.convertValue(envelope.payload(), AuthorizePaymentCommand.class);
            Payment payment = paymentRepository.findByOrderId(request.orderId()).orElse(null);
            if (payment != null && payment.getMethod() != PaymentMethod.COD
                    && (payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.INITIATING
                    || payment.getStatus() == PaymentStatus.INITIATION_UNKNOWN)) {
                // Chạy cả khi message được gửi lại: marker chỉ chống trùng bước lưu DB,
                // còn link được tái sử dụng/đối soát theo merchantReference đã commit.
                paymentService.initiateForOrder(request.orderId(), request.clientIp());
            }
            return;
        }
        if (EventTypes.PAYMENT_CANCELLATION_REQUESTED.equals(envelope.eventType())) {
            // Huỷ không gọi cổng nên một transaction là đủ.
            transactionTemplate.executeWithoutResult(status ->
                    processedMessageService.processOnce(messageId, "payment-cancellation-v2", () ->
                            cancelPayment(envelope)));
            return;
        }
        if (EventTypes.PAYMENT_REFUND_REQUESTED.equals(envelope.eventType())) {
            refund(envelope, messageId);
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
        }
    }

    // ===================== Huỷ =====================

    private void cancelPayment(EventEnvelope<?> envelope) {
        CancelPaymentCommand request = objectMapper.convertValue(
                envelope.payload(),
                CancelPaymentCommand.class
        );
        Payment payment = paymentRepository.findByOrderIdForUpdate(request.orderId())
                .orElse(null);

        if (payment == null) {
            log.warn("Payment không tồn tại cho order {} khi nhận lệnh hủy, gửi PAYMENT_CANCELLED để saga hoàn tất bù trừ", request.orderId());
            outboxService.saveMessage(request.orderId(), EventTypes.PAYMENT_CANCELLED, EventEnvelope.v1(
                    EventTypes.PAYMENT_CANCELLED,
                    request.orderId(),
                    envelope.correlationId(),
                    new PaymentCancelledEvent(request.orderId(), null, request.reason())
            ));
            return;
        }

        if (payment.getStatus() == PaymentStatus.INITIATING || payment.getStatus() == PaymentStatus.INITIATION_UNKNOWN) {
            // Chưa biết provider đã nhận/tạo giao dịch hay chưa: không được chốt hủy rồi bỏ
            // qua tiền về sau đó. Ném lỗi để rollback cả marker processOnce; saga có thể retry
            // lệnh hủy sau khi callback/job đối soát xác định được trạng thái thật.
            throw new com.fashionstore.common.exception.AppException(
                    com.fashionstore.payment.exception.PaymentErrorCode.PAYMENT_INITIATION_IN_PROGRESS);
        }
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

    // ===================== Hoàn tiền =====================

    private void refund(EventEnvelope<?> envelope, String messageId) {
        // Pha 1: transaction ngắn — kiểm tra điều kiện, ghi refund PENDING.
        AtomicReference<RefundCall> call = new AtomicReference<>();
        transactionTemplate.executeWithoutResult(status ->
                processedMessageService.processOnce(messageId, "payment-refund-v1", () ->
                        call.set(prepareRefund(envelope, messageId))));
        if (call.get() == null) {
            return; // message trùng hoặc đã trả lời ngay (từ chối / đã hoàn trước đó)
        }

        // Pha 2: ngoài transaction — gọi API hoàn tiền của cổng.
        RefundOutcome outcome = refundAtGateway(call.get());

        // Pha 3: transaction ngắn — ghi kết quả và reply cho order-service.
        transactionTemplate.executeWithoutResult(status -> applyRefund(call.get(), outcome));
    }

    /**
     * Hoàn tiền là yêu cầu độc lập với saga đặt hàng — không có sagaId, correlationId ở đây là orderId.
     * Chỉ hoàn được khi tiền thật sự đã thu ({@code COMPLETED}); đã hoàn rồi thì trả lại đúng reply cũ
     * để phía order-service (đang chờ reply) không bị kẹt vì tưởng message thất lạc.
     *
     * @return refund cần gọi cổng, hoặc {@code null} nếu đã trả lời xong ngay trong pha 1
     */
    private RefundCall prepareRefund(EventEnvelope<?> envelope, String idempotencyKey) {
        RefundPaymentCommand request = objectMapper.convertValue(envelope.payload(), RefundPaymentCommand.class);
        Payment payment = paymentRepository.findByOrderIdForUpdate(request.orderId()).orElse(null);
        if (payment == null) {
            publishRefundRejected(request.orderId(), request.paymentId(), "PAYMENT_NOT_FOUND",
                    "Không tìm thấy payment cho đơn hàng này", envelope.correlationId());
            return null;
        }

        PaymentRefund existingRefund = paymentRefundRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existingRefund != null) {
            replayRefundResult(existingRefund, envelope.correlationId());
            return null;
        }

        if (request.paymentId() != null && !request.paymentId().equals(payment.getId())) {
            publishRefundRejected(payment.getOrderId(), payment.getId(), "PAYMENT_MISMATCH",
                    "Refund payment id does not belong to this order", envelope.correlationId());
            return null;
        }

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            publishRefunded(payment, request.reason(), envelope.correlationId());
            return null;
        }
        if (payment.getStatus() != PaymentStatus.COMPLETED
                && payment.getStatus() != PaymentStatus.REFUND_FAILED) {
            publishRefundRejected(payment.getOrderId(), payment.getId(), "PAYMENT_NOT_CAPTURED",
                    "Payment đang ở trạng thái " + payment.getStatus() + ", không thể hoàn tiền",
                    envelope.correlationId());
            return null;
        }

        if (payment.getMethod() == PaymentMethod.COD) {
            publishRefundRejected(payment.getOrderId(), payment.getId(), "COD_REFUND_REQUIRES_MANUAL_PAYOUT",
                    "COD refunds require a separate customer payout method", envelope.correlationId());
            return null;
        }

        BigDecimal amount = request.amount();
        BigDecimal refundedAmount = paymentRefundRepository.sumCompletedAmountByPaymentId(payment.getId());
        BigDecimal remainingAmount = payment.getAmount().subtract(refundedAmount);
        if (amount == null || amount.signum() <= 0 || amount.compareTo(remainingAmount) > 0) {
            publishRefundRejected(payment.getOrderId(), payment.getId(), "REFUND_AMOUNT_INVALID",
                    "Refund amount must be positive and not exceed the remaining captured amount",
                    envelope.correlationId());
            return null;
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
        PaymentRefund savedRefund = paymentRefundRepository.save(refund);
        payment.setStatus(PaymentStatus.REFUND_PENDING);
        payment.setFailureReason(null);
        paymentRepository.save(payment);
        return new RefundCall(payment, savedRefund, refundedAmount, envelope.correlationId());
    }

    /** Chạy ngoài transaction. requestId gửi cổng suy ra từ idempotencyKey nên cổng nhận ra lần gọi trùng. */
    private RefundOutcome refundAtGateway(RefundCall call) {
        try {
            return new RefundOutcome(
                    paymentHandlerRegistry.get(call.payment().getProvider()).refund(call.payment(), call.refund()), null);
        } catch (RuntimeException exception) {
            log.error("Hoàn tiền với cổng thất bại cho order {}: {}", call.payment().getOrderId(), exception.getMessage(), exception);
            return new RefundOutcome(null, safeFailureReason(exception));
        }
    }

    private void applyRefund(RefundCall call, RefundOutcome outcome) {
        Payment payment = paymentRepository.findByIdForUpdate(call.payment().getId())
                .orElseThrow(() -> new IllegalStateException("Payment " + call.payment().getId() + " biến mất giữa hai pha"));
        PaymentRefund refund = paymentRefundRepository.findById(call.refund().getId())
                .orElseThrow(() -> new IllegalStateException("Refund " + call.refund().getId() + " biến mất giữa hai pha"));

        if (outcome.error() != null) {
            refund.setStatus(PaymentRefundStatus.FAILED);
            refund.setFailureReason(outcome.error());
            paymentRefundRepository.save(refund);
            payment.setStatus(PaymentStatus.REFUND_FAILED);
            payment.setFailureReason(outcome.error());
            paymentRepository.save(payment);
            publishRefundRejected(payment.getOrderId(), payment.getId(), "PROVIDER_REFUND_FAILED",
                    outcome.error(), call.correlationId());
            return;
        }
        applyRefundResult(payment, refund, outcome.result(), call.previouslyRefunded(), call.correlationId());
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
