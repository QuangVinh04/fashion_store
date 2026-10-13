package com.fashionstore.contracts.payment.command;

import java.math.BigDecimal;

public record AuthorizePaymentCommand(
        String orderId,
        String userId,
        String method,
        String provider,
        BigDecimal amount,
        String currency,
        String clientIp,
        java.time.Instant expiresAt
) {
    public AuthorizePaymentCommand(String orderId,String userId,String method,String provider,
            BigDecimal amount,String currency,String clientIp) {
        this(orderId,userId,method,provider,amount,currency,clientIp,null);
    }
}
