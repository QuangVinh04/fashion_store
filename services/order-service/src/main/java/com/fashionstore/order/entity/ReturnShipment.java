package com.fashionstore.order.entity;

import com.fashionstore.common.persistence.BaseEntity;
import com.fashionstore.order.entity.enumeration.ReturnShipmentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@Entity
@Table(name = "return_shipment")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReturnShipment extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_request_id", nullable = false, unique = true)
    ReturnRequest returnRequest;

    @Column(name = "ghn_order_code", nullable = false, unique = true, length = 100)
    String ghnOrderCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    ReturnShipmentStatus status = ReturnShipmentStatus.READY_TO_PICK;

    @Column(name = "picked_at")
    Instant pickedAt;

    @Column(name = "arrived_at")
    Instant arrivedAt;

    @Column(name = "cancelled_at")
    Instant cancelledAt;

    @Column(name = "last_ghn_status", length = 40)
    String lastGhnStatus;

    @Column(name = "cancel_attempts", nullable = false)
    @Builder.Default
    int cancelAttempts = 0;

    @Column(name = "last_reconciled_at")
    Instant lastReconciledAt;

    /** Lý do lần lỗi gần nhất: CARRIER_CANCELLED, PICKUP_FAILED, INVALID_DATA, CARRIER_UNAVAILABLE. */
    @Column(name = "failure_code", length = 40)
    String failureCode;

    @Column(name = "failure_reason", length = 500)
    String failureReason;

    @Column(name = "failed_at")
    Instant failedAt;

    @Column(name = "pickup_requested_at")
    Instant pickupRequestedAt;
}
