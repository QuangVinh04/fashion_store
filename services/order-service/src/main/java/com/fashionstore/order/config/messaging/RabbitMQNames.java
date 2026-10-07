package com.fashionstore.order.config.messaging;

/**
 * Tên queue mà order-service sở hữu. Exchange, DLX và header dùng chung nằm ở
 * {@link com.fashionstore.common.messaging.RabbitTopology}; routing key nằm ở {@code EventTypes}.
 */
public final class RabbitMQNames {

    public static final String ORDER_DEAD_LETTER_QUEUE = "order.dlq";

    // Mỗi reply của saga có queue riêng: handler nào lỗi thì chỉ queue đó dồn lại, các bước khác vẫn chạy.
    public static final String ORDER_INVENTORY_RESERVED_QUEUE = "order.inventory-reserved";
    public static final String ORDER_INVENTORY_REJECTED_QUEUE = "order.inventory-rejected";
    public static final String ORDER_INVENTORY_CONFIRMED_QUEUE = "order.inventory-confirmed";
    public static final String ORDER_INVENTORY_RELEASED_QUEUE = "order.inventory-released";
    public static final String ORDER_PAYMENT_INITIATED_QUEUE = "order.payment-initiated";
    public static final String ORDER_PAYMENT_COMPLETED_QUEUE = "order.payment-completed";
    public static final String ORDER_PAYMENT_FAILED_QUEUE = "order.payment-failed";
    public static final String ORDER_PAYMENT_CANCELLED_QUEUE = "order.payment-cancelled";
    public static final String ORDER_PAYMENT_CANCELLATION_REJECTED_QUEUE = "order.payment-cancellation-rejected";
    public static final String ORDER_PAYMENT_REFUNDED_QUEUE = "order.payment-refunded";
    public static final String ORDER_PAYMENT_REFUND_REJECTED_QUEUE = "order.payment-refund-rejected";

    private RabbitMQNames() {
    }
}
