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

import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CallbackPaymentServiceImpl implements CallbackPaymentService {

    PaymentRepository paymentRepository;
    PaymentHandlerRegistry paymentHandlerRegistry;
    PaymentStateService paymentStateService;

    @Override
    @Transactional(readOnly = true)
    public PaymentCallbackResult verifyReturn(PaymentProvider provider, Map<String, String> queryParams) {
        PaymentCallbackResult result = paymentHandlerRegistry.get(provider).verifyCallback(queryParams, null);
        if (!result.isSignatureValid()) {
            throw new AppException(PaymentErrorCode.PAYMENT_SIGNATURE_INVALID);
        }
        return result;
    }

    @Override
    @Transactional
    public CallbackProcessResult processCallback(PaymentProvider provider, Map<String, String> queryParams, String rawBody) {
        boolean empty = (queryParams == null || queryParams.isEmpty()) && rawBody == null;
        if (empty) {
            return CallbackProcessResult.of(CallbackOutcome.INVALID_REQUEST);
        }

        PaymentCallbackResult result = paymentHandlerRegistry.get(provider).verifyCallback(queryParams, rawBody);
        if (!result.isSignatureValid()) {
            return CallbackProcessResult.of(CallbackOutcome.SIGNATURE_INVALID);
        }

        Payment payment = result.getMerchantReference() == null
                ? null
                : paymentRepository.findByMerchantReference(result.getMerchantReference())
                        .or(() -> paymentRepository.findByTransactionId(result.getMerchantReference()))
                        .orElse(null);
        if (payment == null) {
            return CallbackProcessResult.of(CallbackOutcome.PAYMENT_NOT_FOUND);
        }
        if (!paymentStateService.isProviderAmountValid(payment, result)) {
            return CallbackProcessResult.of(CallbackOutcome.AMOUNT_INVALID);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return CallbackProcessResult.of(CallbackOutcome.ALREADY_PROCESSED);
        }

        return new CallbackProcessResult(CallbackOutcome.APPLIED, paymentStateService.applyResult(payment, result));
    }
}
