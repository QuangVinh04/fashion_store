package com.fashionstore.order.config.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import com.fashionstore.contracts.common.EventTypes;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(RabbitMQNames.EXCHANGE);
    }

    // ----- Dead letter: hết retry thì message rơi về đây, không bị drop im lặng -----

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(RabbitMQNames.DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue orderDeadLetterQueue() {
        return QueueBuilder.durable(RabbitMQNames.ORDER_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding orderDeadLetterBinding(DirectExchange deadLetterExchange, Queue orderDeadLetterQueue) {
        return BindingBuilder.bind(orderDeadLetterQueue)
                .to(deadLetterExchange)
                .with(RabbitMQNames.ORDER_DEAD_LETTER_QUEUE);
    }

    private static Queue sagaQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(RabbitMQNames.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitMQNames.ORDER_DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    public Queue orderInventoryReservedQueue() {
        return sagaQueue(RabbitMQNames.ORDER_INVENTORY_RESERVED_QUEUE);
    }

    @Bean
    public Queue orderInventoryRejectedQueue() {
        return sagaQueue(RabbitMQNames.ORDER_INVENTORY_REJECTED_QUEUE);
    }

    @Bean
    public Queue orderInventoryConfirmedQueue() {
        return sagaQueue(RabbitMQNames.ORDER_INVENTORY_CONFIRMED_QUEUE);
    }

    @Bean
    public Queue orderInventoryReleasedQueue() {
        return sagaQueue(RabbitMQNames.ORDER_INVENTORY_RELEASED_QUEUE);
    }

    @Bean
    public Queue orderPaymentCompletedQueue() {
        return sagaQueue(RabbitMQNames.ORDER_PAYMENT_COMPLETED_QUEUE);
    }

    @Bean
    public Queue orderPaymentFailedQueue() {
        return sagaQueue(RabbitMQNames.ORDER_PAYMENT_FAILED_QUEUE);
    }

    @Bean
    public Queue orderPaymentCancelledQueue() {
        return sagaQueue(RabbitMQNames.ORDER_PAYMENT_CANCELLED_QUEUE);
    }

    @Bean
    public Queue orderPaymentCancellationRejectedQueue() {
        return sagaQueue(RabbitMQNames.ORDER_PAYMENT_CANCELLATION_REJECTED_QUEUE);
    }

    @Bean
    public Queue orderPaymentRefundedQueue() {
        return sagaQueue(RabbitMQNames.ORDER_PAYMENT_REFUNDED_QUEUE);
    }

    @Bean
    public Queue orderPaymentRefundRejectedQueue() {
        return sagaQueue(RabbitMQNames.ORDER_PAYMENT_REFUND_REJECTED_QUEUE);
    }

    @Bean
    public Binding orderInventoryReservedBinding(DirectExchange directExchange, Queue orderInventoryReservedQueue) {
        return BindingBuilder.bind(orderInventoryReservedQueue)
                .to(directExchange)
                .with(EventTypes.INVENTORY_RESERVED);
    }

    @Bean
    public Binding orderInventoryRejectedBinding(DirectExchange directExchange, Queue orderInventoryRejectedQueue) {
        return BindingBuilder.bind(orderInventoryRejectedQueue)
                .to(directExchange)
                .with(EventTypes.INVENTORY_REJECTED);
    }

    @Bean
    public Binding orderInventoryConfirmedBinding(DirectExchange directExchange, Queue orderInventoryConfirmedQueue) {
        return BindingBuilder.bind(orderInventoryConfirmedQueue)
                .to(directExchange)
                .with(EventTypes.INVENTORY_CONFIRMED);
    }

    @Bean
    public Binding orderInventoryReleasedBinding(DirectExchange directExchange, Queue orderInventoryReleasedQueue) {
        return BindingBuilder.bind(orderInventoryReleasedQueue)
                .to(directExchange)
                .with(EventTypes.INVENTORY_RELEASED);
    }

    @Bean
    public Binding orderPaymentCompletedBinding(DirectExchange directExchange, Queue orderPaymentCompletedQueue) {
        return BindingBuilder.bind(orderPaymentCompletedQueue)
                .to(directExchange)
                .with(EventTypes.PAYMENT_COMPLETED);
    }

    @Bean
    public Binding orderPaymentFailedBinding(DirectExchange directExchange, Queue orderPaymentFailedQueue) {
        return BindingBuilder.bind(orderPaymentFailedQueue)
                .to(directExchange)
                .with(EventTypes.PAYMENT_FAILED);
    }

    @Bean
    public Binding orderPaymentCancelledBinding(DirectExchange directExchange, Queue orderPaymentCancelledQueue) {
        return BindingBuilder.bind(orderPaymentCancelledQueue)
                .to(directExchange)
                .with(EventTypes.PAYMENT_CANCELLED);
    }

    @Bean
    public Binding orderPaymentCancellationRejectedBinding(
            DirectExchange directExchange,
            Queue orderPaymentCancellationRejectedQueue
    ) {
        return BindingBuilder.bind(orderPaymentCancellationRejectedQueue)
                .to(directExchange)
                .with(EventTypes.PAYMENT_CANCELLATION_REJECTED);
    }

    @Bean
    public Binding orderPaymentRefundedBinding(DirectExchange directExchange, Queue orderPaymentRefundedQueue) {
        return BindingBuilder.bind(orderPaymentRefundedQueue)
                .to(directExchange)
                .with(EventTypes.PAYMENT_REFUNDED);
    }

    @Bean
    public Binding orderPaymentRefundRejectedBinding(
            DirectExchange directExchange,
            Queue orderPaymentRefundRejectedQueue
    ) {
        return BindingBuilder.bind(orderPaymentRefundRejectedQueue)
                .to(directExchange)
                .with(EventTypes.PAYMENT_REFUND_REJECTED);
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
        // Cạn retry thì reject không requeue -> message đi sang DLQ thay vì quay vòng vô hạn.
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
