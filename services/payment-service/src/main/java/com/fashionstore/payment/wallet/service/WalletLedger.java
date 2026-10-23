package com.fashionstore.payment.wallet.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.payment.wallet.entity.WalletAccount;
import com.fashionstore.payment.wallet.entity.WalletLedgerEntry;
import com.fashionstore.payment.wallet.exception.WalletErrorCode;
import com.fashionstore.payment.wallet.repository.WalletAccountRepository;
import com.fashionstore.payment.wallet.repository.WalletLedgerEntryRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Ledger operations for callers that already hold a transaction (the account row lock requires
 * one). Deliberately not {@code @Transactional}: a failure here must not mark the caller's
 * transaction rollback-only, so a caller may catch {@link AppException} and still commit a
 * business rejection. Standalone callers use {@link WalletService}.
 */
@Component
@RequiredArgsConstructor
public class WalletLedger {

    private final WalletAccountRepository accounts;
    private final WalletLedgerEntryRepository entries;

    public WalletLedgerEntry credit(
            String userId, long amount, String sourceType, String sourceId, String operationId) {
        return mutate("CREDIT", userId, amount, sourceType, sourceId, operationId);
    }

    public WalletLedgerEntry debit(
            String userId, long amount, String sourceType, String sourceId, String operationId) {
        return mutate("DEBIT", userId, amount, sourceType, sourceId, operationId);
    }

    public long getBalance(String userId) {
        validate(userId, 255);
        return accounts.findByUserId(userId).map(WalletAccount::getBalance).orElse(0L);
    }

    public Optional<WalletLedgerEntry> findEntry(String id) {
        return entries.findById(id);
    }

    public Optional<WalletLedgerEntry> findEntryByOperationId(String operationId) {
        return entries.findByOperationId(operationId);
    }

    private WalletLedgerEntry mutate(
            String direction,
            String userId,
            long amount,
            String sourceType,
            String sourceId,
            String operationId) {
        validate(userId, 255);
        validate(sourceType, 50);
        validate(sourceId, 120);
        validate(operationId, 160);
        if (amount <= 0) throw new AppException(WalletErrorCode.AMOUNT_INVALID);
        accounts.createIfAbsent(UUID.randomUUID().toString(), userId);
        WalletAccount account = accounts.findByUserIdForUpdate(userId).orElseThrow();
        WalletLedgerEntry existing = entries.findByOperationId(operationId).orElse(null);
        if (existing != null) {
            if (!existing.getUserId().equals(userId)
                    || !existing.getDirection().equals(direction)
                    || existing.getAmount() != amount
                    || !existing.getSourceType().equals(sourceType)
                    || !existing.getSourceId().equals(sourceId))
                throw new AppException(WalletErrorCode.OPERATION_CONFLICT);
            return existing;
        }
        long next;
        if ("DEBIT".equals(direction)) {
            if (account.getBalance() < amount)
                throw new AppException(WalletErrorCode.INSUFFICIENT_BALANCE);
            next = account.getBalance() - amount;
        } else {
            try {
                next = Math.addExact(account.getBalance(), amount);
            } catch (ArithmeticException e) {
                throw new AppException(WalletErrorCode.AMOUNT_INVALID);
            }
        }
        account.setBalance(next);
        accounts.save(account);
        try {
            return entries.saveAndFlush(
                    WalletLedgerEntry.builder()
                            .accountId(account.getId())
                            .userId(userId)
                            .direction(direction)
                            .amount(amount)
                            .balanceAfter(next)
                            .sourceType(sourceType)
                            .sourceId(sourceId)
                            .operationId(operationId)
                            .build());
        } catch (DataIntegrityViolationException e) {
            throw new AppException(WalletErrorCode.OPERATION_CONFLICT, e);
        }
    }

    private static void validate(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max)
            throw new AppException(WalletErrorCode.SOURCE_INVALID);
    }
}
