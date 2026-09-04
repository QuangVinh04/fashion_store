package com.fashionstore.product.config.messaging;

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
    Queue paymentSagaCommandQueue() {
        return new Queue(RabbitMQNames.PAYMENT_SAGA_COMMAND_QUEUE, true);
    }

    @Bean
    Binding inventoryProductVariantStockBinding(Queue inventoryProductVariantStockQueue,
                                                DirectExchange fashionEventsExchange) {
        return BindingBuilder.bind(inventoryProductVariantStockQueue)
                .to(fashionEventsExchange)
                .with(RabbitMQNames.PRODUCT_VARIANT_STOCK_ROUTING_KEY);
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
