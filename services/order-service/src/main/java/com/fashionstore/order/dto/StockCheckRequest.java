package com.fashionstore.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Mirror của catalog-service's CheckStockRequest — read-only, không có side effect. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCheckRequest {
    private List<StockCheckItem> items;
}
