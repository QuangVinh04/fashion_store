package com.fashionstore.payment.entity;

import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "wallet_ledger_entry")
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletLedgerEntry extends BaseEntity {
    @Column(name = "account_id", nullable = false) private String accountId;
    @Column(name = "user_id", nullable = false) private String userId;
    @Column(nullable = false, length = 6) private String direction;
    @Column(nullable = false) private long amount;
    @Column(name = "balance_after", nullable = false) private long balanceAfter;
    @Column(name = "source_type", nullable = false, length = 50) private String sourceType;
    @Column(name = "source_id", nullable = false, length = 120) private String sourceId;
    @Column(name = "operation_id", nullable = false, unique = true, length = 160) private String operationId;
}
