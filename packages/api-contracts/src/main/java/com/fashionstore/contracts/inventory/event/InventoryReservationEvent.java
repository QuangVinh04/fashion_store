package com.fashionstore.contracts.inventory.event;

public record InventoryReservationEvent(
        String orderId,
        String reservationId
) {
}
