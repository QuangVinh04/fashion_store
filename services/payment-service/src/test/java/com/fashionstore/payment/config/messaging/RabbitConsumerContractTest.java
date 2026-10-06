package com.fashionstore.payment.config.messaging;

import com.fashionstore.test.messaging.RabbitListenerContract;
import java.util.List;

class RabbitConsumerContractTest extends RabbitListenerContract {
    protected Class<?> rabbitConfiguration() { return RabbitMQConfig.class; }
    protected String deadLetterQueue() { return "payment.dlq"; }
    protected List<String> sourceQueues() {
        return List.of("payment.saga-command-v1", "payment.order-delivered-v1");
    }
}
