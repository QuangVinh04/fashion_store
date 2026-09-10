package com.fashionstore.payment.gateway;

import com.fashionstore.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.common.payment.PaymentProvider;

public interface PaymentGateway {
    PaymentProvider provider();

    PaymentInitiationResult initiate(Payment payment, String clientIp);
}
