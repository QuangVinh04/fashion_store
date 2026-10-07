package com.fashionstore.payment.config.messaging;

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
 * Topology của payment-service. RabbitAdmin (Spring Boot tự tạo) khai báo mọi bean Exchange/Queue/Binding
 * dưới đây lên broker khi kết nối lần đầu, nên service tự dựng được hạ tầng nó cần trên một broker trống.
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

    // ----- Dead letter của payment: hết retry / lỗi vĩnh viễn thì message nằm ở đây chờ xử lý tay -----

    @Bean
    Queue paymentDeadLetterQueue() {
        return RabbitTopology.deadLetterQueue(RabbitMQNames.PAYMENT_DEAD_LETTER_QUEUE);
    }

    @Bean
    Binding paymentDeadLetterBinding(Queue paymentDeadLetterQueue, DirectExchange deadLetterExchange) {
        return RabbitTopology.deadLetterBinding(paymentDeadLetterQueue, deadLetterExchange);
    }

    // ----- Queue nghiệp vụ -----

    /** Một queue nhận cả 3 lệnh saga để giữ đúng thứ tự xin thanh toán → huỷ → hoàn tiền của cùng một đơn. */
    @Bean
    Queue paymentSagaCommandQueue() {
        return RabbitTopology.consumerQueue(
                RabbitMQNames.PAYMENT_SAGA_COMMAND_QUEUE, RabbitMQNames.PAYMENT_DEAD_LETTER_QUEUE);
    }

    @Bean
    Queue paymentOrderDeliveredQueue() {
        return RabbitTopology.consumerQueue(
                RabbitMQNames.PAYMENT_ORDER_DELIVERED_QUEUE, RabbitMQNames.PAYMENT_DEAD_LETTER_QUEUE);
    }

    @Bean
    Binding paymentRequestedBinding(Queue paymentSagaCommandQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(paymentSagaCommandQueue, fashionEventsExchange, EventTypes.PAYMENT_REQUESTED);
    }

    @Bean
    Binding paymentCancellationRequestedBinding(Queue paymentSagaCommandQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(paymentSagaCommandQueue, fashionEventsExchange, EventTypes.PAYMENT_CANCELLATION_REQUESTED);
    }

    @Bean
    Binding paymentRefundRequestedBinding(Queue paymentSagaCommandQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(paymentSagaCommandQueue, fashionEventsExchange, EventTypes.PAYMENT_REFUND_REQUESTED);
    }

    @Bean
    Binding paymentOrderDeliveredBinding(Queue paymentOrderDeliveredQueue, DirectExchange fashionEventsExchange) {
        return RabbitTopology.bind(paymentOrderDeliveredQueue, fashionEventsExchange, EventTypes.ORDER_DELIVERED);
    }

    /** Payload là JSON; kiểu đích suy ra từ tham số của @RabbitListener, không cần header __TypeId__. */
    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
