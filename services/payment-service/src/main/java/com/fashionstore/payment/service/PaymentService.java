package com.fashionstore.payment.service;

import com.fashionstore.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.dto.PaymentResponse;

public interface PaymentService {
    PaymentResponse getByOrderId(String orderId);
    PaymentInitiationResult initiate(String paymentId, String clientIp);
    /** Internal saga entry point; does not use the browser's authenticated user. */
    PaymentInitiationResult initiateForOrder(String orderId, String clientIp);
    void reconcilePendingPayments();
}
