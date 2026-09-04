package com.fashionstore.contracts.inventory.command;

public record ReleaseInventoryCommand(
        String orderId,
        String reservationId
) {
}
