package com.fashionstore.product.modules.payment.gateway;

import com.fashionstore.product.modules.payment.dto.PaymentCallbackResult;

import java.util.Map;

public interface CallbackPaymentGateway extends PaymentGateway {
    PaymentCallbackResult verifyCallback(Map<String, String> payload);
}
