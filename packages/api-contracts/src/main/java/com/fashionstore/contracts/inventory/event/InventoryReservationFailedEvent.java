package com.fashionstore.contracts.inventory.event;

public record InventoryReservationFailedEvent(
        String orderId,
        String failureCode,
        String failureMessage
) {
}
