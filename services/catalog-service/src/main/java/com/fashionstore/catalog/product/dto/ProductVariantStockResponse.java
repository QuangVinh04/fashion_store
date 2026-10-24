package com.fashionstore.catalog.product.dto;

/**
 * Read-only inventory snapshot attached to an admin product variant.
 */
public record ProductVariantStockResponse(
        int quantity,
        int reservedQuantity,
        int availableQuantity,
        int minThreshold
) {
}
