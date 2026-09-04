package com.fashionstore.inventory.event;

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
