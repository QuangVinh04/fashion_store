package com.fashionstore.contracts.payment;

public record PaymentCancellationRequested(
        String orderId,
        String paymentId,
        String reason
) {
}
