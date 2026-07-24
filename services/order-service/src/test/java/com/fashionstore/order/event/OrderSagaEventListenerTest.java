package com.fashionstore.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.contracts.EventEnvelope;
import com.fashionstore.contracts.EventTypes;
import com.fashionstore.contracts.inventory.InventoryReservationCommand;
import com.fashionstore.contracts.inventory.InventoryReservationResult;
import com.fashionstore.contracts.payment.PaymentResult;
import com.fashionstore.order.entity.Checkout;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderItem;
import com.fashionstore.order.entity.OrderStatus;
import com.fashionstore.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSagaEventListenerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProcessedMessageService processedMessageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrderSagaEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderSagaEventListener(
                orderRepository,
                processedMessageService,
                new ObjectMapper(),
                eventPublisher
        );
    }

    @Test
    void completesOrderOnlyAfterInventoryConfirmation() {
        enableProcessedMessages();
        Order order = order(OrderStatus.PENDING_INVENTORY);
        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));

        listener.inventoryReserved(
                envelope(
                        EventTypes.INVENTORY_RESERVED,
                        new InventoryReservationResult("order-1", "reservation-1", null)),
                "message-1"
        );

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(EventTypes.PAYMENT_REQUESTED, lastPublishedEvent().eventType());

        listener.paymentCompleted(
                envelope(
                        EventTypes.PAYMENT_COMPLETED,
                        new PaymentResult("order-1", "payment-1", null)),
                "message-2"
        );

        assertEquals(OrderStatus.CONFIRMING_INVENTORY, order.getStatus());
        assertEquals(EventTypes.INVENTORY_CONFIRMATION_REQUESTED, lastPublishedEvent().eventType());

        listener.inventoryConfirmed(
                envelope(
                        EventTypes.INVENTORY_CONFIRMED,
                        new InventoryReservationCommand("order-1", "reservation-1")),
                "message-3"
        );

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(EventTypes.CART_ITEMS_REMOVAL_REQUESTED, lastPublishedEvent().eventType());
    }

    @Test
    void finishesPaymentFailureOnlyAfterInventoryRelease() {
        enableProcessedMessages();
        Order order = order(OrderStatus.PENDING_PAYMENT);
        order.setInventoryReservationId("reservation-1");
        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));

        listener.paymentFailed(
                envelope(
                        EventTypes.PAYMENT_FAILED,
                        new PaymentResult("order-1", "payment-1", "Provider rejected payment")),
                "message-1"
        );

        assertEquals(OrderStatus.RELEASING_INVENTORY, order.getStatus());
        assertEquals(OrderStatus.PAYMENT_FAILED, order.getCompensationTargetStatus());
        assertEquals(EventTypes.INVENTORY_RELEASE_REQUESTED, lastPublishedEvent().eventType());

        listener.inventoryReleased(
                envelope(
                        EventTypes.INVENTORY_RELEASED,
                        new InventoryReservationCommand("order-1", "reservation-1")),
                "message-2"
        );

        assertEquals(OrderStatus.PAYMENT_FAILED, order.getStatus());
    }

    @Test
    void releasesLateReservationAfterInventoryTimeoutCancelledOrder() {
        enableProcessedMessages();
        Order order = order(OrderStatus.CANCELLED);
        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));

        listener.inventoryReserved(
                envelope(
                        EventTypes.INVENTORY_RESERVED,
                        new InventoryReservationResult("order-1", "reservation-late", null)),
                "message-1"
        );

        assertEquals(OrderStatus.RELEASING_INVENTORY, order.getStatus());
        assertEquals(OrderStatus.CANCELLED, order.getCompensationTargetStatus());
        assertEquals("reservation-late", order.getInventoryReservationId());
        assertEquals(EventTypes.INVENTORY_RELEASE_REQUESTED, lastPublishedEvent().eventType());
    }

    @Test
    void expiresInventoryAndPaymentStepsWithCorrectCompensation() {
        Order inventoryPending = order(OrderStatus.PENDING_INVENTORY);
        inventoryPending.setUpdatedAt(LocalDateTime.now().minusMinutes(10));
        Order paymentPending = order(OrderStatus.PENDING_PAYMENT);
        paymentPending.setUpdatedAt(LocalDateTime.now().minusMinutes(20));
        paymentPending.setInventoryReservationId("reservation-1");

        when(orderRepository.findByStatusAndUpdatedAtBefore(
                org.mockito.ArgumentMatchers.eq(OrderStatus.PENDING_INVENTORY),
                any(LocalDateTime.class)
        )).thenReturn(List.of(inventoryPending));
        when(orderRepository.findByStatusAndUpdatedAtBefore(
                org.mockito.ArgumentMatchers.eq(OrderStatus.PENDING_PAYMENT),
                any(LocalDateTime.class)
        )).thenReturn(List.of(paymentPending));

        listener.expirePendingSagaSteps();

        assertEquals(OrderStatus.CANCELLED, inventoryPending.getStatus());
        assertEquals("Inventory reservation timed out", inventoryPending.getSagaFailureReason());
        assertEquals(OrderStatus.CANCELLING_PAYMENT, paymentPending.getStatus());
        assertEquals(EventTypes.PAYMENT_CANCELLATION_REQUESTED, lastPublishedEvent().eventType());
    }

    private Order order(OrderStatus status) {
        Order order = Order.builder()
                .orderCode("ORD-2026-TEST")
                .userId("user-1")
                .idempotencyKey("idempotency-1")
                .status(status)
                .recipientName("Customer")
                .recipientPhone("0900000000")
                .shippingAddress("Address")
                .subtotalAmount(BigDecimal.TEN)
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(BigDecimal.TEN)
                .items(List.of(OrderItem.builder()
                        .cartItemId("cart-item-1")
                        .variantId("variant-1")
                        .productName("Product")
                        .unitPrice(BigDecimal.TEN)
                        .quantity(1)
                        .lineTotal(BigDecimal.TEN)
                        .build()))
                .build();
        order.setId("order-1");
        Checkout checkout = Checkout.builder()
                .paymentMethod(PaymentMethod.ONLINE)
                .paymentProvider(PaymentProvider.VNPAY)
                .subtotalAmount(BigDecimal.TEN)
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(BigDecimal.TEN)
                .build();
        order.setCheckout(checkout);
        return order;
    }

    private void enableProcessedMessages() {
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return null;
        }).when(processedMessageService).processOnce(anyString(), anyString(), any(Runnable.class));
    }

    private EventEnvelope<?> envelope(String eventType, Object payload) {
        return EventEnvelope.v1(eventType, "order-1", "correlation-1", payload);
    }

    private EventEnvelope<?> lastPublishedEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishEvent(captor.capture());
        return (EventEnvelope<?>) captor.getAllValues().getLast();
    }
}
