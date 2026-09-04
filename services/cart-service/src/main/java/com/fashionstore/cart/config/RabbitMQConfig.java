package com.fashionstore.cart.config;

import com.fashionstore.contracts.common.EventTypes;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "fashion.events";
    public static final String OUTBOX_EVENT_ID_HEADER = "outboxEventId";
    public static final String CART_ITEMS_REMOVAL_QUEUE = "cart.items-removal";

    /** Hết retry thì message rơi về đây thay vì bị drop im lặng — cùng mẫu với order/payment/notification. */
    public static final String DEAD_LETTER_EXCHANGE = "fashion.events.dlx";
    public static final String CART_DEAD_LETTER_QUEUE = "cart.dlq";

    @Bean
    DirectExchange fashionEventsExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue cartDeadLetterQueue() {
        return QueueBuilder.durable(CART_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding cartDeadLetterBinding(Queue cartDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(cartDeadLetterQueue)
                .to(deadLetterExchange)
                .with(CART_DEAD_LETTER_QUEUE);
    }

    @Bean
    Queue cartItemsRemovalQueue() {
        return QueueBuilder.durable(CART_ITEMS_REMOVAL_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(CART_DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    Binding cartItemsRemovalBinding(Queue cartItemsRemovalQueue, DirectExchange fashionEventsExchange) {
        return BindingBuilder.bind(cartItemsRemovalQueue)
                .to(fashionEventsExchange)
                .with(EventTypes.CART_ITEMS_REMOVAL_REQUESTED);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        // Cạn retry thì reject không requeue -> message đi sang DLQ thay vì quay vòng vô hạn.
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
