package com.fashionstore.payment.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.payment.exception.PaymentErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.dto.PaymentResponse;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import com.fashionstore.payment.mapper.PaymentResponseMapper;
import com.fashionstore.payment.repository.PaymentRepository;
import com.fashionstore.payment.service.PaymentService;
import com.fashionstore.payment.service.provider.PaymentHandlerRegistry;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import com.fashionstore.payment.service.CallbackPaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.payment.event.PaymentInitiatedEvent;
import com.fashionstore.payment.outbox.OutboxService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentServiceImpl implements PaymentService {

    PaymentRepository paymentRepository;
    PaymentHandlerRegistry paymentHandlerRegistry;
    PaymentResponseMapper paymentResponseMapper;
    CurrentUserProvider currentUserProvider;
    TransactionTemplate transactionTemplate;
    OutboxService outboxService;
    CallbackPaymentService callbackPaymentService;

    @NonFinal
    @Value("${payment.reconciliation.enabled:true}")
    boolean reconciliationEnabled = true;

    @Override
    @Scheduled(fixedDelayString = "${payment.reconciliation.delay-ms:30000}",
            initialDelayString = "${payment.reconciliation.delay-ms:30000}")
    public void reconcilePendingPayments() {
        if (!reconciliationEnabled) {
            return;
        }
        LocalDateTime dueBefore = LocalDateTime.now().minusSeconds(30);
        List<PaymentStatus> openStatuses = List.of(PaymentStatus.PENDING, PaymentStatus.INITIATING,
                PaymentStatus.INITIATION_UNKNOWN);
        List<Payment> candidates = transactionTemplate.execute(tx ->
                paymentRepository.findForReconciliation(openStatuses, dueBefore, PageRequest.of(0, 50)));
        for (Payment candidate : candidates) {
            try {
                // Chỉ một worker nhận quyền tra cứu trong mỗi nhịp; ghi thời gian trước HTTP
                // để payment lỗi cũng không chiếm mãi đầu danh sách quét.
                Payment payment = transactionTemplate.execute(tx -> {
                    Payment current = paymentRepository.findByIdForUpdate(candidate.getId()).orElse(null);
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
                });
                if (payment == null) {
                    continue;
                }
                if (payment.getPaymentUrl() == null) {
                    if (payment.getProvider() == PaymentProvider.PAYOS) {
                        try {
                            // Link có thể đã PAID/CANCELLED/EXPIRED dù chưa lưu được URL.
                            // Ghi nhận trạng thái thật trước; không bắt nó đi qua bước tạo link.
                            var verified = paymentHandlerRegistry.get(payment.getProvider()).queryPayment(payment);
                            callbackPaymentService.applyVerifiedResult(payment.getProvider(), verified);
                            if (verified.getStatus() != PaymentStatus.PENDING) {
                                continue;
                            }
                        } catch (AppException exception) {
                            if (!(exception.getCause() instanceof vn.payos.exception.NotFoundException)) {
                                throw exception; // Timeout/lỗi API không chứng minh link chưa tồn tại.
                            }
                        }
                    }
                    // Ứng dụng có thể chết sau HTTP nhưng trước bước lưu URL. Flow khởi tạo
                    // dùng lại reference; PayOS tra cứu link cũ trước khi tạo lại.
                    initiatePayment(payment.getId(), payment.getClientIp(), null);
                    payment = transactionTemplate.execute(tx ->
                            paymentRepository.findByIdForUpdate(candidate.getId()).orElseThrow());
                }
                var result = paymentHandlerRegistry.get(payment.getProvider()).queryPayment(payment);
                callbackPaymentService.applyVerifiedResult(payment.getProvider(), result);
            } catch (RuntimeException exception) {
                // Không suy diễn thành FAILED khi provider lỗi hoặc chưa tìm thấy giao dịch.
                log.warn("[Payment] Reconciliation deferred for paymentId={}: {}", candidate.getId(), exception.getMessage());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getByOrderId(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        assertPaymentOwner(payment);
        return paymentResponseMapper.toResponse(payment);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public PaymentInitiationResult initiate(String paymentId, String clientIp) {
        return initiatePayment(paymentId, clientIp, currentUserProvider.getCurrentUserId());
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public PaymentInitiationResult initiateForOrder(String orderId, String clientIp) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        return initiatePayment(payment.getId(), clientIp, null);
    }

    /** Hai entry point dùng cùng flow; chỉ HTTP của khách hàng cần kiểm tra owner. */
    private PaymentInitiationResult initiatePayment(String paymentId, String clientIp, String requiredUserId) {
        String token = UUID.randomUUID().toString();
        // 1. Khóa ngắn để nhận quyền khởi tạo. Định danh và số tiền phải commit TRƯỚC HTTP,
        // vì webhook có thể đến ngay khi provider tạo link, trước khi trả response cho ta.
        Payment payment = transactionTemplate.execute(tx -> {
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
                        .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
            }
            current.setClientIp(clientIp);
            current.setStatus(PaymentStatus.INITIATING);
            current.setInitiationToken(token);
            current.setInitiationStartedAt(LocalDateTime.now());
            current.setInitiationAttempts(current.getInitiationAttempts() + 1);
            return paymentRepository.save(current);
        });

        if (payment.getPaymentUrl() != null) {
            return PaymentInitiationResult.builder().paymentUrl(payment.getPaymentUrl())
                    .merchantReference(payment.getMerchantReference()).providerTransactionId(payment.getTransactionId())
                    .providerAmount(payment.getProviderAmount()).providerCurrency(payment.getProviderCurrency())
                    .providerTransactionDate(payment.getProviderTransactionDate()).build();
        }

        // 2. Không giữ transaction/connection/khóa DB trong lúc đợi provider.
        PaymentInitiationResult result;
        try {
            result = paymentHandlerRegistry.get(payment.getProvider()).initiate(payment, clientIp);
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(tx -> {
                Payment current = paymentRepository.findByIdForUpdate(paymentId).orElseThrow();
                if (current.getStatus() == PaymentStatus.INITIATING
                        && Objects.equals(token, current.getInitiationToken())) {
                    // Timeout không chứng minh provider thất bại. Giữ định danh để đối soát/retry.
                    current.setStatus(PaymentStatus.INITIATION_UNKNOWN);
                    current.setFailureReason("Chưa xác định kết quả khởi tạo; cần tra cứu provider");
                    paymentRepository.save(current);
                }
            });
            throw exception;
        }

        // 3. Đọc lại dưới khóa: callback có thể đã hoàn tất thanh toán trong lúc HTTP chạy.
        transactionTemplate.executeWithoutResult(tx -> {
            Payment current = paymentRepository.findByIdForUpdate(paymentId).orElseThrow();
            if (!Objects.equals(token, current.getInitiationToken())) {
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
        });
        return result;
    }

    private void assertPaymentOwner(Payment payment) {
        if (!payment.getUserId().equals(currentUserProvider.getCurrentUserId())) {
            throw new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
    }
}
