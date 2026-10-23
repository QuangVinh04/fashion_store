package com.fashionstore.order.saga.config.messaging;

import com.fashionstore.order.shared.config.messaging.RabbitMQNames;
import com.fashionstore.common.messaging.RabbitTopology;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.order.saga.event.OrderCancellationEventListener;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;

@Configuration
public class OrderCancellationTopology {
    @Bean Declarables orderCancellationWorkflowTopology() {
        var queue=RabbitTopology.consumerQueue(OrderCancellationEventListener.QUEUE,RabbitMQNames.ORDER_DEAD_LETTER_QUEUE);
        var exchange=RabbitTopology.eventsExchange();
        return new Declarables(queue,RabbitTopology.bind(queue,exchange,EventTypes.ORDER_INVENTORY_CANCELLED),
                RabbitTopology.bind(queue,exchange,EventTypes.ORDER_INVENTORY_CANCELLATION_REJECTED),
                RabbitTopology.bind(queue,exchange,EventTypes.ORDER_FUNDS_REFUND_CREDITED),
                RabbitTopology.bind(queue,exchange,EventTypes.ORDER_FUNDS_REFUND_REJECTED));
    }
}
