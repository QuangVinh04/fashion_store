package com.fashionstore.catalog.entity;

import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "inventory_reservation_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InventoryReservationItem extends BaseEntity {

    @Column(name = "reservation_id", nullable = false, length = 36)
    String reservationId;

    @Column(name = "variant_id", nullable = false, length = 36)
    String variantId;

    @Column(nullable = false)
    Integer quantity;
}
