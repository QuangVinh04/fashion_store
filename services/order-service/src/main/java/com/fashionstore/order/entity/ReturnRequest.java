package com.fashionstore.order.entity;

import com.fashionstore.common.persistence.BaseEntity;
import com.fashionstore.order.entity.enumeration.ReturnRequestStatus;
import com.fashionstore.order.entity.enumeration.ReturnRequestKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@Entity
@Table(name = "return_request")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReturnRequest extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    @Builder.Default
    ReturnRequestKind kind = ReturnRequestKind.CUSTOMER_RETURN;

    @Column(name = "verification_by", length = 120)
    String verificationBy;

    @Column(name = "verification_evidence_ids", columnDefinition = "TEXT")
    String verificationEvidenceIds;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    Order order;

    @Column(name = "user_id", nullable = false, length = 36)
    String userId;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    String reason;

    @Column(name = "images", columnDefinition = "TEXT")
    String images;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    ReturnRequestStatus status;

    @Column(name = "reject_reason", length = 500)
    String rejectReason;

    @Column(name = "reviewed_by", length = 120)
    String reviewedBy;

    @Column(name = "reviewed_at")
    LocalDateTime reviewedAt;

    @Column(name = "approved_at")
    Instant approvedAt;

    @Column(name = "received_at")
    Instant receivedAt;

    @Column(name = "closed_at")
    Instant closedAt;

    @Column(name = "approved_refund_amount", precision = 19, scale = 2)
    BigDecimal approvedRefundAmount;

    @Column(name = "refund_ledger_entry_id", length = 255)
    String refundLedgerEntryId;

    @Column(name = "received_by", length = 120)
    String receivedBy;

    @Column(name = "qc_passed")
    Boolean qcPassed;

    @Column(name = "qc_note", length = 500)
    String qcNote;

    @Column(name = "qc_evidence_ids", columnDefinition = "TEXT")
    String qcEvidenceIds;

    @Column(name = "refund_started_at")
    Instant refundStartedAt;

    @Column(name = "refund_failure_reason", length = 500)
    String refundFailureReason;
}
