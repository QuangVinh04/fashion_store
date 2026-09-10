package com.fashionstore.contracts.payment.event;

public record PaymentRefundedEvent(
        String orderId,
        String paymentId,
        String reason
) {
}
