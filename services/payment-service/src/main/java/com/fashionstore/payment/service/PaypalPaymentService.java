package com.fashionstore.payment.service;

import com.fashionstore.payment.dto.PaymentResponse;

public interface PaypalPaymentService {
    PaymentResponse capture(String paymentId);
}
