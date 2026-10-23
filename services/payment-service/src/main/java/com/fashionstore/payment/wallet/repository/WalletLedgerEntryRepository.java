package com.fashionstore.payment.wallet.repository;

import com.fashionstore.payment.wallet.entity.WalletLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WalletLedgerEntryRepository extends JpaRepository<WalletLedgerEntry, String> {
    Optional<WalletLedgerEntry> findByOperationId(String operationId);
    Page<WalletLedgerEntry> findByUserIdOrderByCreatedAtDescIdDesc(String userId, Pageable pageable);
}
