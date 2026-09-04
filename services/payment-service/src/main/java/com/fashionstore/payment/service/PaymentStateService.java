package com.fashionstore.payment.service;

import com.fashionstore.payment.dto.PaymentCallbackResult;
import com.fashionstore.payment.dto.PaymentResponse;
import com.fashionstore.payment.entity.Payment;

public interface PaymentStateService {
    PaymentResponse applyResult(Payment payment, PaymentCallbackResult result);
    boolean isProviderAmountValid(Payment payment, PaymentCallbackResult result);
}
