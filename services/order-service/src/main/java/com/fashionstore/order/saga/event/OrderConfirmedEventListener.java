package com.fashionstore.order.saga.event;

import com.fashionstore.common.messaging.RabbitTopology;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.order.OrderConfirmedEvent;
import com.fashionstore.order.shared.config.messaging.RabbitMQNames;
import com.fashionstore.order.order.entity.Order;
import com.fashionstore.order.order.entity.OrderStatusHistory;
import com.fashionstore.order.order.entity.OrderStatus;
import com.fashionstore.order.order.repository.OrderRepository;
import com.fashionstore.order.order.repository.OrderStatusHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lắng nghe sự kiện order.confirmed để tự động cập nhật trạng thái đơn hàng sang PROCESSING.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConfirmedEventListener {

    public static final String CONSUMER_NAME = "order-auto-processing-v1";

    private final ProcessedMessageService processedMessageService;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_CONFIRMED_QUEUE)
    public void onOrderConfirmed(
            EventEnvelope<?> envelope,
            @Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId) {
        processedMessageService.processOnce(
                messageId,
                CONSUMER_NAME,
                () -> {
                    OrderConfirmedEvent event = objectMapper.convertValue(envelope.payload(), OrderConfirmedEvent.class);
                    if (event == null || event.orderId() == null) {
                        return;
                    }
                    Order order = orderRepository.findByIdForUpdate(event.orderId()).orElse(null);
                    if (order == null) {
                        log.warn("Order {} không tồn tại khi nhận order.confirmed — bỏ qua", event.orderId());
                        return;
                    }
                    if (order.getStatus() == OrderStatus.PROCESSING) {
                        return; // Đã xử lý (idempotent)
                    }
                    if (order.getStatus() != OrderStatus.CONFIRMED) {
                        log.info(
                                "Order {} nhận order.confirmed nhưng đang ở trạng thái {} — bỏ qua",
                                order.getId(),
                                order.getStatus());
                        return;
                    }
                    order.setStatus(OrderStatus.PROCESSING);
                    orderRepository.save(order);

                    OrderStatusHistory history =
                            OrderStatusHistory.builder()
                                    .order(order)
                                    .fromStatus(OrderStatus.CONFIRMED)
                                    .toStatus(OrderStatus.PROCESSING)
                                    .action("AUTO_PROCESSING")
                                    .changedBy("SYSTEM")
                                    .reason("Tự động chuyển sang PROCESSING sau khi nhận OrderConfirmedEvent")
                                    .build();
                    orderStatusHistoryRepository.save(history);
                    log.info("Order {} đã tự động chuyển sang PROCESSING", order.getId());
                });
    }
}
