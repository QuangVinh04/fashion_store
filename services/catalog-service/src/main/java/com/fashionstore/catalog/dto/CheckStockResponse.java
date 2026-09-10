package com.fashionstore.catalog.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CheckStockResponse {
    private boolean allAvailable;
    private List<StockItemResult> items;

    @Data
    @Builder
    public static class StockItemResult {
        private String  variantId;
        private boolean available;
        private int     requestedQty;
        private int     availableQty;
        private String  message;
    }
}

