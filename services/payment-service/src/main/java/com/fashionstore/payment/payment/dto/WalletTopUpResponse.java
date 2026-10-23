package com.fashionstore.payment.payment.dto;

import com.fashionstore.payment.payment.entity.Payment;
import java.time.Instant;
import java.time.ZoneId;

public record WalletTopUpResponse(
        String id,
        String amount,
        String currency,
        String status,
        Instant expiresAt,
        String paymentUrl,
        Instant creditedAt,
        String reconciliationReason
) {
    public static WalletTopUpResponse from(Payment payment) {
        Instant credited = payment.getPaidAt() != null
                ? payment.getPaidAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant()
                : null;
        return new WalletTopUpResponse(
                payment.getId(),
                payment.getAmount() != null ? payment.getAmount().toPlainString() : "0",
                payment.getCurrency() != null ? payment.getCurrency() : "VND",
                payment.getStatus() != null ? payment.getStatus().name() : "PENDING",
                payment.getAuthorizationExpiresAt(),
                payment.getPaymentUrl(),
                credited,
                payment.getFailureReason()
        );
    }
}
