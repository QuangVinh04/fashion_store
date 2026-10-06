package com.fashionstore.catalog.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import com.fashionstore.contracts.common.EventTypes;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(RabbitMQNames.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue catalogDeadLetterQueue() {
        return QueueBuilder.durable(RabbitMQNames.CATALOG_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding catalogDeadLetterBinding(Queue catalogDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(catalogDeadLetterQueue).to(deadLetterExchange)
                .with(RabbitMQNames.CATALOG_DEAD_LETTER_QUEUE);
    }

    @Bean
    DirectExchange fashionEventsExchange() {
        return new DirectExchange(RabbitMQNames.EXCHANGE, true, false);
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
    Queue inventoryRestockRequestedQueue() {
        return new Queue(RabbitMQNames.INVENTORY_RESTOCK_REQUESTED_QUEUE, true);
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
    Binding inventoryRestockRequestedBinding(
            Queue inventoryRestockRequestedQueue,
            DirectExchange fashionEventsExchange
    ) {
        return BindingBuilder.bind(inventoryRestockRequestedQueue)
                .to(fashionEventsExchange)
                .with(EventTypes.INVENTORY_RESTOCK_REQUESTED);
    }

    @Bean
    Queue profileAvatarChangedQueue() {
        return new Queue(RabbitMQNames.PROFILE_AVATAR_CHANGED_QUEUE, true);
    }

    @Bean
    Binding profileAvatarChangedBinding(
            Queue profileAvatarChangedQueue,
            DirectExchange fashionEventsExchange
    ) {
        return BindingBuilder.bind(profileAvatarChangedQueue)
                .to(fashionEventsExchange)
                .with(EventTypes.PROFILE_AVATAR_CHANGED);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

}
