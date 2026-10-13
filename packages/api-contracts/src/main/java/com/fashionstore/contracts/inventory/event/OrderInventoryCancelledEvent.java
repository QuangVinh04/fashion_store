package com.fashionstore.contracts.inventory.event;

public record OrderInventoryCancelledEvent(String cancellationId,String orderId,String reservationId) {}
