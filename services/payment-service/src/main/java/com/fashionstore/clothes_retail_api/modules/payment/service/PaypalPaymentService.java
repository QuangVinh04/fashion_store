package com.fashionstore.product.modules.payment.service;

import com.fashionstore.product.modules.payment.dto.PaymentResponse;

public interface PaypalPaymentService {
    PaymentResponse capture(String paymentId);
}
