package com.fashionstore.payment.service;

import com.fashionstore.common.exception.AppException;
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
import com.fashionstore.contracts.payment.event.PaymentInitiatedEvent;
import com.fashionstore.payment.dto.*;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.PaymentRefund;
import com.fashionstore.payment.entity.enumeration.PaymentRefundStatus;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import com.fashionstore.payment.exception.PaymentErrorCode;
import com.fashionstore.payment.outbox.OutboxService;
import com.fashionstore.payment.repository.PaymentRefundRepository;
import com.fashionstore.payment.repository.PaymentRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentDbService {

    PaymentRepository paymentRepository;
    PaymentRefundRepository paymentRefundRepository;
    ProcessedMessageService processedMessageService;
    OutboxService outboxService;
    PaymentStateService paymentStateService;

    @Transactional
    public Payment createPaymentOnce(String messageId, AuthorizePaymentCommand request, String correlationId) {
        AtomicReference<Payment> paymentRef = new AtomicReference<>();
        processedMessageService.processOnce(messageId, "payment-requested-v2", () -> {
            Payment existing = paymentRepository.findByOrderIdForUpdate(request.orderId()).orElse(null);
            if (existing != null) {
                if (existing.getStatus() == PaymentStatus.COMPLETED || existing.getStatus() == PaymentStatus.COD_PENDING) {
                    outboxService.saveMessage(existing.getOrderId(), EventTypes.PAYMENT_COMPLETED, EventEnvelope.v1(
                        EventTypes.PAYMENT_COMPLETED, existing.getOrderId(), correlationId,
                        new PaymentSuccessEvent(existing.getOrderId(), existing.getId())
                ));
                }
                paymentRef.set(existing);
                return;
            }

            PaymentMethod method = PaymentMethod.valueOf(request.method());
            Payment payment = Payment.builder()
                    .orderId(request.orderId())
                    .userId(request.userId())
                    .sagaId(correlationId)
                    .method(method)
                    .provider(PaymentProvider.valueOf(request.provider()))
                    .amount(request.amount())
                    .currency(request.currency())
                    .status(method == PaymentMethod.COD ? PaymentStatus.COD_PENDING : PaymentStatus.PENDING)
                    .build();
            Payment saved = paymentRepository.save(payment);
            if (saved.getStatus() == PaymentStatus.COD_PENDING) {
                outboxService.saveMessage(saved.getOrderId(), EventTypes.PAYMENT_COMPLETED, EventEnvelope.v1(
                        EventTypes.PAYMENT_COMPLETED, saved.getOrderId(), correlationId,
                        new PaymentSuccessEvent(saved.getOrderId(), saved.getId())
                ));
            }
            paymentRef.set(saved);
        });
        
        // If it was skipped by processedMessageService, we still need to fetch it to return for re-initiation
        if (paymentRef.get() == null) {
            return paymentRepository.findByOrderId(request.orderId()).orElse(null);
        }
        return paymentRef.get();
    }

    @Transactional
    public void cancelPaymentOnce(String messageId, CancelPaymentCommand request, String correlationId) {
        processedMessageService.processOnce(messageId, "payment-cancellation-v2", () -> {
            Payment payment = paymentRepository.findByOrderIdForUpdate(request.orderId()).orElse(null);

            if (payment == null) {
                log.warn("Payment không tồn tại cho order {} khi nhận lệnh hủy, gửi PAYMENT_CANCELLED để saga hoàn tất bù trừ", request.orderId());
                outboxService.saveMessage(request.orderId(), EventTypes.PAYMENT_CANCELLED, EventEnvelope.v1(
                        EventTypes.PAYMENT_CANCELLED, request.orderId(), correlationId,
                        new PaymentCancelledEvent(request.orderId(), null, request.reason())
                ));
                return;
            }

            if (payment.getStatus() == PaymentStatus.INITIATING || payment.getStatus() == PaymentStatus.INITIATION_UNKNOWN) {
                throw new AppException(PaymentErrorCode.PAYMENT_INITIATION_IN_PROGRESS);
            }
            if (payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.COD_PENDING) {
                payment.setStatus(PaymentStatus.CANCELLED);
                payment.setFailureReason(request.reason());
                paymentRepository.save(payment);
                outboxService.saveMessage(payment.getOrderId(), EventTypes.PAYMENT_CANCELLED, EventEnvelope.v1(
                        EventTypes.PAYMENT_CANCELLED, payment.getOrderId(), correlationId,
                        new PaymentCancelledEvent(payment.getOrderId(), payment.getId(), request.reason())
                ));
                return;
            }

            if (payment.getStatus() == PaymentStatus.COMPLETED || payment.getStatus() == PaymentStatus.REFUND_PENDING
                    || payment.getStatus() == PaymentStatus.REFUNDED || payment.getStatus() == PaymentStatus.REFUND_FAILED) {
                outboxService.saveMessage(payment.getOrderId(), EventTypes.PAYMENT_CANCELLATION_REJECTED, EventEnvelope.v1(
                        EventTypes.PAYMENT_CANCELLATION_REJECTED, payment.getOrderId(), correlationId,
                        new PaymentCancellationRejectedEvent(payment.getOrderId(), "PAYMENT_ALREADY_CAPTURED", "Payment was already completed")
                ));
                return;
            }

            outboxService.saveMessage(payment.getOrderId(), EventTypes.PAYMENT_CANCELLED, EventEnvelope.v1(
                        EventTypes.PAYMENT_CANCELLED, payment.getOrderId(), correlationId,
                        new PaymentCancelledEvent(payment.getOrderId(), payment.getId(), payment.getFailureReason())
                ));
        });
    }

    public record RefundCall(Payment payment, PaymentRefund refund, BigDecimal previouslyRefunded, String correlationId) {}

    @Transactional
    public RefundCall prepareRefundOnce(String messageId, RefundPaymentCommand request, String correlationId) {
        AtomicReference<RefundCall> callRef = new AtomicReference<>();
        processedMessageService.processOnce(messageId, "payment-refund-v1", () -> {
            Payment payment = paymentRepository.findByOrderIdForUpdate(request.orderId()).orElse(null);
            if (payment == null) {
                outboxService.saveMessage(request.orderId(), EventTypes.PAYMENT_REFUND_REJECTED, EventEnvelope.v1(
                        EventTypes.PAYMENT_REFUND_REJECTED, request.orderId(), correlationId,
                        new PaymentRefundRejectedEvent(request.orderId(), request.paymentId(), "PAYMENT_NOT_FOUND", "Không tìm thấy payment")
                ));
                return;
            }

            PaymentRefund existingRefund = paymentRefundRepository.findByIdempotencyKey(messageId).orElse(null);
            if (existingRefund != null) {
                replayRefundResult(existingRefund, correlationId);
                return;
            }

            if (request.paymentId() != null && !request.paymentId().equals(payment.getId())) {
                outboxService.saveMessage(payment.getOrderId(), EventTypes.PAYMENT_REFUND_REJECTED, EventEnvelope.v1(
                        EventTypes.PAYMENT_REFUND_REJECTED, payment.getOrderId(), correlationId,
                        new PaymentRefundRejectedEvent(payment.getOrderId(), payment.getId(), "PAYMENT_MISMATCH", "Refund payment id does not belong to this order")
                ));
                return;
            }

            if (payment.getStatus() == PaymentStatus.REFUNDED) {
                outboxService.saveMessage(payment.getOrderId(), EventTypes.PAYMENT_REFUNDED, EventEnvelope.v1(
                        EventTypes.PAYMENT_REFUNDED, payment.getOrderId(), correlationId,
                        new PaymentRefundedEvent(payment.getOrderId(), payment.getId(), request.reason())
                ));
                return;
            }
            if (payment.getStatus() != PaymentStatus.COMPLETED && payment.getStatus() != PaymentStatus.REFUND_FAILED) {
                outboxService.saveMessage(payment.getOrderId(), EventTypes.PAYMENT_REFUND_REJECTED, EventEnvelope.v1(
                        EventTypes.PAYMENT_REFUND_REJECTED, payment.getOrderId(), correlationId,
                        new PaymentRefundRejectedEvent(payment.getOrderId(), payment.getId(), "PAYMENT_NOT_CAPTURED", "Payment đang ở trạng thái " + payment.getStatus())
                ));
                return;
            }

            if (payment.getMethod() == PaymentMethod.COD) {
                outboxService.saveMessage(payment.getOrderId(), EventTypes.PAYMENT_REFUND_REJECTED, EventEnvelope.v1(
                        EventTypes.PAYMENT_REFUND_REJECTED, payment.getOrderId(), correlationId,
                        new PaymentRefundRejectedEvent(payment.getOrderId(), payment.getId(), "COD_REFUND_REQUIRES_MANUAL_PAYOUT", "COD refunds require a separate customer payout method")
                ));
                return;
            }

            BigDecimal amount = request.amount();
            BigDecimal refundedAmount = paymentRefundRepository.sumCompletedAmountByPaymentId(payment.getId());
            BigDecimal remainingAmount = payment.getAmount().subtract(refundedAmount);
            if (amount == null || amount.signum() <= 0 || amount.compareTo(remainingAmount) > 0) {
                outboxService.saveMessage(payment.getOrderId(), EventTypes.PAYMENT_REFUND_REJECTED, EventEnvelope.v1(
                        EventTypes.PAYMENT_REFUND_REJECTED, payment.getOrderId(), correlationId,
                        new PaymentRefundRejectedEvent(payment.getOrderId(), payment.getId(), "REFUND_AMOUNT_INVALID", "Refund amount must be positive and not exceed the remaining captured amount")
                ));
                return;
            }

            PaymentRefund refund = PaymentRefund.builder()
                    .payment(payment)
                    .orderId(payment.getOrderId())
                    .amount(amount)
                    .provider(payment.getProvider())
                    .status(PaymentRefundStatus.PENDING)
                    .idempotencyKey(messageId)
                    .reason(request.reason())
                    .build();
            PaymentRefund savedRefund = paymentRefundRepository.save(refund);
            payment.setStatus(PaymentStatus.REFUND_PENDING);
            payment.setFailureReason(null);
            paymentRepository.save(payment);
            callRef.set(new RefundCall(payment, savedRefund, refundedAmount, correlationId));
        });
        return callRef.get();
    }

    @Transactional
    public void applyRefund(RefundCall call, PaymentRefundResult result, String error) {
        Payment payment = paymentRepository.findByIdForUpdate(call.payment().getId())
                .orElseThrow(() -> new IllegalStateException("Payment " + call.payment().getId() + " biến mất giữa hai pha"));
        PaymentRefund refund = paymentRefundRepository.findById(call.refund().getId())
                .orElseThrow(() -> new IllegalStateException("Refund " + call.refund().getId() + " biến mất giữa hai pha"));

        if (error != null) {
            refund.setStatus(PaymentRefundStatus.FAILED);
            refund.setFailureReason(error);
            paymentRefundRepository.save(refund);
            payment.setStatus(PaymentStatus.REFUND_FAILED);
            payment.setFailureReason(error);
            paymentRepository.save(payment);
            outboxService.saveMessage(payment.getOrderId(), EventTypes.PAYMENT_REFUND_REJECTED, EventEnvelope.v1(
                        EventTypes.PAYMENT_REFUND_REJECTED, payment.getOrderId(), call.correlationId(),
                        new PaymentRefundRejectedEvent(payment.getOrderId(), payment.getId(), "PROVIDER_REFUND_FAILED", error)
                ));
            return;
        }

        refund.setProviderRefundId(result.providerRefundId());
        refund.setStatus(result.status());
        refund.setFailureReason(result.failureReason());

        if (result.status() == PaymentRefundStatus.COMPLETED) {
            refund.setCompletedAt(LocalDateTime.now());
            BigDecimal totalRefunded = call.previouslyRefunded().add(refund.getAmount());
            payment.setStatus(totalRefunded.compareTo(payment.getAmount()) >= 0 ? PaymentStatus.REFUNDED : PaymentStatus.COMPLETED);
            payment.setFailureReason(null);
            paymentRefundRepository.save(refund);
            paymentRepository.save(payment);
            outboxService.saveMessage(payment.getOrderId(), EventTypes.PAYMENT_REFUNDED, EventEnvelope.v1(
                        EventTypes.PAYMENT_REFUNDED, payment.getOrderId(), call.correlationId(),
                        new PaymentRefundedEvent(payment.getOrderId(), payment.getId(), refund.getReason())
                ));
            return;
        }

        if (result.status() == PaymentRefundStatus.FAILED) {
            payment.setStatus(PaymentStatus.REFUND_FAILED);
            payment.setFailureReason(result.failureReason());
            paymentRefundRepository.save(refund);
            paymentRepository.save(payment);
            outboxService.saveMessage(payment.getOrderId(), EventTypes.PAYMENT_REFUND_REJECTED, EventEnvelope.v1(
                        EventTypes.PAYMENT_REFUND_REJECTED, payment.getOrderId(), call.correlationId(),
                        new PaymentRefundRejectedEvent(payment.getOrderId(), payment.getId(), "PROVIDER_REFUND_FAILED", result.failureReason())
                ));
            return;
        }

        paymentRefundRepository.save(refund);
        paymentRepository.save(payment);
    }

    @Transactional
    public CallbackProcessResult applyVerifiedCallbackResult(PaymentProvider provider, PaymentCallbackResult result) {
        Payment payment = result.getMerchantReference() == null ? null : paymentRepository.findByMerchantReference(result.getMerchantReference())
                .or(() -> paymentRepository.findByTransactionIdForUpdate(result.getMerchantReference()))
                .orElse(null);
        if (payment == null || payment.getProvider() != provider) {
            return CallbackProcessResult.of(CallbackOutcome.PAYMENT_NOT_FOUND);
        }
        if (result.getStatus() == PaymentStatus.COMPLETED && !paymentStateService.isProviderAmountValid(payment, result)) {
            return CallbackProcessResult.of(CallbackOutcome.AMOUNT_INVALID);
        }
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.INITIATING
                && payment.getStatus() != PaymentStatus.INITIATION_UNKNOWN) {
            return CallbackProcessResult.of(CallbackOutcome.ALREADY_PROCESSED);
        }
        if (result.getStatus() == PaymentStatus.PENDING) {
            return CallbackProcessResult.of(CallbackOutcome.PENDING);
        }
        return new CallbackProcessResult(CallbackOutcome.APPLIED, paymentStateService.applyResult(payment, result));
    }


    @Transactional(readOnly = true)
    public java.util.List<Payment> findForReconciliation(java.util.List<PaymentStatus> openStatuses, LocalDateTime dueBefore, org.springframework.data.domain.Pageable pageable) {
        return paymentRepository.findForReconciliation(openStatuses, dueBefore, pageable);
    }

    @Transactional
    public Payment lockAndPrepareForReconciliation(String paymentId, java.util.List<PaymentStatus> openStatuses, LocalDateTime dueBefore) {
        Payment current = paymentRepository.findByIdForUpdate(paymentId).orElse(null);
        if (current == null || !openStatuses.contains(current.getStatus())
                || (current.getLastReconciledAt() != null && !current.getLastReconciledAt().isBefore(dueBefore))) {
            return null;
        }
        if (current.getStatus() == PaymentStatus.INITIATING && current.getInitiationStartedAt() != null
                && current.getInitiationStartedAt().isAfter(LocalDateTime.now().minusMinutes(2))) {
            return null; // Request khởi tạo còn hoạt động, chưa nhận quyền phục hồi.
        }
        current.setLastReconciledAt(LocalDateTime.now());
        return paymentRepository.save(current);
    }

    @Transactional
    public Payment findByIdForUpdate(String paymentId) {
        return paymentRepository.findByIdForUpdate(paymentId).orElseThrow();
    }
    
    @Transactional
    public Payment initiatePhaseOne(String paymentId, String clientIp, String requiredUserId, String token) {
        Payment current = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        if (requiredUserId != null && !requiredUserId.equals(current.getUserId())) {
            throw new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
        if (current.getStatus() != PaymentStatus.PENDING && current.getStatus() != PaymentStatus.INITIATING
                && current.getStatus() != PaymentStatus.INITIATION_UNKNOWN) {
            throw new AppException(PaymentErrorCode.PAYMENT_STATUS_INVALID);
        }
        if (current.getPaymentUrl() != null) {
            // Phát lại URL cho saga nếu reply trước bị thất lạc; consumer đã chống trùng.
            outboxService.saveMessage(current.getOrderId(), EventTypes.PAYMENT_INITIATED, EventEnvelope.v1(
                    EventTypes.PAYMENT_INITIATED, current.getOrderId(),
                    current.getSagaId() == null ? current.getOrderId() : current.getSagaId(),
                    new PaymentInitiatedEvent(current.getOrderId(), current.getId(), current.getPaymentUrl())));
            return current;
        }
        if (current.getStatus() == PaymentStatus.INITIATING && current.getInitiationStartedAt() != null
                && current.getInitiationStartedAt().isAfter(LocalDateTime.now().minusMinutes(2))) {
            throw new AppException(PaymentErrorCode.PAYMENT_INITIATION_IN_PROGRESS);
        }
        if (current.getMerchantReference() == null) {
            String reference = current.getId().replace("-", "");
            current.setMerchantReference(current.getProvider() == PaymentProvider.PAYOS
                    ? String.valueOf(Long.parseLong(reference.substring(0, 13), 16)) : reference);
        }
        current.setProviderAmount(current.getAmount());
        current.setProviderCurrency(current.getCurrency());
        if (current.getProviderTransactionDate() == null) {
            current.setProviderTransactionDate(LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        }
        current.setClientIp(clientIp);
        current.setStatus(PaymentStatus.INITIATING);
        current.setInitiationToken(token);
        current.setInitiationStartedAt(LocalDateTime.now());
        current.setInitiationAttempts(current.getInitiationAttempts() + 1);
        return paymentRepository.save(current);
    }

    @Transactional
    public void initiateHandleException(String paymentId, String token) {
        Payment current = paymentRepository.findByIdForUpdate(paymentId).orElseThrow();
        if (current.getStatus() == PaymentStatus.INITIATING
                && java.util.Objects.equals(token, current.getInitiationToken())) {
            // Timeout không chứng minh provider thất bại. Giữ định danh để đối soát/retry.
            current.setStatus(PaymentStatus.INITIATION_UNKNOWN);
            current.setFailureReason("Chưa xác định kết quả khởi tạo; cần tra cứu provider");
            paymentRepository.save(current);
        }
    }

    @Transactional
    public void initiatePhaseTwo(String paymentId, String token, PaymentInitiationResult result) {
        Payment current = paymentRepository.findByIdForUpdate(paymentId).orElseThrow();
        if (!java.util.Objects.equals(token, current.getInitiationToken())) {
            return; // Kết quả thuộc worker cũ, không ghi đè lần phục hồi đang chạy.
        }
        current.setPaymentUrl(result.getPaymentUrl());
        if (current.getStatus() == PaymentStatus.INITIATING) {
            current.setStatus(PaymentStatus.PENDING);
            current.setTransactionId(result.getProviderTransactionId());
            current.setProviderAmount(result.getProviderAmount());
            current.setProviderCurrency(result.getProviderCurrency());
            current.setFailureReason(null);
            outboxService.saveMessage(current.getOrderId(), EventTypes.PAYMENT_INITIATED, EventEnvelope.v1(
                    EventTypes.PAYMENT_INITIATED, current.getOrderId(),
                    current.getSagaId() == null ? current.getOrderId() : current.getSagaId(),
                    new PaymentInitiatedEvent(current.getOrderId(), current.getId(), result.getPaymentUrl())));
        }
        paymentRepository.save(current);
    }











    private void replayRefundResult(PaymentRefund refund, String correlationId) {
        if (refund.getStatus() == PaymentRefundStatus.COMPLETED) {
            outboxService.saveMessage(refund.getPayment().getOrderId(), EventTypes.PAYMENT_REFUNDED, EventEnvelope.v1(
                        EventTypes.PAYMENT_REFUNDED, refund.getPayment().getOrderId(), correlationId,
                        new PaymentRefundedEvent(refund.getPayment().getOrderId(), refund.getPayment().getId(), refund.getReason())
                ));
        } else if (refund.getStatus() == PaymentRefundStatus.FAILED) {
            outboxService.saveMessage(refund.getOrderId(), EventTypes.PAYMENT_REFUND_REJECTED, EventEnvelope.v1(
                        EventTypes.PAYMENT_REFUND_REJECTED, refund.getOrderId(), correlationId,
                        new PaymentRefundRejectedEvent(refund.getOrderId(), refund.getPayment().getId(), "PROVIDER_REFUND_FAILED", refund.getFailureReason())
                ));
        }
    }
}
