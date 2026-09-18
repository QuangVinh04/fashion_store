package com.fashionstore.contracts.order.dto;

public record VerifyPurchaseResponse(
        boolean purchased,
        String orderId
) {
}
