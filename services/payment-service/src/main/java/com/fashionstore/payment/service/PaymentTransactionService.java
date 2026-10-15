package com.fashionstore.payment.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.common.payment.PaymentType;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.payment.command.AuthorizePaymentCommand;
import com.fashionstore.contracts.payment.command.CancelPaymentCommand;
import com.fashionstore.contracts.payment.event.PaymentCancellationRejectedEvent;
import com.fashionstore.contracts.payment.event.PaymentCancelledEvent;
import com.fashionstore.contracts.payment.event.PaymentInitiatedEvent;
import com.fashionstore.contracts.payment.event.PaymentSuccessEvent;
import com.fashionstore.payment.dto.*;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import com.fashionstore.payment.event.OutboxEventRecorder;
import com.fashionstore.payment.exception.PaymentErrorCode;
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
public class PaymentTransactionService {

    PaymentRepository paymentRepository;
    org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    ProcessedMessageService processedMessageService;
    OutboxEventRecorder outboxEventRecorder;
    PaymentStateService paymentStateService;
    WalletService walletService;

    @Transactional
    public Payment createPaymentOnce(
            String messageId, AuthorizePaymentCommand request, String correlationId) {
        AtomicReference<Payment> paymentRef = new AtomicReference<>();
        processedMessageService.processOnce(
                messageId,
                "payment-requested-v2",
                () -> {
                    Payment existing =
                            paymentRepository
                                    .findByOrderIdForUpdate(request.orderId())
                                    .orElse(null);
                    if (existing != null) {
                        if (existing.getStatus() == PaymentStatus.COMPLETED
                                || existing.getStatus() == PaymentStatus.COD_PENDING) {
                            outboxEventRecorder.saveMessage(
                                    existing.getOrderId(),
                                    EventTypes.PAYMENT_COMPLETED,
                                    EventEnvelope.v1(
                                            EventTypes.PAYMENT_COMPLETED,
                                            existing.getOrderId(),
                                            correlationId,
                                            new PaymentSuccessEvent(
                                                    existing.getOrderId(), existing.getId(), 0)));
                        }
                        paymentRef.set(existing);
                        return;
                    }

                    PaymentMethod method = PaymentMethod.valueOf(request.method());
                    PaymentProvider provider = PaymentProvider.valueOf(request.provider());
                    if ((method == PaymentMethod.WALLET)
                            != (provider == PaymentProvider.INTERNAL)) {
                        throw new AppException(PaymentErrorCode.PAYMENT_PROVIDER_UNSUPPORTED);
                    }
                    if (method == PaymentMethod.WALLET) {
                        WalletService.toVnd(request.amount());
                        if (!"VND".equals(request.currency()) || request.expiresAt() == null)
                            throw new AppException(PaymentErrorCode.PAYMENT_STATUS_INVALID);
                    }
                    Payment payment =
                            Payment.builder()
                                    .orderId(request.orderId())
                                    .userId(request.userId())
                                    .sagaId(correlationId)
                                    .method(method)
                                    .provider(provider)
                                    .authorizationExpiresAt(request.expiresAt())
                                    .amount(request.amount())
                                    .currency(request.currency())
                                    .status(
                                            method == PaymentMethod.COD
                                                    ? PaymentStatus.COD_PENDING
                                                    : PaymentStatus.PENDING)
                                    .build();
                    Payment saved = paymentRepository.save(payment);
                    if (saved.getStatus() == PaymentStatus.COD_PENDING) {
                        outboxEventRecorder.saveMessage(
                                saved.getOrderId(),
                                EventTypes.PAYMENT_COMPLETED,
                                EventEnvelope.v1(
                                        EventTypes.PAYMENT_COMPLETED,
                                        saved.getOrderId(),
                                        correlationId,
                                        new PaymentSuccessEvent(
                                                saved.getOrderId(), saved.getId(), 0)));
                    }
                    paymentRef.set(saved);
                });

        // If it was skipped by processedMessageService, we still need to fetch it to return for
        // re-initiation
        if (paymentRef.get() == null) {
            return paymentRepository.findByOrderId(request.orderId()).orElse(null);
        }
        return paymentRef.get();
    }

    @Transactional
    public void cancelPaymentOnce(
            String messageId, CancelPaymentCommand request, String correlationId) {
        processedMessageService.processOnce(
                messageId,
                "payment-cancellation-v2",
                () -> {
                    Payment payment =
                            paymentRepository
                                    .findByOrderIdForUpdate(request.orderId())
                                    .orElse(null);

                    if (payment == null) {
                        log.warn(
                                "Payment không tồn tại cho order {} khi nhận lệnh hủy, gửi"
                                    + " PAYMENT_CANCELLED để saga hoàn tất bù trừ",
                                request.orderId());
                        outboxEventRecorder.saveMessage(
                                request.orderId(),
                                EventTypes.PAYMENT_CANCELLED,
                                EventEnvelope.v1(
                                        EventTypes.PAYMENT_CANCELLED,
                                        request.orderId(),
                                        correlationId,
                                        new PaymentCancelledEvent(
                                                request.orderId(), null, request.reason())));
                        return;
                    }

                    if (payment.getStatus() == PaymentStatus.INITIATING
                            || payment.getStatus() == PaymentStatus.INITIATION_UNKNOWN) {
                        throw new AppException(PaymentErrorCode.PAYMENT_INITIATION_IN_PROGRESS);
                    }
                    if (payment.getStatus() == PaymentStatus.PENDING
                            || payment.getStatus() == PaymentStatus.COD_PENDING) {
                        if (payment.getMethod() == PaymentMethod.WALLET)
                            walletService.cancelPending(payment.getId());
                        payment.setStatus(PaymentStatus.CANCELLED);
                        payment.setFailureReason(request.reason());
                        paymentRepository.save(payment);
                        outboxEventRecorder.saveMessage(
                                payment.getOrderId(),
                                EventTypes.PAYMENT_CANCELLED,
                                EventEnvelope.v1(
                                        EventTypes.PAYMENT_CANCELLED,
                                        payment.getOrderId(),
                                        correlationId,
                                        new PaymentCancelledEvent(
                                                payment.getOrderId(),
                                                payment.getId(),
                                                request.reason())));
                        return;
                    }

                    if (payment.getStatus() == PaymentStatus.COMPLETED
                            || payment.getStatus() == PaymentStatus.REFUNDED) {
                        outboxEventRecorder.saveMessage(
                                payment.getOrderId(),
                                EventTypes.PAYMENT_CANCELLATION_REJECTED,
                                EventEnvelope.v1(
                                        EventTypes.PAYMENT_CANCELLATION_REJECTED,
                                        payment.getOrderId(),
                                        correlationId,
                                        new PaymentCancellationRejectedEvent(
                                                payment.getOrderId(),
                                                payment.getId(),
                                                "PAYMENT_ALREADY_CAPTURED",
                                                "Payment was already completed")));
                        return;
                    }

                    outboxEventRecorder.saveMessage(
                            payment.getOrderId(),
                            EventTypes.PAYMENT_CANCELLED,
                            EventEnvelope.v1(
                                    EventTypes.PAYMENT_CANCELLED,
                                    payment.getOrderId(),
                                    correlationId,
                                    new PaymentCancelledEvent(
                                            payment.getOrderId(),
                                            payment.getId(),
                                            payment.getFailureReason())));
                });
    }

    @Transactional
    public CallbackProcessResult applyVerifiedCallbackResult(
            PaymentProvider provider, PaymentCallbackResult result) {
        if (result == null || !result.isSignatureValid())
            return CallbackProcessResult.of(CallbackOutcome.SIGNATURE_INVALID);

        Payment payment =
                result.getMerchantReference() == null
                        ? null
                        : paymentRepository
                                .findByMerchantReference(result.getMerchantReference())
                                .or(
                                        () ->
                                                paymentRepository.findByTransactionIdForUpdate(
                                                        result.getMerchantReference()))
                                .orElse(null);
        if (payment == null || payment.getProvider() != provider) {
            return CallbackProcessResult.of(CallbackOutcome.PAYMENT_NOT_FOUND);
        }
        if (result.getStatus() == null || payment.getMethod() != PaymentMethod.ONLINE) {
            return CallbackProcessResult.of(CallbackOutcome.INVALID_REQUEST);
        }
        if (payment.getType() == PaymentType.TOP_UP) {
            if (provider != PaymentProvider.PAYOS || !"VND".equals(payment.getCurrency())) {
                return CallbackProcessResult.of(CallbackOutcome.INVALID_REQUEST);
            }
            if (result.getStatus() == PaymentStatus.COMPLETED) {
                if (!"VND".equals(result.getCurrency())
                        || result.getAmount() == null
                        || result.getAmount().compareTo(payment.getAmount()) != 0) {
                    return CallbackProcessResult.of(CallbackOutcome.AMOUNT_INVALID);
                }
                if (result.getProviderTransactionId() == null
                        || result.getProviderTransactionId().isBlank()) {
                    return CallbackProcessResult.of(CallbackOutcome.INVALID_REQUEST);
                }
                Payment previous =
                        paymentRepository
                                .findByTransactionId(result.getProviderTransactionId())
                                .orElse(null);
                if (previous != null && !previous.getId().equals(payment.getId())) {
                    return CallbackProcessResult.of(CallbackOutcome.INVALID_REQUEST);
                }
                if (payment.getStatus() == PaymentStatus.COMPLETED) {
                    return CallbackProcessResult.of(CallbackOutcome.ALREADY_PROCESSED);
                }
                walletService.credit(
                        payment.getUserId(),
                        WalletService.toVnd(payment.getAmount()),
                        "TOP_UP",
                        payment.getId(),
                        "top-up:" + payment.getId());
                payment.setTransactionId(result.getProviderTransactionId());
                payment.setProviderAmount(result.getAmount());
                payment.setProviderCurrency(result.getCurrency());
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setPaidAt(LocalDateTime.now());
                payment.setFailureReason(null);
                paymentRepository.saveAndFlush(payment);
                return CallbackProcessResult.of(CallbackOutcome.APPLIED);
            }
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                return CallbackProcessResult.of(CallbackOutcome.ALREADY_PROCESSED);
            }
            if (result.getStatus() == PaymentStatus.FAILED) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(result.getFailureReason());
                paymentRepository.save(payment);
                return CallbackProcessResult.of(CallbackOutcome.APPLIED);
            }
            return CallbackProcessResult.of(CallbackOutcome.PENDING);
        }
        if (result.getStatus() == PaymentStatus.COMPLETED
                && !paymentStateService.isProviderAmountValid(payment, result)) {
            return CallbackProcessResult.of(CallbackOutcome.AMOUNT_INVALID);
        }
        boolean verifiedLateCapture =
                result.getStatus() == PaymentStatus.COMPLETED
                        && (payment.getStatus() == PaymentStatus.CANCELLED
                                || payment.getStatus() == PaymentStatus.FAILED);
        if (payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.INITIATING
                && payment.getStatus() != PaymentStatus.INITIATION_UNKNOWN
                && !verifiedLateCapture) {
            return CallbackProcessResult.of(CallbackOutcome.ALREADY_PROCESSED);
        }
        if (result.getStatus() == PaymentStatus.COMPLETED) {
            String txnId = result.getProviderTransactionId() != null && !result.getProviderTransactionId().isBlank()
                    ? result.getProviderTransactionId()
                    : result.getMerchantReference();
            if (txnId == null || txnId.isBlank()) {
                return CallbackProcessResult.of(CallbackOutcome.INVALID_REQUEST);
            }
        }
        if (result.getStatus() == PaymentStatus.PENDING) {
            return CallbackProcessResult.of(CallbackOutcome.PENDING);
        }
        return new CallbackProcessResult(
                CallbackOutcome.APPLIED, paymentStateService.applyResult(payment, result));
    }

    @Transactional(readOnly = true)
    public java.util.List<Payment> findForReconciliation(
            java.util.List<PaymentStatus> openStatuses,
            LocalDateTime dueBefore,
            org.springframework.data.domain.Pageable pageable) {
        return paymentRepository.findForReconciliation(openStatuses, dueBefore, pageable);
    }

    @Transactional
    public Payment lockAndPrepareForReconciliation(
            String paymentId, java.util.List<PaymentStatus> openStatuses, LocalDateTime dueBefore) {
        Payment current = paymentRepository.findByIdForUpdate(paymentId).orElse(null);
        if (current == null
                || !openStatuses.contains(current.getStatus())
                || (current.getLastReconciledAt() != null
                        && !current.getLastReconciledAt().isBefore(dueBefore))) {
            return null;
        }
        if (current.getStatus() == PaymentStatus.INITIATING
                && current.getInitiationStartedAt() != null
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
    public Payment initiatePhaseOne(
            String paymentId, String clientIp, String requiredUserId, String token) {
        Payment current =
                paymentRepository
                        .findByIdForUpdate(paymentId)
                        .orElseThrow(() -> new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        if (requiredUserId != null && !requiredUserId.equals(current.getUserId())) {
            throw new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
        if (current.getStatus() != PaymentStatus.PENDING
                && current.getStatus() != PaymentStatus.INITIATING
                && current.getStatus() != PaymentStatus.INITIATION_UNKNOWN) {
            throw new AppException(PaymentErrorCode.PAYMENT_STATUS_INVALID);
        }
        if (current.getMethod() == PaymentMethod.WALLET) return current;
        if (current.getPaymentUrl() != null) {
            // Phát lại URL cho saga nếu reply trước bị thất lạc; consumer đã chống trùng.
            outboxEventRecorder.saveMessage(
                    current.getOrderId(),
                    EventTypes.PAYMENT_INITIATED,
                    EventEnvelope.v1(
                            EventTypes.PAYMENT_INITIATED,
                            current.getOrderId(),
                            current.getSagaId() == null
                                    ? current.getOrderId()
                                    : current.getSagaId(),
                            new PaymentInitiatedEvent(
                                    current.getOrderId(),
                                    current.getId(),
                                    current.getPaymentUrl(),
                                    0)));
            return current;
        }
        if (current.getStatus() == PaymentStatus.INITIATING
                && current.getInitiationStartedAt() != null
                && current.getInitiationStartedAt().isAfter(LocalDateTime.now().minusMinutes(2))) {
            throw new AppException(PaymentErrorCode.PAYMENT_INITIATION_IN_PROGRESS);
        }
        if (current.getMerchantReference() == null) {
            if (current.getProvider() == PaymentProvider.PAYOS) {
                Long nextRef =
                        jdbcTemplate.queryForObject(
                                "select nextval('payos_reference_seq')", Long.class);
                current.setMerchantReference(String.valueOf(nextRef));
            } else {
                current.setMerchantReference(
                        java.util.UUID.randomUUID().toString().replace("-", ""));
            }
        }
        current.setProviderAmount(current.getAmount());
        current.setProviderCurrency(current.getCurrency());
        if (current.getProviderTransactionDate() == null) {
            current.setProviderTransactionDate(
                    LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                            .format(
                                    java.time.format.DateTimeFormatter.ofPattern(
                                            "yyyyMMddHHmmss")));
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
            outboxEventRecorder.saveMessage(
                    current.getOrderId(),
                    EventTypes.PAYMENT_INITIATED,
                    EventEnvelope.v1(
                            EventTypes.PAYMENT_INITIATED,
                            current.getOrderId(),
                            current.getSagaId() == null
                                    ? current.getOrderId()
                                    : current.getSagaId(),
                            new PaymentInitiatedEvent(
                                    current.getOrderId(),
                                    current.getId(),
                                    result.getPaymentUrl(),
                                    0)));
        }
        paymentRepository.save(current);
    }
}
