package com.fashionstore.contracts.inventory.command;

import java.util.List;

public record ReservationInventoryCommand(
        String orderId,
        String userId,
        List<InventoryItem> items
) {
}
