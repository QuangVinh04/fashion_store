package com.fashionstore.product.modules.payment.service;

import com.fashionstore.product.modules.payment.dto.PaymentInitiationResult;
import com.fashionstore.product.modules.payment.dto.PaymentResponse;

public interface PaymentService {
    PaymentResponse getByOrderId(String orderId);
    PaymentInitiationResult initiate(String paymentId, String clientIp);
}
