package com.fashionstore.payment.gateway;

import com.fashionstore.payment.dto.PaymentCallbackResult;
import com.fashionstore.payment.entity.Payment;

public interface CapturablePaymentGateway extends PaymentGateway {
    PaymentCallbackResult capture(Payment payment);
}
