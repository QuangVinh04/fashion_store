package com.fashionstore.cart.config;

import com.fashionstore.contracts.EventTypes;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
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

    @Bean
    DirectExchange fashionEventsExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue cartItemsRemovalQueue() {
        return new Queue(CART_ITEMS_REMOVAL_QUEUE, true);
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
        return factory;
    }
}
