package com.fashionstore.contracts.order;

public record OrderCancelledEvent(
        String orderId,
        String customerId,
        String reason
) {
}
