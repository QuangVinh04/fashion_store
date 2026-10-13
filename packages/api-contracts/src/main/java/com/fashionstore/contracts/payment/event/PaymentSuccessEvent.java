package com.fashionstore.contracts.payment.event;

public record PaymentSuccessEvent(String orderId,
                                  String paymentId,int generation) {
    public PaymentSuccessEvent(String orderId,String paymentId) { this(orderId,paymentId,0); }
}
