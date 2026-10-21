package com.fashionstore.payment.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.payment.command.AuthorizePaymentCommand;
import com.fashionstore.contracts.payment.command.CancelPaymentCommand;
import com.fashionstore.payment.client.PaymentHandlerRegistry;
import com.fashionstore.payment.dto.PaymentInitiationResult;
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
import java.util.Objects;
import com.fashionstore.payment.dto.PaymentReturnResolutionResponse;

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
        // Chỉ tra cứu link đã phát cho khách để bù webhook bị mất; CANCELLED/FAILED vẫn tra để bắt
        // late capture và đóng link còn mở. Không bao giờ tạo link ở đây.
        List<PaymentStatus> openStatuses =
                List.of(PaymentStatus.PENDING, PaymentStatus.CANCELLED, PaymentStatus.FAILED);
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
                var handler = paymentHandlerRegistry.get(payment.getProvider());
                var result = handler.queryPayment(payment);
                // Đơn đã huỷ mà link còn mở: đóng link. Khách trả tiền ngay trước đó thì cancel lỗi,
                // lượt sau query thấy PAID và đi nhánh late capture.
                if (payment.getStatus() == PaymentStatus.CANCELLED
                        && result.getStatus() == PaymentStatus.PENDING) {
                    handler.cancelLink(payment);
                    continue;
                }
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

    /**
     * Khách bấm "Thanh toán": tạo link đồng bộ, một lần. Provider lỗi thì transaction rollback,
     * payment vẫn PENDING và khách thử lại hoặc huỷ đơn — saga không bị ảnh hưởng.
     */
    @Override
    @Transactional
    public PaymentInitiationResult initiate(String paymentId, String clientIp) {
        Payment payment =
                paymentTransactionService.lockForInitiation(
                        paymentId, currentUserProvider.getCurrentUserId());
        if (payment.getMethod() == PaymentMethod.WALLET) return walletService.prepare(payment);
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

        PaymentInitiationResult result;
        try {
            result = paymentHandlerRegistry.get(payment.getProvider()).initiate(payment, clientIp);
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AppException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR, exception);
        }
        paymentTransactionService.saveInitiatedLink(payment, result);
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
        // Chỉ ghi DB. Link online do khách tạo qua initiate(), nên lỗi provider không chạm tới saga.
        Payment payment =
                paymentTransactionService.createPaymentOnce(messageId, request, correlationId);
        if (payment != null
                && payment.getMethod() == PaymentMethod.WALLET
                && payment.getStatus() == PaymentStatus.PENDING) {
            walletService.prepare(payment);
        }
    }

    @Override
    public void cancel(CancelPaymentCommand request, String messageId, String correlationId) {
        paymentTransactionService.cancelPaymentOnce(messageId, request, correlationId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentReturnResolutionResponse resolveReturn(PaymentProvider provider, String reference) {
        if ((provider != PaymentProvider.PAYOS && provider != PaymentProvider.VNPAY)
                || reference == null || reference.isBlank() || reference.length() > 100) {
            throw new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }

        Payment payment = paymentRepository.findForReturnByMerchantReference(reference)
                .filter(p -> p.getProvider() == provider)
                .or(() -> paymentRepository.findByTransactionId(reference)
                        .filter(p -> p.getProvider() == provider))
                .orElseThrow(() -> new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        if (!Objects.equals(payment.getUserId(), currentUserProvider.getCurrentUserId())) {
            throw new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }

        String captureStatus = payment.getStatus() == PaymentStatus.COMPLETED
                ? "CAPTURED"
                : "UNCONFIRMED";

        return new PaymentReturnResolutionResponse(
                payment.getOrderId(),
                payment.getId(),
                payment.getInitiationAttempts(),
                payment.getStatus().name(),
                captureStatus
        );
    }
}
