package com.fashionstore.catalog.product.dto;

/**
 * Read-only stock aggregate exposed to the backoffice product pages.
 */
public record ProductStockSummaryResponse(
        long totalQuantity,
        long totalReserved,
        long totalAvailable,
        long activeVariantCount,
        long outOfStockVariantCount,
        long lowStockVariantCount,
        String state
) {
}
