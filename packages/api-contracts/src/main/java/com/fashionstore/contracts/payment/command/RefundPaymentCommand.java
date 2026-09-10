package com.fashionstore.contracts.payment.command;

import java.math.BigDecimal;

public record RefundPaymentCommand(
        String orderId,
        String paymentId,
        BigDecimal amount,
        String reason
) {
}
