package com.fashionstore.contracts.inventory.command;

public record CancelOrderInventoryCommand(String cancellationId,String orderId,String reservationId) {}
