package com.fashionstore.payment.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.dto.CallbackOutcome;
import com.fashionstore.payment.dto.CallbackProcessResult;
import com.fashionstore.payment.dto.PaymentCallbackResult;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import com.fashionstore.payment.exception.PaymentErrorCode;
import com.fashionstore.payment.repository.PaymentRepository;
import com.fashionstore.payment.service.CallbackPaymentService;
import com.fashionstore.payment.service.PaymentStateService;
import com.fashionstore.payment.service.provider.PaymentHandlerRegistry;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CallbackPaymentServiceImpl implements CallbackPaymentService {

    PaymentRepository paymentRepository;
    PaymentHandlerRegistry paymentHandlerRegistry;
    PaymentStateService paymentStateService;
    TransactionTemplate transactionTemplate;

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public PaymentCallbackResult verifyReturn(PaymentProvider provider, Map<String, String> queryParams) {
        PaymentCallbackResult result = paymentHandlerRegistry.get(provider).verifyCallback(queryParams, null);
        if (!result.isSignatureValid()) {
            throw new AppException(PaymentErrorCode.PAYMENT_SIGNATURE_INVALID);
        }
        // PayOS return cần gọi API tra cứu. Chỉ xác minh một lần, ngoài transaction.
        CallbackProcessResult applied = applyVerifiedResult(provider, result);
        if (applied.outcome() == CallbackOutcome.AMOUNT_INVALID) {
            throw new AppException(PaymentErrorCode.PAYMENT_AMOUNT_INVALID);
        }
        if (applied.outcome() == CallbackOutcome.PAYMENT_NOT_FOUND) {
            throw new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
        return result;
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public CallbackProcessResult processCallback(PaymentProvider provider, Map<String, String> queryParams, String rawBody) {
        boolean empty = (queryParams == null || queryParams.isEmpty()) && rawBody == null;
        if (empty) {
            return CallbackProcessResult.of(CallbackOutcome.INVALID_REQUEST);
        }

        PaymentCallbackResult result = paymentHandlerRegistry.get(provider).verifyCallback(queryParams, rawBody);
        if (!result.isSignatureValid()) {
            return CallbackProcessResult.of(CallbackOutcome.SIGNATURE_INVALID);
        }

        return applyVerifiedResult(provider, result);
    }

    @Override
    public CallbackProcessResult applyVerifiedResult(PaymentProvider provider, PaymentCallbackResult result) {
        if (result == null || !result.isSignatureValid()) {
            return CallbackProcessResult.of(CallbackOutcome.SIGNATURE_INVALID);
        }
        if (result.getStatus() == null) {
            return CallbackProcessResult.of(CallbackOutcome.INVALID_REQUEST);
        }
        // Khóa và cập nhật DB + outbox trong cùng transaction ngắn; không gọi mạng ở đây.
        return transactionTemplate.execute(tx -> {
            Payment payment = result.getMerchantReference() == null
                    ? null
                    : paymentRepository.findByMerchantReference(result.getMerchantReference())
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
        });
    }
}
