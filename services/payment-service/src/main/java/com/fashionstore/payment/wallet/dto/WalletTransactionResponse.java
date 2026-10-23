package com.fashionstore.payment.wallet.dto;

import com.fashionstore.payment.wallet.entity.WalletLedgerEntry;
import java.time.Instant;
import java.time.ZoneId;

public record WalletTransactionResponse(String id,String direction,String amount,String balanceAfter,
        String sourceType,String sourceId,Instant createdAt) {
    public static WalletTransactionResponse from(WalletLedgerEntry entry) {
        return new WalletTransactionResponse(entry.getId(),entry.getDirection(),Long.toString(entry.getAmount()),
                Long.toString(entry.getBalanceAfter()),entry.getSourceType(),entry.getSourceId(),entry.getCreatedAt()==null
                        ? null : entry.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant());
    }
}
