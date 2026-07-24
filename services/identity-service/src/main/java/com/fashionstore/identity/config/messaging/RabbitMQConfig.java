package com.fashionstore.identity.config.messaging;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "fashion.events";

    @Bean
    DirectExchange fashionEventsExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }
}
