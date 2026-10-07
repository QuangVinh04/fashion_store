package com.fashionstore.payment.config.messaging;

/**
 * Tên queue mà payment-service sở hữu. Exchange, DLX và header dùng chung nằm ở
 * {@link com.fashionstore.common.messaging.RabbitTopology}; routing key nằm ở {@code EventTypes}.
 */
public final class RabbitMQNames {

    public static final String PAYMENT_DEAD_LETTER_QUEUE = "payment.dlq";

    public static final String PAYMENT_SAGA_COMMAND_QUEUE = "payment.saga-command-v1";
    public static final String PAYMENT_ORDER_DELIVERED_QUEUE = "payment.order-delivered-v1";

    private RabbitMQNames() {
    }
}
