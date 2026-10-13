package com.fashionstore.payment.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.payment.command.AuthorizePaymentCommand;
import com.fashionstore.contracts.payment.command.CancelPaymentCommand;
import com.fashionstore.contracts.payment.command.RefundPaymentCommand;
import com.fashionstore.payment.client.PaymentHandlerRegistry;
import com.fashionstore.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.dto.PaymentRefundResult;
import com.fashionstore.payment.dto.PaymentResponse;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import com.fashionstore.payment.exception.PaymentErrorCode;
import com.fashionstore.payment.mapper.PaymentResponseMapper;
import com.fashionstore.payment.repository.PaymentRepository;
import com.fashionstore.payment.service.CallbackPaymentService;
import com.fashionstore.payment.service.PaymentService;
import com.fashionstore.payment.service.PaymentTransactionService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
    PaymentTransactionService paymentTransactionService;
    CallbackPaymentService callbackPaymentService;
    com.fashionstore.payment.service.WalletService walletService;

    @NonFinal
    @Value("${payment.reconciliation.enabled:true}")
    boolean reconciliationEnabled = true;

    @Override
    @Scheduled(
            fixedDelayString = "${payment.reconciliation.delay-ms:30000}",
            initialDelayString = "${payment.reconciliation.delay-ms:30000}")
    public void reconcilePendingPayments() {
        if (!reconciliationEnabled) {
            return;
        }
        LocalDateTime dueBefore = LocalDateTime.now().minusSeconds(30);
        List<PaymentStatus> openStatuses =
                List.of(
                        PaymentStatus.PENDING,
                        PaymentStatus.INITIATING,
                        PaymentStatus.INITIATION_UNKNOWN,
                        PaymentStatus.CANCELLED,
                        PaymentStatus.FAILED);
        List<Payment> candidates =
                paymentTransactionService.findForReconciliation(
                        openStatuses, dueBefore, PageRequest.of(0, 50));
        for (Payment candidate : candidates) {
            try {
                if (candidate.getMethod() != PaymentMethod.ONLINE) continue;
                // Chỉ một worker nhận quyền tra cứu trong mỗi nhịp; ghi thời gian trước HTTP
                // để payment lỗi cũng không chiếm mãi đầu danh sách quét.
                Payment payment =
                        paymentTransactionService.lockAndPrepareForReconciliation(
                                candidate.getId(), openStatuses, dueBefore);
                if (payment == null) {
                    continue;
                }
                if (payment.getMethod() != PaymentMethod.ONLINE) continue;
                if (payment.getStatus() == PaymentStatus.CANCELLED
                        || payment.getStatus() == PaymentStatus.FAILED) {
                    callbackPaymentService.applyVerifiedResult(
                            payment.getProvider(),
                            paymentHandlerRegistry
                                    .get(payment.getProvider())
                                    .queryPayment(payment));
                    continue;
                }
                if (payment.getPaymentUrl() == null) {
                    if (payment.getProvider() == PaymentProvider.PAYOS) {
                        try {
                            // Link có thể đã PAID/CANCELLED/EXPIRED dù chưa lưu được URL.
                            // Ghi nhận trạng thái thật trước; không bắt nó đi qua bước tạo link.
                            var verified =
                                    paymentHandlerRegistry
                                            .get(payment.getProvider())
                                            .queryPayment(payment);
                            callbackPaymentService.applyVerifiedResult(
                                    payment.getProvider(), verified);
                            if (verified.getStatus() != PaymentStatus.PENDING) {
                                continue;
                            }
                        } catch (AppException exception) {
                            if (!(exception.getCause()
                                    instanceof vn.payos.exception.NotFoundException)) {
                                throw exception; // Timeout/lỗi API không chứng minh link chưa tồn
                                                 // tại.
                            }
                        }
                    }
                    // Ứng dụng có thể chết sau HTTP nhưng trước bước lưu URL. Flow khởi tạo
                    // dùng lại reference; PayOS tra cứu link cũ trước khi tạo lại.
                    initiatePayment(payment.getId(), payment.getClientIp(), null);
                    payment = paymentTransactionService.findByIdForUpdate(candidate.getId());
                }
                var result =
                        paymentHandlerRegistry.get(payment.getProvider()).queryPayment(payment);
                callbackPaymentService.applyVerifiedResult(payment.getProvider(), result);
            } catch (RuntimeException exception) {
                // Không suy diễn thành FAILED khi provider lỗi hoặc chưa tìm thấy giao dịch.
                log.warn(
                        "[Payment] Reconciliation deferred for paymentId={}: {}",
                        candidate.getId(),
                        exception.getMessage());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getByOrderId(String orderId) {
        Payment payment =
                paymentRepository
                        .findByOrderId(orderId)
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
        Payment payment =
                paymentRepository
                        .findByOrderId(orderId)
                        .orElseThrow(() -> new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        return initiatePayment(payment.getId(), clientIp, null);
    }

    /** Hai entry point dùng cùng flow; chỉ HTTP của khách hàng cần kiểm tra owner. */
    private PaymentInitiationResult initiatePayment(
            String paymentId, String clientIp, String requiredUserId) {
        String token = UUID.randomUUID().toString();
        // 1. Khóa ngắn để nhận quyền khởi tạo. Định danh và số tiền phải commit TRƯỚC HTTP,
        // vì webhook có thể đến ngay khi provider tạo link, trước khi trả response cho ta.
        Payment payment =
                paymentTransactionService.initiatePhaseOne(
                        paymentId, clientIp, requiredUserId, token);
        if (payment.getMethod() == PaymentMethod.WALLET)
            return walletService.prepare(payment);

        if (payment.getPaymentUrl() != null) {
            return PaymentInitiationResult.builder()
                    .paymentUrl(payment.getPaymentUrl())
                    .merchantReference(payment.getMerchantReference())
                    .providerTransactionId(payment.getTransactionId())
                    .providerAmount(payment.getProviderAmount())
                    .providerCurrency(payment.getProviderCurrency())
                    .providerTransactionDate(payment.getProviderTransactionDate())
                    .build();
        }

        // 2. Không giữ transaction/connection/khóa DB trong lúc đợi provider.
        PaymentInitiationResult result;
        try {
            result = paymentHandlerRegistry.get(payment.getProvider()).initiate(payment, clientIp);
        } catch (RuntimeException exception) {
            paymentTransactionService.initiateHandleException(paymentId, token);
            throw exception;
        }
        // 3. Đọc lại dưới khóa: callback có thể đã hoàn tất thanh toán trong lúc HTTP chạy.
        paymentTransactionService.initiatePhaseTwo(paymentId, token, result);
        return result;
    }

    private void assertPaymentOwner(Payment payment) {
        if (!payment.getUserId().equals(currentUserProvider.getCurrentUserId())) {
            throw new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void authorize(AuthorizePaymentCommand request, String messageId, String correlationId) {
        Payment payment =
                paymentTransactionService.createPaymentOnce(messageId, request, correlationId);

        if (payment != null
                && payment.getMethod() != com.fashionstore.common.payment.PaymentMethod.COD
                && (payment.getStatus() == PaymentStatus.PENDING
                        || payment.getStatus() == PaymentStatus.INITIATING
                        || payment.getStatus() == PaymentStatus.INITIATION_UNKNOWN)) {
            try {
                this.initiateForOrder(request.orderId(), request.clientIp());
            } catch (AppException ex) {
                if (ex.getErrorCode() == PaymentErrorCode.PAYMENT_INITIATION_IN_PROGRESS) {
                    throw new IllegalStateException(
                            "Payment initiation is in progress, please retry later");
                }
                throw ex;
            }
        }
    }

    @Override
    public void cancel(CancelPaymentCommand request, String messageId, String correlationId) {
        try {
            paymentTransactionService.cancelPaymentOnce(messageId, request, correlationId);
        } catch (AppException ex) {
            if (ex.getErrorCode() == PaymentErrorCode.PAYMENT_INITIATION_IN_PROGRESS) {
                throw new IllegalStateException(
                        "Payment initiation is in progress, please retry later");
            }
            throw ex;
        }
    }

    @Override
    public void refund(RefundPaymentCommand request, String messageId, String correlationId) {
        PaymentTransactionService.RefundCall call =
                paymentTransactionService.prepareRefundOnce(messageId, request, correlationId);

        if (call == null) {
            return;
        }

        RefundOutcome outcome = refundAtGateway(call);
        paymentTransactionService.applyRefund(call, outcome.result(), outcome.error());
    }

    private record RefundOutcome(PaymentRefundResult result, String error) {}

    private RefundOutcome refundAtGateway(PaymentTransactionService.RefundCall call) {
        try {
            return new RefundOutcome(
                    paymentHandlerRegistry
                            .get(call.payment().getProvider())
                            .refund(call.payment(), call.refund()),
                    null);
        } catch (RuntimeException exception) {
            log.error(
                    "Hoàn tiền với cổng thất bại cho order {}: {}",
                    call.payment().getOrderId(),
                    exception.getMessage(),
                    exception);
            return new RefundOutcome(null, safeFailureReason(exception));
        }
    }

    private String safeFailureReason(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Payment provider rejected the refund";
        }
        return message.substring(0, Math.min(message.length(), 500));
    }
}
