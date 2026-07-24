package com.fashionstore.contracts.payment;

public record PaymentResult(String orderId, String paymentId, String failureReason) {
}
