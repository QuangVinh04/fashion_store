package com.fashionstore.catalog.inventory.dto;

/**
 * Read-only stock aggregate for a product, calculated from active variants only.
 */
public record ProductStockSummary(
        String productId,
        long totalQuantity,
        long totalReserved,
        long totalAvailable,
        long activeVariantCount,
        long outOfStockVariantCount,
        long lowStockVariantCount,
        ProductStockState state
) {
}
