package com.fashionstore.contracts.order;

import java.time.LocalDateTime;
import java.time.Instant;
import java.math.BigDecimal;

/**
 * Announces that an order has been delivered so deferred COD collection can be settled.
 *
 * @param orderId order that reached the delivered state
 * @param deliveredAt delivery completion time
 */
public record OrderDeliveredEvent(String orderId, LocalDateTime deliveredAt,
        BigDecimal codCollectedAmount, Instant codCollectedAt, String trackingCode, String carrierShopId) {

    public OrderDeliveredEvent(String orderId, LocalDateTime deliveredAt) {
        this(orderId, deliveredAt, null, null, null, null);
    }
}
