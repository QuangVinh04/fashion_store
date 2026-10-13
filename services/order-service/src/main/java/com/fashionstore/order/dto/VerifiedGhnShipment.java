package com.fashionstore.order.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record VerifiedGhnShipment(
        String orderCode,
        String shopId,
        String clientOrderCode,
        String status,
        BigDecimal codAmount,
        boolean codCollected,
        Instant codCollectedAt,
        boolean codTransferred,
        Instant codTransferredAt) {}
