package com.fashionstore.contracts.payment;

public record PaymentCancellationResult(
        String orderId,
        String paymentId,
        String reason
) {
}
