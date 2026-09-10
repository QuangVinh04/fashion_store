package com.fashionstore.contracts.order;

public record OrderConfirmedEvent(
        String orderId,
        String customerId,
        String checkoutId
) {
}
