package com.fashionstore.contracts.inventory.event;

public record InventoryReleasedEvent(
        String orderId,
        String reservationId
) {
}
