package com.fashionstore.identity.config.messaging;

import com.fashionstore.common.messaging.RabbitTopology;
import com.fashionstore.contracts.common.EventTypes;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * identity-service phát event (đổi avatar) và tiêu thụ event user của Keycloak (đăng ký, đổi email).
 * Vẫn khai báo exchange dùng chung để publish được ngay cả khi service này khởi động trước mọi service khác.
 */
@Configuration
public class RabbitMQConfig {

    public static final String KEYCLOAK_USER_EVENTS_QUEUE = "identity.keycloak-user-events";
    public static final String AUDIT_EVENTS_QUEUE = "identity.audit-events";
    public static final String IDENTITY_DEAD_LETTER_QUEUE = "identity.dlq";

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

    @Bean
    Queue identityDeadLetterQueue() {
        return RabbitTopology.deadLetterQueue(IDENTITY_DEAD_LETTER_QUEUE);
    }

    @Bean
    Binding identityDeadLetterBinding(Queue identityDeadLetterQueue, DirectExchange deadLetterExchange) {
        return RabbitTopology.deadLetterBinding(identityDeadLetterQueue, deadLetterExchange);
    }

    @Bean
    Queue keycloakUserEventsQueue() {
        return RabbitTopology.consumerQueue(KEYCLOAK_USER_EVENTS_QUEUE, IDENTITY_DEAD_LETTER_QUEUE);
    }

    @Bean
    Binding keycloakUserRegisteredBinding(Queue keycloakUserEventsQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(keycloakUserEventsQueue, fashionEventsExchange, EventTypes.KEYCLOAK_USER_REGISTERED);
    }

    @Bean
    Binding keycloakUserEmailUpdatedBinding(Queue keycloakUserEventsQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(keycloakUserEventsQueue, fashionEventsExchange, EventTypes.KEYCLOAK_USER_EMAIL_UPDATED);
    }

    @Bean
    Binding keycloakUserEmailVerifiedBinding(Queue keycloakUserEventsQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(keycloakUserEventsQueue, fashionEventsExchange, EventTypes.KEYCLOAK_USER_EMAIL_VERIFIED);
    }

    @Bean
    Queue auditEventsQueue() {
        return RabbitTopology.consumerQueue(AUDIT_EVENTS_QUEUE, IDENTITY_DEAD_LETTER_QUEUE);
    }

    @Bean
    Binding auditRecordedBinding(Queue auditEventsQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(auditEventsQueue, fashionEventsExchange, EventTypes.AUDIT_RECORDED);
    }

    /** Payload là JSON; kiểu đích suy ra từ tham số của @RabbitListener, không cần header __TypeId__. */
    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
