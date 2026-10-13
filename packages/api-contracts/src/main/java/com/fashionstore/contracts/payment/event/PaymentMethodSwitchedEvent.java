package com.fashionstore.contracts.payment.event;

public record PaymentMethodSwitchedEvent(String operationId,String orderId,String paymentId,int generation,String method,String provider) {}
