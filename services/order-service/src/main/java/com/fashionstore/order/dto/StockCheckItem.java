package com.fashionstore.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Mirror của catalog-service's CheckStockRequest.StockItem — request gửi đi. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCheckItem {
    private String variantId;
    private Integer quantity;
}
