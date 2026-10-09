package com.fashionstore.payment.service;

import com.fashionstore.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.dto.PaymentResponse;
import com.fashionstore.contracts.payment.command.AuthorizePaymentCommand;
import com.fashionstore.contracts.payment.command.CancelPaymentCommand;
import com.fashionstore.contracts.payment.command.RefundPaymentCommand;

public interface PaymentService {
    PaymentResponse getByOrderId(String orderId);
    PaymentInitiationResult initiate(String paymentId, String clientIp);
    /** Internal saga entry point; does not use the browser's authenticated user. */
    PaymentInitiationResult initiateForOrder(String orderId, String clientIp);
    void reconcilePendingPayments();
    
    void authorize(AuthorizePaymentCommand request, String messageId, String correlationId);
    void cancel(CancelPaymentCommand request, String messageId, String correlationId);
    void refund(RefundPaymentCommand request, String messageId, String correlationId);
}
