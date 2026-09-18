package com.fashionstore.catalog.dto.inventory;

import com.fashionstore.catalog.entity.enumeration.InventoryLedgerType;
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
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InventoryLedgerResponse {

    String id;
    String variantId;
    InventoryLedgerType type;
    Integer quantity;
    String refOrderId;
    String createdBy;
    LocalDateTime createdAt;
}
