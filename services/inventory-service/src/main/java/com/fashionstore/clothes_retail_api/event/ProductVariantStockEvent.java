package com.fashionstore.clothes_retail_api.event;

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
