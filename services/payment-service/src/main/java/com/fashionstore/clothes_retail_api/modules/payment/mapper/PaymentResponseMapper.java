package com.fashionstore.product.modules.payment.mapper;

import com.fashionstore.product.modules.payment.dto.PaymentResponse;
import com.fashionstore.product.modules.payment.entity.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentResponseMapper {

    public PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .method(payment.getMethod())
                .provider(payment.getProvider())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .providerAmount(payment.getProviderAmount())
                .providerCurrency(payment.getProviderCurrency())
                .merchantReference(payment.getMerchantReference())
                .transactionId(payment.getTransactionId())
                .failureReason(payment.getFailureReason())
                .paidAt(payment.getPaidAt())
                .build();
    }
}
