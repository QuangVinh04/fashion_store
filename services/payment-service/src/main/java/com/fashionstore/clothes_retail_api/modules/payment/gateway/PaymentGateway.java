package com.fashionstore.product.modules.payment.gateway;

import com.fashionstore.product.modules.payment.dto.PaymentInitiationResult;
import com.fashionstore.product.modules.payment.entity.Payment;
import com.fashionstore.common.payment.PaymentProvider;

public interface PaymentGateway {
    PaymentProvider provider();

    PaymentInitiationResult initiate(Payment payment, String clientIp);
}
