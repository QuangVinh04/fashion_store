package com.fashionstore.payment.gateway;

import com.fashionstore.payment.dto.PaymentCallbackResult;

import java.util.Map;

public interface CallbackPaymentGateway extends PaymentGateway {
    PaymentCallbackResult verifyCallback(Map<String, String> payload);
}
