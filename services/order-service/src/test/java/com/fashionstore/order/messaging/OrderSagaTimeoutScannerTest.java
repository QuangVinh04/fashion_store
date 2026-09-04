package com.fashionstore.order.messaging;

import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.order.model.Order;
import com.fashionstore.order.model.OrderSaga;
import com.fashionstore.order.model.enumeration.OrderSagaStatus;
import com.fashionstore.order.model.enumeration.OrderSagaStep;
import com.fashionstore.order.model.enumeration.OrderStatus;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.OrderSagaRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderSagaTimeoutScannerTest {

    @Mock
    private OrderSagaRepository sagaRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SagaOutbox sagaOutbox;

    private SimpleMeterRegistry meterRegistry;
    private OrderSagaTimeoutScanner scanner;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scanner = new OrderSagaTimeoutScanner(sagaRepository, orderRepository, sagaOutbox, meterRegistry);
    }

    @Test
    void reservationTimeoutCancelsOrderWithoutCompensation() {
        OrderSaga saga = OrderSaga.start("order-1");
        Order order = order();
        due(saga);
        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));

        scanner.expireDueSteps();

        assertEquals(OrderSagaStatus.COMPENSATED, saga.getStatus());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(EventTypes.ORDER_CANCELLED, emitted().getFirst().eventType());
    }

    @Test
    void paymentTimeoutStartsCompensationWithCancelPayment() {
        OrderSaga saga = OrderSaga.start("order-1");
        saga.inventoryReserved("res-1");   // -> AUTHORIZE_PAYMENT
        due(saga);

        scanner.expireDueSteps();

        assertEquals(OrderSagaStatus.COMPENSATING, saga.getStatus());
        assertEquals(OrderSagaStep.CANCEL_PAYMENT, saga.getCurrentStep());
        assertEquals(EventTypes.PAYMENT_CANCELLATION_REQUESTED, emitted().getFirst().eventType());
    }

    @Test
    void compensationStepIsRepublishedUntilRetriesRunOut() {
        OrderSaga saga = OrderSaga.start("order-1");
        saga.inventoryReserved("res-1");
        saga.startCompensation(OrderSagaStep.RELEASE_INVENTORY, "PAYMENT_FAILED", "Thẻ bị từ chối");
        due(saga);

        scanner.expireDueSteps();

        assertEquals(1, saga.getRetryCount());
        assertEquals(OrderSagaStep.RELEASE_INVENTORY, saga.getCurrentStep());
        assertEquals(EventTypes.INVENTORY_RELEASE_REQUESTED, emitted().getFirst().eventType());
    }

    @Test
    void exhaustedRetriesFailTheSagaAndRaiseAMetric() {
        OrderSaga saga = OrderSaga.start("order-1");
        saga.inventoryReserved("res-1");
        saga.startCompensation(OrderSagaStep.RELEASE_INVENTORY, "PAYMENT_FAILED", "Thẻ bị từ chối");
        saga.setRetryCount(OrderSaga.MAX_RETRIES);
        due(saga);

        scanner.expireDueSteps();

        assertEquals(OrderSagaStatus.FAILED, saga.getStatus());
        assertNull(saga.getStepDeadline());
        assertEquals(1.0, meterRegistry.counter("order.saga.failed", "step", "RELEASE_INVENTORY").count());
        verify(sagaOutbox, never()).emit(any(), any());
    }

    private void due(OrderSaga saga) {
        saga.setStepDeadline(LocalDateTime.now().minusMinutes(1));
        when(sagaRepository.findDueForUpdate(anyList(), any(LocalDateTime.class))).thenReturn(List.of(saga));
    }

    private List<SagaCommand> emitted() {
        ArgumentCaptor<SagaCommand> captor = ArgumentCaptor.forClass(SagaCommand.class);
        verify(sagaOutbox, org.mockito.Mockito.atLeastOnce()).emit(any(OrderSaga.class), captor.capture());
        return captor.getAllValues();
    }

    private Order order() {
        Order order = Order.builder()
                .orderCode("ORD-2026-0001")
                .userId("user-1")
                .idempotencyKey("checkout-1")
                .checkoutId("checkout-1")
                .status(OrderStatus.PENDING)
                .recipientName("Customer")
                .recipientPhone("0900000000")
                .shippingAddress("Address")
                .subtotalAmount(BigDecimal.TEN)
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(BigDecimal.TEN)
                .items(new ArrayList<>())
                .build();
        order.setId("order-1");
        return order;
    }
}
