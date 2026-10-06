package com.fashionstore.catalog.config;

import com.fashionstore.test.messaging.RabbitListenerContract;
import java.util.List;

class RabbitConsumerContractTest extends RabbitListenerContract {
    protected Class<?> rabbitConfiguration() { return RabbitMQConfig.class; }
    protected String deadLetterQueue() { return "catalog.dlq"; }
    protected List<String> sourceQueues() {
        return List.of("inventory.reservation-requested", "inventory.confirmation-requested",
                "inventory.release-requested", "inventory.restock-requested", "catalog.profile-avatar-changed");
    }
}
