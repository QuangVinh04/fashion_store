package com.fashionstore.order.cart.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Mirror của inventory-service's CheckStockRequest.StockItem — request gửi đi. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCheckItem {
    private String variantId;
    private Integer quantity;
}
