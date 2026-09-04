package com.fashionstore.contracts.payment.event;

public record PaymentFailedEvent(
        String orderId, String paymentId, String reason
) {
}
