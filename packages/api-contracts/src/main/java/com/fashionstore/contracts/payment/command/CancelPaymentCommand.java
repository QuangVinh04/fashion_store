package com.fashionstore.contracts.payment.command;

public record CancelPaymentCommand(
        String orderId,
        String paymentId,
        String reason
) {
}
