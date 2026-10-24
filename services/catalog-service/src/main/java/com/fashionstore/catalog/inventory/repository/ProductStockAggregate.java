package com.fashionstore.catalog.inventory.repository;

/**
 * Projection of inventory totals grouped by product for admin product reads.
 */
public interface ProductStockAggregate {
    String getProductId();

    Long getTotalQuantity();

    Long getTotalReserved();

    Long getTotalAvailable();

    Long getActiveVariantCount();

    Long getOutOfStockVariantCount();

    Long getLowStockVariantCount();
}
