package com.fashionstore.order.shipment.dto;

import java.math.BigDecimal;
import java.util.List;

/** What the carrier needs to ship a customer order; the caller maps its own order onto this. */
public record CarrierOrder(
        String recipientName,
        String recipientPhone,
        String addressLine,
        BigDecimal codAmount,
        List<Item> items) {

    public record Item(String name, String code, int quantity, BigDecimal price) {}
}
