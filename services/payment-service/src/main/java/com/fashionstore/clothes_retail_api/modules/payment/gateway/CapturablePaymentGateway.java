package com.fashionstore.product.modules.payment.gateway;

import com.fashionstore.product.modules.payment.dto.PaymentCallbackResult;
import com.fashionstore.product.modules.payment.entity.Payment;

public interface CapturablePaymentGateway extends PaymentGateway {
    PaymentCallbackResult capture(Payment payment);
}
