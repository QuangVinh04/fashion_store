package com.fashionstore.contracts.inventory.command;

public record ConfirmInventoryCommand(String orderId, String reservationId) {
}
