package com.fashionstore.product.modules.payment.service;

import com.fashionstore.product.modules.payment.dto.PaymentCallbackResult;
import com.fashionstore.product.modules.payment.dto.PaymentResponse;
import com.fashionstore.product.modules.payment.entity.Payment;

public interface PaymentStateService {
    PaymentResponse applyResult(Payment payment, PaymentCallbackResult result);
    boolean isProviderAmountValid(Payment payment, PaymentCallbackResult result);
}
