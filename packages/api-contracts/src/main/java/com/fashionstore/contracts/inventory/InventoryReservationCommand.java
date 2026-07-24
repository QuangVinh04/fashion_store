package com.fashionstore.contracts.inventory;

public record InventoryReservationCommand(String orderId, String reservationId) {
}
