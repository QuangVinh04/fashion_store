package com.fashionstore.order.config.messaging;

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
 * Topology của order-service: các queue nhận reply của saga (từ catalog và payment).
 *
 * <p>Không tự khai báo RabbitTemplate: dùng bean của Spring Boot để nó nhận đủ cấu hình
 * {@code spring.rabbitmq.template.*} (mandatory) và tự gắn {@link #jsonMessageConverter()}.
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

    // ----- Dead letter của order -----

    @Bean
    Queue orderDeadLetterQueue() {
        return RabbitTopology.deadLetterQueue(RabbitMQNames.ORDER_DEAD_LETTER_QUEUE);
    }

    @Bean
    Binding orderDeadLetterBinding(Queue orderDeadLetterQueue, DirectExchange deadLetterExchange) {
        return RabbitTopology.deadLetterBinding(orderDeadLetterQueue, deadLetterExchange);
    }

    // ----- Reply của saga -----

    @Bean
    Queue orderInventoryReservedQueue() {
        return sagaQueue(RabbitMQNames.ORDER_INVENTORY_RESERVED_QUEUE);
    }

    @Bean
    Queue orderInventoryRejectedQueue() {
        return sagaQueue(RabbitMQNames.ORDER_INVENTORY_REJECTED_QUEUE);
    }

    @Bean
    Queue orderInventoryConfirmedQueue() {
        return sagaQueue(RabbitMQNames.ORDER_INVENTORY_CONFIRMED_QUEUE);
    }

    @Bean
    Queue orderInventoryReleasedQueue() {
        return sagaQueue(RabbitMQNames.ORDER_INVENTORY_RELEASED_QUEUE);
    }

    @Bean
    Queue orderPaymentInitiatedQueue() {
        return sagaQueue(RabbitMQNames.ORDER_PAYMENT_INITIATED_QUEUE);
    }

    @Bean
    Queue orderPaymentCompletedQueue() {
        return sagaQueue(RabbitMQNames.ORDER_PAYMENT_COMPLETED_QUEUE);
    }

    @Bean
    Queue orderPaymentFailedQueue() {
        return sagaQueue(RabbitMQNames.ORDER_PAYMENT_FAILED_QUEUE);
    }

    @Bean
    Queue orderPaymentCancelledQueue() {
        return sagaQueue(RabbitMQNames.ORDER_PAYMENT_CANCELLED_QUEUE);
    }

    @Bean
    Queue orderPaymentCancellationRejectedQueue() {
        return sagaQueue(RabbitMQNames.ORDER_PAYMENT_CANCELLATION_REJECTED_QUEUE);
    }

    @Bean
    Queue orderPaymentRefundedQueue() {
        return sagaQueue(RabbitMQNames.ORDER_PAYMENT_REFUNDED_QUEUE);
    }

    @Bean
    Queue orderPaymentRefundRejectedQueue() {
        return sagaQueue(RabbitMQNames.ORDER_PAYMENT_REFUND_REJECTED_QUEUE);
    }

    @Bean
    Binding orderInventoryReservedBinding(Queue orderInventoryReservedQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(orderInventoryReservedQueue, fashionEventsExchange, EventTypes.INVENTORY_RESERVED);
    }

    @Bean
    Binding orderInventoryRejectedBinding(Queue orderInventoryRejectedQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(orderInventoryRejectedQueue, fashionEventsExchange, EventTypes.INVENTORY_REJECTED);
    }

    @Bean
    Binding orderInventoryConfirmedBinding(Queue orderInventoryConfirmedQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(orderInventoryConfirmedQueue, fashionEventsExchange, EventTypes.INVENTORY_CONFIRMED);
    }

    @Bean
    Binding orderInventoryReleasedBinding(Queue orderInventoryReleasedQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(orderInventoryReleasedQueue, fashionEventsExchange, EventTypes.INVENTORY_RELEASED);
    }

    @Bean
    Binding orderPaymentInitiatedBinding(Queue orderPaymentInitiatedQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(orderPaymentInitiatedQueue, fashionEventsExchange, EventTypes.PAYMENT_INITIATED);
    }

    @Bean
    Binding orderPaymentCompletedBinding(Queue orderPaymentCompletedQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(orderPaymentCompletedQueue, fashionEventsExchange, EventTypes.PAYMENT_COMPLETED);
    }

    @Bean
    Binding orderPaymentFailedBinding(Queue orderPaymentFailedQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(orderPaymentFailedQueue, fashionEventsExchange, EventTypes.PAYMENT_FAILED);
    }

    @Bean
    Binding orderPaymentCancelledBinding(Queue orderPaymentCancelledQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(orderPaymentCancelledQueue, fashionEventsExchange, EventTypes.PAYMENT_CANCELLED);
    }

    @Bean
    Binding orderPaymentCancellationRejectedBinding(Queue orderPaymentCancellationRejectedQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(orderPaymentCancellationRejectedQueue, fashionEventsExchange, EventTypes.PAYMENT_CANCELLATION_REJECTED);
    }

    @Bean
    Binding orderPaymentRefundedBinding(Queue orderPaymentRefundedQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(orderPaymentRefundedQueue, fashionEventsExchange, EventTypes.PAYMENT_REFUNDED);
    }

    @Bean
    Binding orderPaymentRefundRejectedBinding(Queue orderPaymentRefundRejectedQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(orderPaymentRefundRejectedQueue, fashionEventsExchange, EventTypes.PAYMENT_REFUND_REJECTED);
    }

    /** Payload là JSON; kiểu đích suy ra từ tham số của @RabbitListener, không cần header __TypeId__. */
    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    private static Queue sagaQueue(String name) {
        return RabbitTopology.consumerQueue(name, RabbitMQNames.ORDER_DEAD_LETTER_QUEUE);
    }
}
