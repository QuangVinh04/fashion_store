package com.fashionstore.contracts.payment;

import java.math.BigDecimal;

public record PaymentRequested(
        String orderId,
        String userId,
        String method,
        String provider,
        BigDecimal amount
) {
}
