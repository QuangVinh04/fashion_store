package com.fashionstore.order.config.messaging;

public final class RabbitMQNames {

    public static final String EXCHANGE = "fashion.events";
    public static final String OUTBOX_EVENT_ID_HEADER = "outboxEventId";

    public static final String INVENTORY_RESERVED_ROUTING_KEY = "inventory.reserved";
    public static final String INVENTORY_REJECTED_ROUTING_KEY = "inventory.rejected";
    public static final String INVENTORY_CONFIRMED_ROUTING_KEY = "inventory.confirmed";
    public static final String INVENTORY_RELEASED_ROUTING_KEY = "inventory.released";
    public static final String PAYMENT_COMPLETED_ROUTING_KEY = "payment.completed";
    public static final String PAYMENT_FAILED_ROUTING_KEY = "payment.failed";
    public static final String PAYMENT_CANCELLED_ROUTING_KEY = "payment.cancelled";
    public static final String PAYMENT_CANCELLATION_REJECTED_ROUTING_KEY = "payment.cancellation.rejected";

    public static final String ORDER_INVENTORY_RESERVED_QUEUE = "order.inventory-reserved";
    public static final String ORDER_INVENTORY_REJECTED_QUEUE = "order.inventory-rejected";
    public static final String ORDER_INVENTORY_CONFIRMED_QUEUE = "order.inventory-confirmed";
    public static final String ORDER_INVENTORY_RELEASED_QUEUE = "order.inventory-released";
    public static final String ORDER_PAYMENT_COMPLETED_QUEUE = "order.payment-completed";
    public static final String ORDER_PAYMENT_FAILED_QUEUE = "order.payment-failed";
    public static final String ORDER_PAYMENT_CANCELLED_QUEUE = "order.payment-cancelled";
    public static final String ORDER_PAYMENT_CANCELLATION_REJECTED_QUEUE = "order.payment-cancellation-rejected";

    private RabbitMQNames() {
    }
}
