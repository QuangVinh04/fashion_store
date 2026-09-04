package com.fashionstore.payment.service;

import com.fashionstore.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.dto.PaymentResponse;

public interface PaymentService {
    PaymentResponse getByOrderId(String orderId);
    PaymentInitiationResult initiate(String paymentId, String clientIp);
}
