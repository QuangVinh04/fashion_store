package com.fashionstore.contracts.payment.event;

public record PaymentCancellationRejectedEvent(
        String orderId,
        String failureCode,
        String failureMessage
) {
}
