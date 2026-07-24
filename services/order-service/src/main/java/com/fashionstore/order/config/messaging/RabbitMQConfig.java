package com.fashionstore.order.config.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(RabbitMQNames.EXCHANGE);
    }

    @Bean
    public Queue orderInventoryReservedQueue() {
        return new Queue(RabbitMQNames.ORDER_INVENTORY_RESERVED_QUEUE, true);
    }

    @Bean
    public Queue orderInventoryRejectedQueue() {
        return new Queue(RabbitMQNames.ORDER_INVENTORY_REJECTED_QUEUE, true);
    }

    @Bean
    public Queue orderInventoryConfirmedQueue() {
        return new Queue(RabbitMQNames.ORDER_INVENTORY_CONFIRMED_QUEUE, true);
    }

    @Bean
    public Queue orderInventoryReleasedQueue() {
        return new Queue(RabbitMQNames.ORDER_INVENTORY_RELEASED_QUEUE, true);
    }

    @Bean
    public Queue orderPaymentCompletedQueue() {
        return new Queue(RabbitMQNames.ORDER_PAYMENT_COMPLETED_QUEUE, true);
    }

    @Bean
    public Queue orderPaymentFailedQueue() {
        return new Queue(RabbitMQNames.ORDER_PAYMENT_FAILED_QUEUE, true);
    }

    @Bean
    public Queue orderPaymentCancelledQueue() {
        return new Queue(RabbitMQNames.ORDER_PAYMENT_CANCELLED_QUEUE, true);
    }

    @Bean
    public Queue orderPaymentCancellationRejectedQueue() {
        return new Queue(RabbitMQNames.ORDER_PAYMENT_CANCELLATION_REJECTED_QUEUE, true);
    }

    @Bean
    public Binding orderInventoryReservedBinding(DirectExchange directExchange, Queue orderInventoryReservedQueue) {
        return BindingBuilder.bind(orderInventoryReservedQueue)
                .to(directExchange)
                .with(RabbitMQNames.INVENTORY_RESERVED_ROUTING_KEY);
    }

    @Bean
    public Binding orderInventoryRejectedBinding(DirectExchange directExchange, Queue orderInventoryRejectedQueue) {
        return BindingBuilder.bind(orderInventoryRejectedQueue)
                .to(directExchange)
                .with(RabbitMQNames.INVENTORY_REJECTED_ROUTING_KEY);
    }

    @Bean
    public Binding orderInventoryConfirmedBinding(DirectExchange directExchange, Queue orderInventoryConfirmedQueue) {
        return BindingBuilder.bind(orderInventoryConfirmedQueue)
                .to(directExchange)
                .with(RabbitMQNames.INVENTORY_CONFIRMED_ROUTING_KEY);
    }

    @Bean
    public Binding orderInventoryReleasedBinding(DirectExchange directExchange, Queue orderInventoryReleasedQueue) {
        return BindingBuilder.bind(orderInventoryReleasedQueue)
                .to(directExchange)
                .with(RabbitMQNames.INVENTORY_RELEASED_ROUTING_KEY);
    }

    @Bean
    public Binding orderPaymentCompletedBinding(DirectExchange directExchange, Queue orderPaymentCompletedQueue) {
        return BindingBuilder.bind(orderPaymentCompletedQueue)
                .to(directExchange)
                .with(RabbitMQNames.PAYMENT_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public Binding orderPaymentFailedBinding(DirectExchange directExchange, Queue orderPaymentFailedQueue) {
        return BindingBuilder.bind(orderPaymentFailedQueue)
                .to(directExchange)
                .with(RabbitMQNames.PAYMENT_FAILED_ROUTING_KEY);
    }

    @Bean
    public Binding orderPaymentCancelledBinding(DirectExchange directExchange, Queue orderPaymentCancelledQueue) {
        return BindingBuilder.bind(orderPaymentCancelledQueue)
                .to(directExchange)
                .with(RabbitMQNames.PAYMENT_CANCELLED_ROUTING_KEY);
    }

    @Bean
    public Binding orderPaymentCancellationRejectedBinding(
            DirectExchange directExchange,
            Queue orderPaymentCancellationRejectedQueue
    ) {
        return BindingBuilder.bind(orderPaymentCancellationRejectedQueue)
                .to(directExchange)
                .with(RabbitMQNames.PAYMENT_CANCELLATION_REJECTED_ROUTING_KEY);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setExchange(RabbitMQNames.EXCHANGE);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
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
