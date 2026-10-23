package com.fashionstore.payment.wallet.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.payment.wallet.entity.WalletLedgerEntry;
import com.fashionstore.payment.wallet.exception.WalletErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Transactional entry point to the wallet ledger; see {@link WalletLedger} for in-transaction use. */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletLedger ledger;

    @Transactional
    public WalletLedgerEntry credit(
            String userId, long amount, String sourceType, String sourceId, String operationId) {
        return ledger.credit(userId, amount, sourceType, sourceId, operationId);
    }

    @Transactional
    public WalletLedgerEntry debit(
            String userId, long amount, String sourceType, String sourceId, String operationId) {
        return ledger.debit(userId, amount, sourceType, sourceId, operationId);
    }

    @Transactional(readOnly = true)
    public long getBalance(String userId) {
        return ledger.getBalance(userId);
    }

    public static long toVnd(BigDecimal amount) {
        if (amount == null) throw new AppException(WalletErrorCode.AMOUNT_INVALID);
        try {
            long value = amount.longValueExact();
            if (value <= 0) throw new AppException(WalletErrorCode.AMOUNT_INVALID);
            return value;
        } catch (ArithmeticException e) {
            throw new AppException(WalletErrorCode.AMOUNT_INVALID);
        }
    }
}
