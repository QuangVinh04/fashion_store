package com.fashionstore.notification.config;

import com.fashionstore.test.messaging.RabbitListenerContract;
import java.util.List;

class RabbitConsumerContractTest extends RabbitListenerContract {
    protected Class<?> rabbitConfiguration() { return RabbitMQConfig.class; }
    protected String deadLetterQueue() { return "notification.email.dlq"; }
    protected List<String> sourceQueues() { return List.of("notification.email"); }
}
