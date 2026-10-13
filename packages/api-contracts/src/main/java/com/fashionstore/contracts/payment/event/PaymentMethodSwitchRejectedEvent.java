package com.fashionstore.contracts.payment.event;

public record PaymentMethodSwitchRejectedEvent(String operationId,String orderId,String paymentId,String code,String reason) {}
