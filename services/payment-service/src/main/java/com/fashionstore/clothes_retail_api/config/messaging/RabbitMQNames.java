package com.fashionstore.product.config.messaging;

public final class RabbitMQNames {

    public static final String EXCHANGE = "fashion.events";
    public static final String OUTBOX_EVENT_ID_HEADER = "outboxEventId";

    public static final String PRODUCT_VARIANT_STOCK_ROUTING_KEY = "product.variant.stock";
    public static final String PAYMENT_REQUESTED_ROUTING_KEY = "payment.requested";
    public static final String PAYMENT_CANCELLATION_REQUESTED_ROUTING_KEY = "payment.cancellation.requested";

    public static final String INVENTORY_PRODUCT_VARIANT_STOCK_QUEUE = "inventory.product-variant-stock";
    public static final String PAYMENT_SAGA_COMMAND_QUEUE = "payment.saga-command-v1";

    public static final String INVENTORY_STOCK_CONSUMER = "inventory-stock-consumer";

    private RabbitMQNames() {
    }
}
