package com.fashionstore.catalog.config;

public final class RabbitMQNames {

    public static final String EXCHANGE = "fashion.events";
    public static final String OUTBOX_EVENT_ID_HEADER = "outboxEventId";

    // product.variant.stock là event nội bộ, không thuộc api-contracts, nên vẫn giữ literal riêng ở đây.
    public static final String PRODUCT_VARIANT_STOCK_ROUTING_KEY = "product.variant.stock";

    // Routing key của saga luôn bằng đúng eventType (EventTypes), bind thẳng vào đó thay vì lặp giá trị.

    public static final String INVENTORY_PRODUCT_VARIANT_STOCK_QUEUE = "inventory.product-variant-stock";
    public static final String INVENTORY_RESERVATION_REQUESTED_QUEUE = "inventory.reservation-requested";
    public static final String INVENTORY_CONFIRMATION_REQUESTED_QUEUE = "inventory.confirmation-requested";
    public static final String INVENTORY_RELEASE_REQUESTED_QUEUE = "inventory.release-requested";

    public static final String INVENTORY_STOCK_CONSUMER = "inventory-stock-consumer";
    public static final String INVENTORY_RESERVATION_CONSUMER = "inventory-reservation-consumer";

    private RabbitMQNames() {
    }
}
