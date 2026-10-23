package com.fashionstore.payment.payment.service;

import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.payment.dto.PaymentResponse;
import com.fashionstore.payment.payment.dto.PaymentReturnResolutionResponse;
import com.fashionstore.contracts.payment.command.AuthorizePaymentCommand;
import com.fashionstore.contracts.payment.command.CancelPaymentCommand;

public interface PaymentService {
    PaymentResponse getByOrderId(String orderId);
    PaymentInitiationResult initiate(String paymentId, String clientIp);
    void reconcilePendingPayments();
    
    void authorize(AuthorizePaymentCommand request, String messageId, String correlationId);
    void cancel(CancelPaymentCommand request, String messageId, String correlationId);

    PaymentReturnResolutionResponse resolveReturn(PaymentProvider provider, String reference);
}
