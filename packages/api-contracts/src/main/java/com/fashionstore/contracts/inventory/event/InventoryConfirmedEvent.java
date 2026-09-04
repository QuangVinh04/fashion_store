package com.fashionstore.contracts.inventory.event;

public record InventoryConfirmedEvent(
        String orderId,
        String reservationId
) {
}
