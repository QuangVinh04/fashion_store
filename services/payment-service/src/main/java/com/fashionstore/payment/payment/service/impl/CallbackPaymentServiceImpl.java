package com.fashionstore.payment.payment.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.payment.client.PaymentHandlerRegistry;
import com.fashionstore.payment.payment.dto.CallbackOutcome;
import com.fashionstore.payment.payment.dto.CallbackProcessResult;
import com.fashionstore.payment.payment.dto.PaymentCallbackResult;
import com.fashionstore.payment.payment.exception.PaymentErrorCode;
import com.fashionstore.payment.payment.service.CallbackPaymentService;
import com.fashionstore.payment.payment.service.PaymentTransactionService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CallbackPaymentServiceImpl implements CallbackPaymentService {

    PaymentHandlerRegistry paymentHandlerRegistry;
    PaymentTransactionService paymentTransactionService;

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public PaymentCallbackResult verifyReturn(
            PaymentProvider provider, Map<String, String> queryParams) {
        PaymentCallbackResult result =
                paymentHandlerRegistry.get(provider).verifyCallback(queryParams, null);
        if (!result.isSignatureValid()) {
            throw new AppException(PaymentErrorCode.PAYMENT_SIGNATURE_INVALID);
        }
        CallbackProcessResult applied = applyVerifiedResult(provider, result);
        if (applied.outcome() == CallbackOutcome.AMOUNT_INVALID) {
            throw new AppException(PaymentErrorCode.PAYMENT_AMOUNT_INVALID);
        }
        if (applied.outcome() == CallbackOutcome.PAYMENT_NOT_FOUND) {
            throw new AppException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
        if (applied.payment() != null) {
            return result.toBuilder()
                    .orderId(applied.payment().getOrderId())
                    .paymentId(applied.payment().getId())
                    .build();
        }
        return result;
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public CallbackProcessResult processCallback(
            PaymentProvider provider, Map<String, String> queryParams, String rawBody) {
        boolean empty = (queryParams == null || queryParams.isEmpty()) && rawBody == null;
        if (empty) {
            return CallbackProcessResult.of(CallbackOutcome.INVALID_REQUEST);
        }

        PaymentCallbackResult result =
                paymentHandlerRegistry.get(provider).verifyCallback(queryParams, rawBody);
        if (!result.isSignatureValid()) {
            return CallbackProcessResult.of(CallbackOutcome.SIGNATURE_INVALID);
        }

        return applyVerifiedResult(provider, result);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public CallbackProcessResult applyVerifiedResult(
            PaymentProvider provider, PaymentCallbackResult result) {
        return paymentTransactionService.applyVerifiedCallbackResult(provider, result);
    }
}
