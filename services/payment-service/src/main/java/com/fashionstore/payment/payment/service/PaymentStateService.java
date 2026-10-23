package com.fashionstore.payment.payment.service;

import com.fashionstore.payment.payment.dto.PaymentCallbackResult;
import com.fashionstore.payment.payment.dto.PaymentResponse;
import com.fashionstore.payment.payment.entity.Payment;

public interface PaymentStateService {
    PaymentResponse applyResult(Payment payment, PaymentCallbackResult result);
    boolean isProviderAmountValid(Payment payment, PaymentCallbackResult result);
}
