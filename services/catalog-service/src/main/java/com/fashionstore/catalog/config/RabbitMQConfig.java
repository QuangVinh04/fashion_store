package com.fashionstore.catalog.config;

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
 * Topology của catalog-service. RabbitAdmin (Spring Boot tự tạo) khai báo mọi bean Exchange/Queue/Binding
 * dưới đây lên broker khi kết nối lần đầu.
 */
@Configuration
public class RabbitMQConfig {

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

    // ----- Dead letter của catalog -----

    @Bean
    Queue catalogDeadLetterQueue() {
        return RabbitTopology.deadLetterQueue(RabbitMQNames.CATALOG_DEAD_LETTER_QUEUE);
    }

    @Bean
    Binding catalogDeadLetterBinding(Queue catalogDeadLetterQueue, DirectExchange deadLetterExchange) {
        return RabbitTopology.deadLetterBinding(catalogDeadLetterQueue, deadLetterExchange);
    }

    // ----- Lệnh kho từ saga đặt hàng -----

    @Bean
    Queue inventoryReservationRequestedQueue() {
        return consumerQueue(RabbitMQNames.INVENTORY_RESERVATION_REQUESTED_QUEUE);
    }

    @Bean
    Queue inventoryConfirmationRequestedQueue() {
        return consumerQueue(RabbitMQNames.INVENTORY_CONFIRMATION_REQUESTED_QUEUE);
    }

    @Bean
    Queue inventoryReleaseRequestedQueue() {
        return consumerQueue(RabbitMQNames.INVENTORY_RELEASE_REQUESTED_QUEUE);
    }

    @Bean
    Queue inventoryRestockRequestedQueue() {
        return consumerQueue(RabbitMQNames.INVENTORY_RESTOCK_REQUESTED_QUEUE);
    }

    @Bean
    Binding inventoryReservationRequestedBinding(Queue inventoryReservationRequestedQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(inventoryReservationRequestedQueue, fashionEventsExchange, EventTypes.INVENTORY_RESERVATION_REQUESTED);
    }

    @Bean
    Binding inventoryConfirmationRequestedBinding(Queue inventoryConfirmationRequestedQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(inventoryConfirmationRequestedQueue, fashionEventsExchange, EventTypes.INVENTORY_CONFIRMATION_REQUESTED);
    }

    @Bean
    Binding inventoryReleaseRequestedBinding(Queue inventoryReleaseRequestedQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(inventoryReleaseRequestedQueue, fashionEventsExchange, EventTypes.INVENTORY_RELEASE_REQUESTED);
    }

    @Bean
    Binding inventoryRestockRequestedBinding(Queue inventoryRestockRequestedQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(inventoryRestockRequestedQueue, fashionEventsExchange, EventTypes.INVENTORY_RESTOCK_REQUESTED);
    }

    // ----- Avatar từ identity-service -----

    @Bean
    Queue profileAvatarChangedQueue() {
        return consumerQueue(RabbitMQNames.PROFILE_AVATAR_CHANGED_QUEUE);
    }

    @Bean
    Binding profileAvatarChangedBinding(Queue profileAvatarChangedQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(profileAvatarChangedQueue, fashionEventsExchange, EventTypes.PROFILE_AVATAR_CHANGED);
    }

    /** Payload là JSON; kiểu đích suy ra từ tham số của @RabbitListener, không cần header __TypeId__. */
    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    private static Queue consumerQueue(String name) {
        return RabbitTopology.consumerQueue(name, RabbitMQNames.CATALOG_DEAD_LETTER_QUEUE);
    }
}
