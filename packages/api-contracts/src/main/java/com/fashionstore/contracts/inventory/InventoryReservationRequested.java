package com.fashionstore.contracts.inventory;

import java.util.List;

public record InventoryReservationRequested(
        String orderId,
        String userId,
        List<InventoryItem> items
) {
}
