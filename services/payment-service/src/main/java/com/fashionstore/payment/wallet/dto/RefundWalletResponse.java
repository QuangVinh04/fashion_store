package com.fashionstore.payment.wallet.dto;

public record RefundWalletResponse(
        String ledgerEntryId,
        String userId,
        long amount,
        long newBalance
) {}
