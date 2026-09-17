package com.fashionstore.contracts.inventory.command;

public record RestockInventoryCommand(
        String orderId
) {
}
