package com.fashionstore.order.messaging;

import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.inventory.event.InventoryConfirmedEvent;
import com.fashionstore.contracts.inventory.event.InventoryReleasedEvent;
import com.fashionstore.contracts.inventory.event.InventoryReservationEvent;
import com.fashionstore.contracts.inventory.event.InventoryReservationFailedEvent;
import com.fashionstore.contracts.payment.event.PaymentCancellationRejectedEvent;
import com.fashionstore.contracts.payment.event.PaymentCancelledEvent;
import com.fashionstore.contracts.payment.event.PaymentFailedEvent;
import com.fashionstore.contracts.payment.event.PaymentSuccessEvent;
import com.fashionstore.order.config.messaging.RabbitMQNames;
import com.fashionstore.order.model.Order;
import com.fashionstore.order.model.OrderSaga;
import com.fashionstore.order.model.enumeration.OrderSagaStep;
import com.fashionstore.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Toàn bộ reply handler của order saga. Mỗi method chỉ khai báo: nghe queue nào, chờ bước nào,
 * đổi saga ra sao và phát tiếp cái gì — phần còn lại nằm trong {@link SagaReplyProcessor}.
 */
@Component
@RequiredArgsConstructor
public class OrderSagaEventListener {

    private final SagaReplyProcessor sagaReplies;
    private final OrderRepository orderRepository;

    // 1. Giữ kho xong -> xin thanh toán
    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_INVENTORY_RESERVED_QUEUE)
    public void inventoryReserved(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        sagaReplies.process(SagaReply.<InventoryReservationEvent>builder()
                .messageId(messageId)
                .consumer(SagaConsumers.INVENTORY_RESERVED)
                .envelope(envelope)
                .payloadType(InventoryReservationEvent.class)
                .orderIdOf(InventoryReservationEvent::orderId)
                .expectedStep(OrderSagaStep.RESERVE_INVENTORY)
                .onStep((saga, event) -> {
                    saga.inventoryReserved(event.reservationId());
                    return List.of(SagaCommands.authorizePayment(order(saga)));
                })
                // §08: reply về sau khi saga đã đóng vì timeout — nhả kho một lần, không mở lại state machine
                .onTerminal((saga, event) -> {
                    if (saga.getInventoryReservationId() != null) {
                        return List.of();
                    }
                    saga.recordOrphanReservation(event.reservationId());
                    return List.of(SagaCommands.releaseInventory(saga.getOrderId(), event.reservationId()));
                })
                .build());
    }

    // 2. Hết hàng -> chưa giữ gì nên đóng saga luôn, không cần bù trừ
    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_INVENTORY_REJECTED_QUEUE)
    public void inventoryRejected(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        sagaReplies.process(SagaReply.<InventoryReservationFailedEvent>builder()
                .messageId(messageId)
                .consumer(SagaConsumers.INVENTORY_REJECTED)
                .envelope(envelope)
                .payloadType(InventoryReservationFailedEvent.class)
                .orderIdOf(InventoryReservationFailedEvent::orderId)
                .expectedStep(OrderSagaStep.RESERVE_INVENTORY)
                .onStep((saga, event) -> {
                    saga.compensated(event.failureCode(), event.failureMessage());
                    return cancelOrder(saga, event.failureMessage());
                })
                .build());
    }

    // 3. Thanh toán xong -> xin chốt kho
    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_PAYMENT_COMPLETED_QUEUE)
    public void paymentCompleted(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        sagaReplies.process(SagaReply.<PaymentSuccessEvent>builder()
                .messageId(messageId)
                .consumer(SagaConsumers.PAYMENT_COMPLETED)
                .envelope(envelope)
                .payloadType(PaymentSuccessEvent.class)
                .orderIdOf(PaymentSuccessEvent::orderId)
                .expectedStep(OrderSagaStep.AUTHORIZE_PAYMENT)
                .onStep((saga, event) -> {
                    saga.paymentAuthorized(event.paymentId());
                    return List.of(SagaCommands.confirmInventory(saga));
                })
                .build());
    }

    // 4. Thanh toán hỏng -> nhả kho
    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_PAYMENT_FAILED_QUEUE)
    public void paymentFailed(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        sagaReplies.process(SagaReply.<PaymentFailedEvent>builder()
                .messageId(messageId)
                .consumer(SagaConsumers.PAYMENT_FAILED)
                .envelope(envelope)
                .payloadType(PaymentFailedEvent.class)
                .orderIdOf(PaymentFailedEvent::orderId)
                .expectedStep(OrderSagaStep.AUTHORIZE_PAYMENT)
                .onStep((saga, event) -> {
                    saga.startCompensation(OrderSagaStep.RELEASE_INVENTORY, "PAYMENT_FAILED", event.reason());
                    return List.of(SagaCommands.releaseInventory(saga));
                })
                .build());
    }

    // 5. Hủy được thanh toán -> nhả kho
    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_PAYMENT_CANCELLED_QUEUE)
    public void paymentCancelled(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        sagaReplies.process(SagaReply.<PaymentCancelledEvent>builder()
                .messageId(messageId)
                .consumer(SagaConsumers.PAYMENT_CANCELLED)
                .envelope(envelope)
                .payloadType(PaymentCancelledEvent.class)
                .orderIdOf(PaymentCancelledEvent::orderId)
                .expectedStep(OrderSagaStep.CANCEL_PAYMENT)
                .onStep((saga, event) -> {
                    saga.paymentCancelled();
                    return List.of(SagaCommands.releaseInventory(saga));
                })
                .build());
    }

    // 6. §08 forward recovery: tiền đã thu, không hủy được -> quay lại đi tiếp tới CONFIRM_INVENTORY
    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_PAYMENT_CANCELLATION_REJECTED_QUEUE)
    public void paymentCancellationRejected(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        sagaReplies.process(SagaReply.<PaymentCancellationRejectedEvent>builder()
                .messageId(messageId)
                .consumer(SagaConsumers.PAYMENT_CANCELLATION_REJECTED)
                .envelope(envelope)
                .payloadType(PaymentCancellationRejectedEvent.class)
                .orderIdOf(PaymentCancellationRejectedEvent::orderId)
                .expectedStep(OrderSagaStep.CANCEL_PAYMENT)
                .onStep((saga, event) -> {
                    saga.resumeAfterCancellationRejected();
                    return List.of(SagaCommands.confirmInventory(saga));
                })
                .build());
    }

    // 7. Chốt kho xong -> saga hoàn tất, đơn CONFIRMED
    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_INVENTORY_CONFIRMED_QUEUE)
    public void inventoryConfirmed(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        sagaReplies.process(SagaReply.<InventoryConfirmedEvent>builder()
                .messageId(messageId)
                .consumer(SagaConsumers.INVENTORY_CONFIRMED)
                .envelope(envelope)
                .payloadType(InventoryConfirmedEvent.class)
                .orderIdOf(InventoryConfirmedEvent::orderId)
                .expectedStep(OrderSagaStep.CONFIRM_INVENTORY)
                .onStep((saga, event) -> {
                    saga.complete();

                    Order order = lockOrderWithItems(saga);
                    order.confirm(saga.getPaymentId());
                    orderRepository.save(order);

                    // Việc phụ, phát cùng transaction nhưng nằm ngoài saga: hỏng cũng không rollback đơn.
                    return List.of(
                            SagaCommands.orderConfirmed(order),
                            SagaCommands.removeCartItems(order)
                    );
                })
                .build());
    }

    // 8. Nhả kho xong -> bù trừ hoàn tất, đơn CANCELLED
    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_INVENTORY_RELEASED_QUEUE)
    public void inventoryReleased(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        sagaReplies.process(SagaReply.<InventoryReleasedEvent>builder()
                .messageId(messageId)
                .consumer(SagaConsumers.INVENTORY_RELEASED)
                .envelope(envelope)
                .payloadType(InventoryReleasedEvent.class)
                .orderIdOf(InventoryReleasedEvent::orderId)
                .expectedStep(OrderSagaStep.RELEASE_INVENTORY)
                .onStep((saga, event) -> {
                    String reason = saga.getFailureReason();
                    saga.compensated();
                    return cancelOrder(saga, reason);
                })
                .build());
    }

    private List<SagaCommand> cancelOrder(OrderSaga saga, String reason) {
        Order order = order(saga);
        order.cancel(reason);
        orderRepository.save(order);
        return List.of(SagaCommands.orderCancelled(order, reason));
    }

    /**
     * Order phải tồn tại khi saga của nó còn sống — thiếu là lỗi dữ liệu thật, không phải race bình thường,
     * nên để exception ném ra ngoài cho container retry rồi đẩy sang DLQ.
     */
    private Order order(OrderSaga saga) {
        return orderRepository.findByIdForUpdate(saga.getOrderId())
                .orElseThrow(() -> new IllegalStateException(
                        "Order %s của saga %s không tồn tại".formatted(saga.getOrderId(), saga.getId())));
    }

    private Order lockOrderWithItems(OrderSaga saga) {
        Order order = order(saga);
        order.getItems().size(); // nạp item để dựng lệnh xóa giỏ hàng trước khi transaction đóng
        return order;
    }
}
