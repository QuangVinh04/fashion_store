package com.fashionstore.payment.event;

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
