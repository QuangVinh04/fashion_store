package com.fashionstore.contracts.inventory.event;

public record OrderInventoryCancellationRejectedEvent(String cancellationId,String orderId,String code,String reason) {}
