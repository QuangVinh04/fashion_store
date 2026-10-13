package com.fashionstore.catalog.config;

import com.fashionstore.catalog.messaging.OrderInventoryCancellationListener;
import com.fashionstore.common.messaging.RabbitTopology;
import com.fashionstore.contracts.common.EventTypes;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;

@Configuration
public class OrderInventoryCancellationTopology {
    @Bean Queue orderInventoryCancellationQueue() {
        return RabbitTopology.consumerQueue(OrderInventoryCancellationListener.QUEUE,RabbitMQNames.CATALOG_DEAD_LETTER_QUEUE);
    }
    @Bean Binding orderInventoryCancellationBinding(Queue orderInventoryCancellationQueue,DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(orderInventoryCancellationQueue,fashionEventsExchange,EventTypes.ORDER_INVENTORY_CANCELLATION_REQUESTED);
    }
}
