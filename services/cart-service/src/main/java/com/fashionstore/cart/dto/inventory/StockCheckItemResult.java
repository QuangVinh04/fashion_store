package com.fashionstore.cart.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Mirror của inventory-service's CheckStockResponse.StockItemResult. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCheckItemResult {
    private String variantId;
    private boolean available;
    private int requestedQty;
    private int availableQty;
    private String message;
}
