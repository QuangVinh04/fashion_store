package com.fashionstore.notification.config;

import com.fashionstore.common.messaging.RabbitTopology;
import com.fashionstore.contracts.common.EventTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topology của notification-service: một queue nhận lệnh gửi email từ mọi service.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EMAIL_QUEUE = "notification.email";
    public static final String EMAIL_DEAD_LETTER_QUEUE = "notification.email.dlq";

    /**
     * Service này không có spring-web nên Spring Boot không tự tạo ObjectMapper; tự khai báo và đăng ký
     * module (JavaTimeModule cho {@code Instant} trong EventEnvelope).
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    /** Listener nhận thẳng {@code EventEnvelope<?>}: converter này đọc JSON thành object theo kiểu tham số. */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    // ----- Hạ tầng dùng chung: khai báo y hệt ở mọi service (xem RabbitTopology) -----

    @Bean
    DirectExchange fashionEventsExchange() {
        return RabbitTopology.eventsExchange();
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return RabbitTopology.deadLetterExchange();
    }

    @Bean
    Declarables unroutedTopology() {
        return RabbitTopology.unroutedTopology();
    }

    // ----- Email -----

    @Bean
    Queue emailDeadLetterQueue() {
        return RabbitTopology.deadLetterQueue(EMAIL_DEAD_LETTER_QUEUE);
    }

    @Bean
    Binding emailDeadLetterBinding(Queue emailDeadLetterQueue, DirectExchange deadLetterExchange) {
        return RabbitTopology.deadLetterBinding(emailDeadLetterQueue, deadLetterExchange);
    }

    @Bean
    Queue emailQueue() {
        return RabbitTopology.consumerQueue(EMAIL_QUEUE, EMAIL_DEAD_LETTER_QUEUE);
    }

    @Bean
    Binding emailBinding(Queue emailQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(emailQueue, fashionEventsExchange, EventTypes.NOTIFICATION_EMAIL_REQUESTED);
    }
}
