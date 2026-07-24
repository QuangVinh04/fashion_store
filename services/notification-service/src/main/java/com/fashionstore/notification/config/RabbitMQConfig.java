package com.fashionstore.notification.config;

import com.fashionstore.contracts.EventTypes;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "fashion.events";
    public static final String DEAD_LETTER_EXCHANGE = "fashion.events.dlx";
    public static final String EMAIL_QUEUE = "notification.email";
    public static final String EMAIL_DEAD_LETTER_QUEUE = "notification.email.dlq";

    @Bean
    DirectExchange fashionExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue emailQueue() {
        return QueueBuilder.durable(EMAIL_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EMAIL_DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    Queue emailDeadLetterQueue() {
        return QueueBuilder.durable(EMAIL_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding emailBinding(Queue emailQueue, DirectExchange fashionExchange) {
        return BindingBuilder.bind(emailQueue)
                .to(fashionExchange)
                .with(EventTypes.NOTIFICATION_EMAIL_REQUESTED);
    }

    @Bean
    Binding emailDeadLetterBinding(Queue emailDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(emailDeadLetterQueue)
                .to(deadLetterExchange)
                .with(EMAIL_DEAD_LETTER_QUEUE);
    }
}
