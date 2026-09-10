package com.fashionstore.contracts.payment.event;

public record PaymentRefundRejectedEvent(
        String orderId,
        String paymentId,
        String failureCode,
        String failureMessage
) {
}
