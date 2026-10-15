package com.fashionstore.payment.entity;

import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.common.persistence.AuditedEntity;
import com.fashionstore.payment.entity.enumeration.PaymentRefundStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Persistent audit record for one idempotent provider refund attempt. */
@Getter
@Setter
@Builder
@Entity
@Table(name = "payment_refund")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentRefund extends AuditedEntity {

    @Column(name="business_refund_id",unique=true,length=160) String businessRefundId;
    @Column(nullable=false,length=20) @Builder.Default String destination="PROVIDER";
    @Column(name="reason_type",length=40) String reasonType;
    @Column(name="ledger_entry_id") String ledgerEntryId;
    @Column(name="source_id",length=160) String sourceId;
    @Column(name="currency",nullable=false,length=3) @Builder.Default String currency="VND";
    @Column(name="budget_reserved",nullable=false) @Builder.Default boolean budgetReserved=true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    Payment payment;

    @Column(name = "order_id", nullable = false)
    String orderId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    PaymentRefundStatus status = PaymentRefundStatus.PENDING;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
    String idempotencyKey;

    @Column(name = "reason", length = 500)
    String reason;

    @Column(name = "completed_at")
    LocalDateTime completedAt;
}
