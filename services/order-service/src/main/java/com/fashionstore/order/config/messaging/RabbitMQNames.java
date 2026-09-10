package com.fashionstore.order.config.messaging;

public final class RabbitMQNames {

    public static final String EXCHANGE = "fashion.events";
    public static final String INVENTORY_EXCHANGE = "inventory.events";
    public static final String OUTBOX_EVENT_ID_HEADER = "outboxEventId";

    /** Hết retry thì message rơi về đây thay vì bị drop im lặng. */
    public static final String DEAD_LETTER_EXCHANGE = "fashion.events.dlx";
    public static final String ORDER_DEAD_LETTER_QUEUE = "order.dlq";

    // Routing key luôn bằng đúng eventType (EventTypes), nên bind thẳng vào đó — không lặp giá trị ở đây.

    public static final String ORDER_INVENTORY_RESERVED_QUEUE = "order.inventory-reserved";
    public static final String ORDER_INVENTORY_REJECTED_QUEUE = "order.inventory-rejected";
    public static final String ORDER_INVENTORY_CONFIRMED_QUEUE = "order.inventory-confirmed";
    public static final String ORDER_INVENTORY_RELEASED_QUEUE = "order.inventory-released";
    public static final String ORDER_PAYMENT_COMPLETED_QUEUE = "order.payment-completed";
    public static final String ORDER_PAYMENT_FAILED_QUEUE = "order.payment-failed";
    public static final String ORDER_PAYMENT_CANCELLED_QUEUE = "order.payment-cancelled";
    public static final String ORDER_PAYMENT_CANCELLATION_REJECTED_QUEUE = "order.payment-cancellation-rejected";
    public static final String ORDER_PAYMENT_REFUNDED_QUEUE = "order.payment-refunded";
    public static final String ORDER_PAYMENT_REFUND_REJECTED_QUEUE = "order.payment-refund-rejected";

    private RabbitMQNames() {
    }
}
