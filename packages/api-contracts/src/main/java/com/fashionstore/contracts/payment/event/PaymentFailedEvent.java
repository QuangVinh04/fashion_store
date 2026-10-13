package com.fashionstore.contracts.payment.event;

public record PaymentFailedEvent(
        String orderId, String paymentId, String reason,int generation
) {
    public PaymentFailedEvent(String orderId,String paymentId,String reason) { this(orderId,paymentId,reason,0); }
}
