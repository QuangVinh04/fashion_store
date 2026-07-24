package com.fashionstore.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.EventEnvelope;
import com.fashionstore.contracts.EventTypes;
import com.fashionstore.contracts.cart.CartItemsRemovalRequested;
import com.fashionstore.contracts.inventory.InventoryReservationCommand;
import com.fashionstore.contracts.inventory.InventoryReservationResult;
import com.fashionstore.contracts.payment.PaymentCancellationRequested;
import com.fashionstore.contracts.payment.PaymentCancellationResult;
import com.fashionstore.contracts.payment.PaymentRequested;
import com.fashionstore.contracts.payment.PaymentResult;
import com.fashionstore.order.config.messaging.RabbitMQNames;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderStatus;
import com.fashionstore.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class OrderSagaEventListener {

    private final OrderRepository orderRepository;
    private final ProcessedMessageService processedMessageService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.saga.inventory-timeout-seconds:300}")
    private long inventoryTimeoutSeconds;

    @Value("${app.saga.payment-timeout-seconds:900}")
    private long paymentTimeoutSeconds;

    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_INVENTORY_RESERVED_QUEUE)
    public void inventoryReserved(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        processedMessageService.processOnce(messageId, "order-inventory-reserved-v2", () -> {
            InventoryReservationResult result = payload(envelope, InventoryReservationResult.class);
            Order order = orderRepository.findByIdForUpdate(result.orderId()).orElse(null);
            if (order == null) {
                return;
            }

            if (order.getStatus() == OrderStatus.PENDING_INVENTORY) {
                order.setInventoryReservationId(result.reservationId());
                order.setStatus(OrderStatus.PENDING_PAYMENT);
                order.setSagaFailureReason(null);
                orderRepository.save(order);
                requestPayment(order, envelope.correlationId());
                return;
            }

            if (order.getStatus() == OrderStatus.CANCELLED && order.getInventoryReservationId() == null) {
                order.setInventoryReservationId(result.reservationId());
                beginInventoryRelease(order, OrderStatus.CANCELLED, envelope.correlationId());
            }
        });
    }

    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_INVENTORY_REJECTED_QUEUE)
    public void inventoryRejected(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        processedMessageService.processOnce(messageId, "order-inventory-rejected-v2", () -> {
            InventoryReservationResult result = payload(envelope, InventoryReservationResult.class);
            orderRepository.findByIdForUpdate(result.orderId()).ifPresent(order -> {
                if (order.getStatus() == OrderStatus.PENDING_INVENTORY) {
                    order.setStatus(OrderStatus.CANCELLED);
                    order.setSagaFailureReason(result.rejectionReason());
                    orderRepository.save(order);
                }
            });
        });
    }

    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_PAYMENT_COMPLETED_QUEUE)
    public void paymentCompleted(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        processedMessageService.processOnce(messageId, "order-payment-completed-v2", () -> {
            PaymentResult result = payload(envelope, PaymentResult.class);
            Order order = orderRepository.findByIdForUpdate(result.orderId()).orElse(null);
            if (order == null
                    || (order.getStatus() != OrderStatus.PENDING_PAYMENT
                    && order.getStatus() != OrderStatus.CANCELLING_PAYMENT)) {
                return;
            }
            order.setPaymentId(result.paymentId());
            order.setStatus(OrderStatus.CONFIRMING_INVENTORY);
            order.setCompensationTargetStatus(null);
            order.setSagaFailureReason(null);
            orderRepository.save(order);
            requestInventoryConfirmation(order, envelope.correlationId());
        });
    }

    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_PAYMENT_FAILED_QUEUE)
    public void paymentFailed(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        processedMessageService.processOnce(messageId, "order-payment-failed-v2", () -> {
            PaymentResult result = payload(envelope, PaymentResult.class);
            Order order = orderRepository.findByIdForUpdate(result.orderId()).orElse(null);
            if (order == null
                    || (order.getStatus() != OrderStatus.PENDING_PAYMENT
                    && order.getStatus() != OrderStatus.CANCELLING_PAYMENT)) {
                return;
            }
            order.setPaymentId(result.paymentId());
            order.setSagaFailureReason(result.failureReason());
            beginInventoryRelease(order, OrderStatus.PAYMENT_FAILED, envelope.correlationId());
        });
    }

    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_PAYMENT_CANCELLED_QUEUE)
    public void paymentCancelled(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        processedMessageService.processOnce(messageId, "order-payment-cancelled-v1", () -> {
            PaymentCancellationResult result = payload(envelope, PaymentCancellationResult.class);
            Order order = orderRepository.findByIdForUpdate(result.orderId()).orElse(null);
            if (order == null || order.getStatus() != OrderStatus.CANCELLING_PAYMENT) {
                return;
            }
            order.setPaymentId(result.paymentId());
            beginInventoryRelease(order, OrderStatus.CANCELLED, envelope.correlationId());
        });
    }

    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_PAYMENT_CANCELLATION_REJECTED_QUEUE)
    public void paymentCancellationRejected(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        processedMessageService.processOnce(messageId, "order-payment-cancellation-rejected-v1", () -> {
            PaymentCancellationResult result = payload(envelope, PaymentCancellationResult.class);
            Order order = orderRepository.findByIdForUpdate(result.orderId()).orElse(null);
            if (order == null || order.getStatus() != OrderStatus.CANCELLING_PAYMENT) {
                return;
            }
            order.setPaymentId(result.paymentId());
            order.setStatus(OrderStatus.CONFIRMING_INVENTORY);
            order.setSagaFailureReason(null);
            orderRepository.save(order);
            requestInventoryConfirmation(order, envelope.correlationId());
        });
    }

    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_INVENTORY_CONFIRMED_QUEUE)
    public void inventoryConfirmed(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        processedMessageService.processOnce(messageId, "order-inventory-confirmed-v1", () -> {
            InventoryReservationCommand result = payload(envelope, InventoryReservationCommand.class);
            Order order = orderRepository.findByIdForUpdate(result.orderId()).orElse(null);
            if (order == null
                    || order.getStatus() != OrderStatus.CONFIRMING_INVENTORY
                    || !Objects.equals(result.reservationId(), order.getInventoryReservationId())) {
                return;
            }
            order.setStatus(OrderStatus.CONFIRMED);
            order.setCompensationTargetStatus(null);
            orderRepository.save(order);
            requestCartItemsRemoval(order, envelope.correlationId());
        });
    }

    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_INVENTORY_RELEASED_QUEUE)
    public void inventoryReleased(
            EventEnvelope<?> envelope,
            @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        processedMessageService.processOnce(messageId, "order-inventory-released-v1", () -> {
            InventoryReservationCommand result = payload(envelope, InventoryReservationCommand.class);
            Order order = orderRepository.findByIdForUpdate(result.orderId()).orElse(null);
            if (order == null
                    || order.getStatus() != OrderStatus.RELEASING_INVENTORY
                    || !Objects.equals(result.reservationId(), order.getInventoryReservationId())) {
                return;
            }
            OrderStatus targetStatus = order.getCompensationTargetStatus() == null
                    ? OrderStatus.CANCELLED
                    : order.getCompensationTargetStatus();
            order.setStatus(targetStatus);
            order.setCompensationTargetStatus(null);
            orderRepository.save(order);
        });
    }

    @Transactional
    @Scheduled(fixedDelayString = "${app.saga.timeout-scan-delay-ms:60000}")
    public void expirePendingSagaSteps() {
        LocalDateTime inventoryCutoff = LocalDateTime.now().minusSeconds(inventoryTimeoutSeconds);
        orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.PENDING_INVENTORY, inventoryCutoff)
                .forEach(order -> {
                    order.setStatus(OrderStatus.CANCELLED);
                    order.setSagaFailureReason("Inventory reservation timed out");
                    orderRepository.save(order);
                });

        LocalDateTime paymentCutoff = LocalDateTime.now().minusSeconds(paymentTimeoutSeconds);
        orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.PENDING_PAYMENT, paymentCutoff)
                .forEach(order -> {
                    order.setStatus(OrderStatus.CANCELLING_PAYMENT);
                    order.setSagaFailureReason("Payment timed out");
                    orderRepository.save(order);
                    requestPaymentCancellation(order);
                });
    }

    private void requestPayment(Order order, String correlationId) {
        var checkout = order.getCheckout();
        eventPublisher.publishEvent(EventEnvelope.v1(
                EventTypes.PAYMENT_REQUESTED,
                order.getId(),
                correlationId,
                new PaymentRequested(
                        order.getId(),
                        order.getUserId(),
                        checkout.getPaymentMethod().name(),
                        checkout.getPaymentProvider().name(),
                        order.getTotalAmount()
                )
        ));
    }

    private void requestInventoryConfirmation(Order order, String correlationId) {
        eventPublisher.publishEvent(EventEnvelope.v1(
                EventTypes.INVENTORY_CONFIRMATION_REQUESTED,
                order.getId(),
                correlationId,
                reservationCommand(order)
        ));
    }

    private void beginInventoryRelease(
            Order order,
            OrderStatus targetStatus,
            String correlationId
    ) {
        order.setStatus(OrderStatus.RELEASING_INVENTORY);
        order.setCompensationTargetStatus(targetStatus);
        orderRepository.save(order);
        eventPublisher.publishEvent(EventEnvelope.v1(
                EventTypes.INVENTORY_RELEASE_REQUESTED,
                order.getId(),
                correlationId,
                reservationCommand(order)
        ));
    }

    private void requestPaymentCancellation(Order order) {
        eventPublisher.publishEvent(EventEnvelope.v1(
                EventTypes.PAYMENT_CANCELLATION_REQUESTED,
                order.getId(),
                order.getId(),
                new PaymentCancellationRequested(
                        order.getId(),
                        order.getPaymentId(),
                        order.getSagaFailureReason()
                )
        ));
    }

    private void requestCartItemsRemoval(Order order, String correlationId) {
        List<String> cartItemIds = order.getItems().stream()
                .map(item -> item.getCartItemId())
                .filter(id -> id != null && !id.isBlank())
                .toList();
        eventPublisher.publishEvent(EventEnvelope.v1(
                EventTypes.CART_ITEMS_REMOVAL_REQUESTED,
                order.getId(),
                correlationId,
                new CartItemsRemovalRequested(order.getUserId(), cartItemIds)
        ));
    }

    private InventoryReservationCommand reservationCommand(Order order) {
        return new InventoryReservationCommand(order.getId(), order.getInventoryReservationId());
    }

    private <T> T payload(EventEnvelope<?> envelope, Class<T> type) {
        return objectMapper.convertValue(envelope.payload(), type);
    }
}
