package com.fashionstore.contracts.order.dto;

import java.util.List;

public record VerifyPurchaseRequest(
        String userId,
        String orderId,
        List<String> variantIds
) {
}
