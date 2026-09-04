package com.fashionstore.order.cart.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Mirror của inventory-service's CheckStockResponse — response nhận về. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCheckResult {
    private boolean allAvailable;
    private List<StockCheckItemResult> items;

    /** Dùng khi inventory-service không phản hồi được (circuit breaker fallback). */
    public static StockCheckResult unavailable(List<StockCheckItem> requested) {
        List<StockCheckItemResult> items = requested.stream()
                .map(item -> StockCheckItemResult.builder()
                        .variantId(item.getVariantId())
                        .available(false)
                        .requestedQty(item.getQuantity())
                        .availableQty(0)
                        .message("inventory-service unavailable")
                        .build())
                .toList();
        return StockCheckResult.builder().allAvailable(false).items(items).build();
    }

    public boolean hasEnoughStock(String variantId, int requestedQty) {
        return items.stream()
                .filter(item -> item.getVariantId().equals(variantId))
                .findFirst()
                .map(item -> item.getAvailableQty() >= requestedQty)
                .orElse(false);
    }
}
