package com.fashionstore.order.config.messaging;

import com.fashionstore.test.messaging.RabbitListenerContract;
import java.util.List;

class RabbitConsumerContractTest extends RabbitListenerContract {
    protected Class<?> rabbitConfiguration() { return RabbitMQConfig.class; }
    protected String deadLetterQueue() { return "order.dlq"; }
    protected List<String> sourceQueues() {
        return List.of("order.inventory-reserved", "order.inventory-rejected", "order.inventory-confirmed",
                "order.inventory-released", "order.payment-initiated", "order.payment-completed",
                "order.payment-failed", "order.payment-cancelled", "order.payment-cancellation-rejected",
                "order.payment-refunded", "order.payment-refund-rejected");
    }
}
