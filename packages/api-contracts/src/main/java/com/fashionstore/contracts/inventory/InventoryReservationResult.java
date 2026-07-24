package com.fashionstore.contracts.inventory;

public record InventoryReservationResult(
        String orderId,
        String reservationId,
        String rejectionReason
) {
}
