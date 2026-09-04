package com.fashionstore.inventory.model;

import com.fashionstore.inventory.model.enumeration.InventoryStatus;
import com.fashionstore.common.persistence.AuditedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity
@Table(
        name = "inventory",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"product_id", "variant_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Inventory extends AuditedEntity {

    @Column(name = "variant_id", nullable = false, unique = true)
    String variantId;

    @Column(name = "product_id", nullable = false)
    String productId;

    @Column(nullable = false)
    Integer quantity;

    @Column(name = "reserved_quantity", nullable = false)
    @Builder.Default
    Integer reservedQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    InventoryStatus status;

    public int getQuantityAvailable() {
        return Math.max(0, quantity - reservedQuantity);
    }

    public boolean hasEnoughStock(int requestedQty) {
        return getQuantityAvailable() >= requestedQty;
    }
}
