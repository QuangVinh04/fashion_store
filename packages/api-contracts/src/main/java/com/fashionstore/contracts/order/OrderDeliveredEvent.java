package com.fashionstore.contracts.order;

import java.time.LocalDateTime;

/**
 * Announces that an order has been delivered so deferred COD collection can be settled.
 *
 * @param orderId order that reached the delivered state
 * @param deliveredAt delivery completion time
 */
public record OrderDeliveredEvent(String orderId, LocalDateTime deliveredAt) {
}
