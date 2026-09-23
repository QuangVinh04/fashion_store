package com.fashionstore.payment.service;

import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.dto.CallbackProcessResult;
import com.fashionstore.payment.dto.PaymentCallbackResult;

import java.util.Map;

/** Dùng chung cho mọi provider dạng Passive Callback (VNPay, PayOS, ...), tham số hoá theo provider. */
public interface CallbackPaymentService {
    PaymentCallbackResult verifyReturn(PaymentProvider provider, Map<String, String> queryParams);

    CallbackProcessResult processCallback(PaymentProvider provider, Map<String, String> queryParams, String rawBody);
}
