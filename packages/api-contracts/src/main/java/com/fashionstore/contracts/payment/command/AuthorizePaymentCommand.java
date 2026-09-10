package com.fashionstore.contracts.payment.command;

import java.math.BigDecimal;

public record AuthorizePaymentCommand(
        String orderId,
        String userId,
        String method,
        String provider,
        BigDecimal amount,
        String currency
) {
}
