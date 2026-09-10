package com.fashionstore.catalog.model;

import com.fashionstore.catalog.model.enumeration.InventoryReservationStatus;
import com.fashionstore.common.persistence.AuditedEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "inventory_reservation")
public class InventoryReservation extends AuditedEntity {

    @Column(name = "order_id", nullable = false, unique = true, length = 36)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InventoryReservationStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
}
