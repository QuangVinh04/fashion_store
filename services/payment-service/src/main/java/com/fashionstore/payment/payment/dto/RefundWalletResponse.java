package com.fashionstore.payment.payment.dto;

public record RefundWalletResponse(
        String ledgerEntryId,
        String userId,
        long amount,
        long newBalance
) {}
