package com.fashionstore.order.messaging;

import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.payment.event.PaymentRefundRejectedEvent;
import com.fashionstore.contracts.payment.event.PaymentRefundedEvent;
import com.fashionstore.order.config.messaging.RabbitMQNames;
import com.fashionstore.order.model.Order;
import com.fashionstore.order.model.enumeration.OrderStatus;
import com.fashionstore.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reply cho yêu cầu hoàn tiền. Cố tình tách khỏi {@link OrderSagaEventListener}: hoàn tiền không thuộc
 * {@link com.fashionstore.order.model.OrderSaga} — nó xảy ra sau khi saga đặt hàng đã đóng từ lâu (đơn
 * đã DELIVERED/RETURNED), không có bước nào để bù trừ, nên không cần bộ máy state machine đầy đủ.
 *
 * <p>Guard ở đây đơn giản hơn saga: chỉ cần đơn đang ở đúng trạng thái {@code RETURNED} — không có khái
 * niệm "đúng bước" vì đây là một yêu cầu độc lập, không phải một chuỗi bước tuần tự.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundEventListener {

    private final ProcessedMessageService processedMessageService;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_PAYMENT_REFUNDED_QUEUE)
    public void paymentRefunded(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        processedMessageService.processOnce(messageId, SagaConsumers.PAYMENT_REFUNDED, () -> {
            PaymentRefundedEvent event = payload(envelope, PaymentRefundedEvent.class);
            Order order = orderRepository.findByIdForUpdate(event.orderId()).orElse(null);
            if (order == null) {
                log.warn("Payment refunded cho order {} không tồn tại — bỏ qua", event.orderId());
                return;
            }
            if (order.getStatus() == OrderStatus.REFUNDED) {
                return;   // đã xử lý reply trước đó (redelivery), im lặng bỏ qua
            }
            if (order.getStatus() != OrderStatus.RETURNED) {
                log.error("Order {} nhận payment.refunded nhưng đang ở trạng thái {} — bỏ qua",
                        order.getId(), order.getStatus());
                return;
            }
            order.setStatus(OrderStatus.REFUNDED);
            orderRepository.save(order);
        });
    }

    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_PAYMENT_REFUND_REJECTED_QUEUE)
    public void paymentRefundRejected(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        processedMessageService.processOnce(messageId, SagaConsumers.PAYMENT_REFUND_REJECTED, () -> {
            PaymentRefundRejectedEvent event = payload(envelope, PaymentRefundRejectedEvent.class);
            // Đơn giữ nguyên RETURNED — cần người vận hành tra payment-service để biết vì sao hoàn tiền
            // thất bại (payment chưa từng COMPLETED, đã hoàn trước đó theo cách khác, v.v.) rồi thử lại.
            log.error("Hoàn tiền cho order {} bị từ chối [{}]: {} — cần người vận hành xử lý",
                    event.orderId(), event.failureCode(), event.failureMessage());
        });
    }

    private <T> T payload(EventEnvelope<?> envelope, Class<T> type) {
        return objectMapper.convertValue(envelope.payload(), type);
    }
}
