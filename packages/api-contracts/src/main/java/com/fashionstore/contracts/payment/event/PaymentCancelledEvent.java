package com.fashionstore.contracts.payment.event;

public record PaymentCancelledEvent(
        String orderId, String paymentId, String reason
) {
}
