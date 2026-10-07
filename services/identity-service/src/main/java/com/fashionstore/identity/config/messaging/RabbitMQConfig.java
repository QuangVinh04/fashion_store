package com.fashionstore.identity.config.messaging;

import com.fashionstore.common.messaging.RabbitTopology;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * identity-service chỉ phát event (email, đổi avatar), không tiêu thụ queue nào. Vẫn khai báo exchange
 * dùng chung để có thể publish ngay cả khi service này khởi động trước mọi service khác.
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    DirectExchange fashionEventsExchange() {
        return RabbitTopology.eventsExchange();
    }

    @Bean
    Declarables unroutedTopology() {
        return RabbitTopology.unroutedTopology();
    }
}
