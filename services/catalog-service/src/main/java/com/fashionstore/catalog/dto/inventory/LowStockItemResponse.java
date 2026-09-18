package com.fashionstore.catalog.dto.inventory;

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
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LowStockItemResponse {

    String variantId;
    String productId;
    String productName;
    String sku;
    Integer quantity;
    Integer reservedQuantity;
    Integer availableQuantity;
    Integer threshold;
}
