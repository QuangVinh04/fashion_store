package com.fashionstore.catalog.event;

public record ProductVariantStockEvent(
        String variantId,
        Integer quantity,
        Action action
) {
    public enum Action {
        UPSERT,
        DELETE
    }
}
