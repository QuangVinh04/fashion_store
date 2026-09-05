package com.fashionstore.catalog.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import com.fashionstore.contracts.common.EventTypes;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    DirectExchange fashionEventsExchange() {
        return new DirectExchange(RabbitMQNames.EXCHANGE, true, false);
    }

    @Bean
    Queue inventoryProductVariantStockQueue() {
        return new Queue(RabbitMQNames.INVENTORY_PRODUCT_VARIANT_STOCK_QUEUE, true);
    }

    @Bean
    Queue inventoryReservationRequestedQueue() {
        return new Queue(RabbitMQNames.INVENTORY_RESERVATION_REQUESTED_QUEUE, true);
    }

    @Bean
    Queue inventoryConfirmationRequestedQueue() {
        return new Queue(RabbitMQNames.INVENTORY_CONFIRMATION_REQUESTED_QUEUE, true);
    }

    @Bean
    Queue inventoryReleaseRequestedQueue() {
        return new Queue(RabbitMQNames.INVENTORY_RELEASE_REQUESTED_QUEUE, true);
    }

    @Bean
    Binding inventoryProductVariantStockBinding(Queue inventoryProductVariantStockQueue,
                                                DirectExchange fashionEventsExchange) {
        return BindingBuilder.bind(inventoryProductVariantStockQueue)
                .to(fashionEventsExchange)
                .with(RabbitMQNames.PRODUCT_VARIANT_STOCK_ROUTING_KEY);
    }

    @Bean
    Binding inventoryReservationRequestedBinding(
            Queue inventoryReservationRequestedQueue,
            DirectExchange fashionEventsExchange
    ) {
        return BindingBuilder.bind(inventoryReservationRequestedQueue)
                .to(fashionEventsExchange)
                .with(EventTypes.INVENTORY_RESERVATION_REQUESTED);
    }

    @Bean
    Binding inventoryConfirmationRequestedBinding(
            Queue inventoryConfirmationRequestedQueue,
            DirectExchange fashionEventsExchange
    ) {
        return BindingBuilder.bind(inventoryConfirmationRequestedQueue)
                .to(fashionEventsExchange)
                .with(EventTypes.INVENTORY_CONFIRMATION_REQUESTED);
    }

    @Bean
    Binding inventoryReleaseRequestedBinding(
            Queue inventoryReleaseRequestedQueue,
            DirectExchange fashionEventsExchange
    ) {
        return BindingBuilder.bind(inventoryReleaseRequestedQueue)
                .to(fashionEventsExchange)
                .with(EventTypes.INVENTORY_RELEASE_REQUESTED);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                        MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        return factory;
    }
}
