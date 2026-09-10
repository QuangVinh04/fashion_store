package com.fashionstore.contracts.payment.event;

public record PaymentSuccessEvent(String orderId,
                                  String paymentId) {
}
