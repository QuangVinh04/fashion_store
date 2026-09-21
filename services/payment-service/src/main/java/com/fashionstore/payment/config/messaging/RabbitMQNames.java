package com.fashionstore.payment.config.messaging;

public final class RabbitMQNames {

    public static final String EXCHANGE = "fashion.events";
    public static final String OUTBOX_EVENT_ID_HEADER = "outboxEventId";

    // Routing key của saga luôn bằng đúng eventType (EventTypes), bind thẳng vào đó thay vì lặp giá trị.
    public static final String PAYMENT_SAGA_COMMAND_QUEUE = "payment.saga-command-v1";
    public static final String PAYMENT_ORDER_DELIVERED_QUEUE = "payment.order-delivered-v1";

    private RabbitMQNames() {
    }
}
