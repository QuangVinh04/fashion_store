package com.fashionstore.payment.config.messaging;

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
    Queue paymentDeadLetterQueue() {
        return QueueBuilder.durable(RabbitMQNames.PAYMENT_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding paymentDeadLetterBinding(Queue paymentDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(paymentDeadLetterQueue).to(deadLetterExchange)
                .with(RabbitMQNames.PAYMENT_DEAD_LETTER_QUEUE);
    }

    @Bean
    DirectExchange fashionEventsExchange() {
        return new DirectExchange(RabbitMQNames.EXCHANGE, true, false);
    }

    @Bean
    Queue paymentSagaCommandQueue() {
        return new Queue(RabbitMQNames.PAYMENT_SAGA_COMMAND_QUEUE, true);
    }

    @Bean
    Queue paymentOrderDeliveredQueue() {
        return new Queue(RabbitMQNames.PAYMENT_ORDER_DELIVERED_QUEUE, true);
    }

    @Bean
    Binding paymentRequestedBinding(Queue paymentSagaCommandQueue,
                                    DirectExchange fashionEventsExchange) {
        return BindingBuilder.bind(paymentSagaCommandQueue)
                .to(fashionEventsExchange)
                .with(EventTypes.PAYMENT_REQUESTED);
    }

    @Bean
    Binding paymentCancellationRequestedBinding(
            Queue paymentSagaCommandQueue,
            DirectExchange fashionEventsExchange
    ) {
        return BindingBuilder.bind(paymentSagaCommandQueue)
                .to(fashionEventsExchange)
                .with(EventTypes.PAYMENT_CANCELLATION_REQUESTED);
    }

    @Bean
    Binding paymentRefundRequestedBinding(
            Queue paymentSagaCommandQueue,
            DirectExchange fashionEventsExchange
    ) {
        return BindingBuilder.bind(paymentSagaCommandQueue)
                .to(fashionEventsExchange)
                .with(EventTypes.PAYMENT_REFUND_REQUESTED);
    }

    @Bean
    Binding paymentOrderDeliveredBinding(
            Queue paymentOrderDeliveredQueue,
            DirectExchange fashionEventsExchange
    ) {
        return BindingBuilder.bind(paymentOrderDeliveredQueue)
                .to(fashionEventsExchange)
                .with(EventTypes.ORDER_DELIVERED);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

}
