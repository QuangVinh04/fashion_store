package com.fashionstore.catalog.dto.inventory;

import java.time.LocalDateTime;

public record InventoryListItemResponse(
        String variantId,
        String productId,
        String productName,
        String sku,
        boolean active,
        int quantity,
        int reservedQuantity,
        int availableQuantity,
        int minThreshold,
        LocalDateTime updatedAt
) {}
