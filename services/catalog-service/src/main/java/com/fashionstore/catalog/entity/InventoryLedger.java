package com.fashionstore.catalog.entity;

import com.fashionstore.catalog.entity.enumeration.InventoryLedgerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@Entity
@Table(name = "inventory_ledger")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InventoryLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(name = "variant_id", nullable = false)
    String variantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    InventoryLedgerType type;

    @Column(nullable = false)
    Integer quantity;

    @Column(name = "ref_order_id")
    String refOrderId;

    @Column(name = "created_by")
    String createdBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    LocalDateTime createdAt = LocalDateTime.now();
}
