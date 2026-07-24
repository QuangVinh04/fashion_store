package com.fashionstore.product.config.messaging;

public final class RabbitMQNames {

    public static final String EXCHANGE = "fashion.events";
    public static final String OUTBOX_EVENT_ID_HEADER = "outboxEventId";

    public static final String PRODUCT_VARIANT_STOCK_ROUTING_KEY = "product.variant.stock";
    public static final String INVENTORY_RESERVATION_REQUESTED_ROUTING_KEY = "inventory.reservation.requested";
    public static final String INVENTORY_CONFIRMATION_REQUESTED_ROUTING_KEY = "inventory.confirmation.requested";
    public static final String INVENTORY_RELEASE_REQUESTED_ROUTING_KEY = "inventory.release.requested";

    public static final String INVENTORY_PRODUCT_VARIANT_STOCK_QUEUE = "inventory.product-variant-stock";
    public static final String INVENTORY_RESERVATION_REQUESTED_QUEUE = "inventory.reservation-requested";
    public static final String INVENTORY_CONFIRMATION_REQUESTED_QUEUE = "inventory.confirmation-requested";
    public static final String INVENTORY_RELEASE_REQUESTED_QUEUE = "inventory.release-requested";

    public static final String INVENTORY_STOCK_CONSUMER = "inventory-stock-consumer";
    public static final String INVENTORY_RESERVATION_CONSUMER = "inventory-reservation-consumer";

    private RabbitMQNames() {
    }
}
