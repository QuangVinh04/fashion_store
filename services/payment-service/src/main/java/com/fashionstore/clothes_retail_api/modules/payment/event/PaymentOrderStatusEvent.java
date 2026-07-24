package com.fashionstore.product.modules.payment.event;

@Deprecated(forRemoval = true)
public record PaymentOrderStatusEvent(
        String orderId,
        Outcome outcome
) {
    public enum Outcome {
        COMPLETED,
        FAILED
    }
}
