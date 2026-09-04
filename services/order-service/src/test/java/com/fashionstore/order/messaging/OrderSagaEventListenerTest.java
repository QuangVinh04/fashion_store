package com.fashionstore.order.messaging;

import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.inventory.event.InventoryConfirmedEvent;
import com.fashionstore.contracts.inventory.event.InventoryReservationEvent;
import com.fashionstore.contracts.payment.event.PaymentCancellationRejectedEvent;
import com.fashionstore.contracts.payment.event.PaymentFailedEvent;
import com.fashionstore.contracts.payment.event.PaymentSuccessEvent;
import com.fashionstore.order.model.Order;
import com.fashionstore.order.model.OrderSaga;
import com.fashionstore.order.model.enumeration.OrderSagaStatus;
import com.fashionstore.order.model.enumeration.OrderSagaStep;
import com.fashionstore.order.model.enumeration.OrderStatus;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.OrderSagaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test đi qua đúng khung thật ({@link SagaReplyProcessor}), chỉ mock hạ tầng. Nhờ vậy mỗi test kiểm tra
 * được cả guard lẫn transition, không phải kiểm tra riêng từng mảnh.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderSagaEventListenerTest {

    @Mock
    private ProcessedMessageService processedMessageService;

    @Mock
    private OrderSagaRepository sagaRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SagaOutbox sagaOutbox;

    private OrderSagaEventListener listener;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return null;
        }).when(processedMessageService).processOnce(anyString(), anyString(), any(Runnable.class));

        SagaReplyProcessor processor = new SagaReplyProcessor(
                processedMessageService,
                sagaRepository,
                sagaOutbox,
                new ObjectMapper()
        );
        listener = new OrderSagaEventListener(processor, orderRepository);
    }

    @Test
    void reservedInventoryMovesSagaToPaymentAndEmitsAuthorizeCommand() {
        OrderSaga saga = runningSaga();
        Order order = order();
        registerSaga(saga, order);

        listener.inventoryReserved(
                envelope(saga, EventTypes.INVENTORY_RESERVED, new InventoryReservationEvent("order-1", "res-1")),
                "message-1"
        );

        assertEquals(OrderSagaStep.AUTHORIZE_PAYMENT, saga.getCurrentStep());
        assertEquals("res-1", saga.getInventoryReservationId());
        assertEquals(EventTypes.PAYMENT_REQUESTED, emittedCommands().getFirst().eventType());
        // Bảng orders không bị saga chạm tới giữa chừng.
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void replyForAnotherStepIsIgnoredSilently() {
        OrderSaga saga = runningSaga();
        saga.inventoryReserved("res-1");   // saga đã ở AUTHORIZE_PAYMENT
        registerSaga(saga, order());

        listener.inventoryReserved(
                envelope(saga, EventTypes.INVENTORY_RESERVED, new InventoryReservationEvent("order-1", "res-9")),
                "message-2"
        );

        assertEquals(OrderSagaStep.AUTHORIZE_PAYMENT, saga.getCurrentStep());
        assertEquals("res-1", saga.getInventoryReservationId());
        verify(sagaOutbox, never()).emit(any(), any());
    }

    @Test
    void replyCarryingAnotherOrderIdIsRejected() {
        OrderSaga saga = runningSaga();
        registerSaga(saga, order());

        listener.inventoryReserved(
                envelope(saga, EventTypes.INVENTORY_RESERVED, new InventoryReservationEvent("order-999", "res-1")),
                "message-3"
        );

        assertEquals(OrderSagaStep.RESERVE_INVENTORY, saga.getCurrentStep());
        assertNull(saga.getInventoryReservationId());
        verify(sagaOutbox, never()).emit(any(), any());
    }

    @Test
    void lateReservationOnClosedSagaReleasesInventoryWithoutReopeningTheSaga() {
        OrderSaga saga = runningSaga();
        saga.compensated("INVENTORY_TIMEOUT", "Quá hạn chờ giữ kho");
        registerSaga(saga, order());

        listener.inventoryReserved(
                envelope(saga, EventTypes.INVENTORY_RESERVED, new InventoryReservationEvent("order-1", "res-late")),
                "message-4"
        );

        assertEquals(OrderSagaStatus.COMPENSATED, saga.getStatus());
        assertEquals(OrderSagaStep.DONE, saga.getCurrentStep());
        assertEquals("res-late", saga.getInventoryReservationId());
        assertEquals(EventTypes.INVENTORY_RELEASE_REQUESTED, emittedCommands().getFirst().eventType());
    }

    @Test
    void confirmedInventoryCompletesSagaAndConfirmsOrder() {
        OrderSaga saga = runningSaga();
        saga.inventoryReserved("res-1");
        saga.paymentAuthorized("pay-1");
        Order order = order();
        registerSaga(saga, order);

        listener.inventoryConfirmed(
                envelope(saga, EventTypes.INVENTORY_CONFIRMED, new InventoryConfirmedEvent("order-1", "res-1")),
                "message-5"
        );

        assertEquals(OrderSagaStatus.COMPLETED, saga.getStatus());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals("pay-1", order.getPaymentId());

        List<String> emitted = emittedCommands().stream().map(SagaCommand::eventType).toList();
        assertEquals(List.of(EventTypes.ORDER_CONFIRMED, EventTypes.CART_ITEMS_REMOVAL_REQUESTED), emitted);
    }

    @Test
    void failedPaymentStartsCompensationByReleasingInventory() {
        OrderSaga saga = runningSaga();
        saga.inventoryReserved("res-1");
        registerSaga(saga, order());

        listener.paymentFailed(
                envelope(saga, EventTypes.PAYMENT_FAILED, new PaymentFailedEvent("order-1", null, "Thẻ bị từ chối")),
                "message-6"
        );

        assertEquals(OrderSagaStatus.COMPENSATING, saga.getStatus());
        assertEquals(OrderSagaStep.RELEASE_INVENTORY, saga.getCurrentStep());
        assertEquals(EventTypes.INVENTORY_RELEASE_REQUESTED, emittedCommands().getFirst().eventType());
    }

    @Test
    void rejectedCancellationDrivesSagaForwardInsteadOfBackward() {
        OrderSaga saga = runningSaga();
        saga.inventoryReserved("res-1");
        saga.startCompensation(OrderSagaStep.CANCEL_PAYMENT, "PAYMENT_TIMEOUT", "Quá hạn chờ thanh toán");
        registerSaga(saga, order());

        listener.paymentCancellationRejected(
                envelope(saga, EventTypes.PAYMENT_CANCELLATION_REJECTED,
                        new PaymentCancellationRejectedEvent("order-1", "ALREADY_CAPTURED", "Tiền đã thu")),
                "message-7"
        );

        assertEquals(OrderSagaStatus.RUNNING, saga.getStatus());
        assertEquals(OrderSagaStep.CONFIRM_INVENTORY, saga.getCurrentStep());
        assertNull(saga.getFailureCode());
        assertEquals(EventTypes.INVENTORY_CONFIRMATION_REQUESTED, emittedCommands().getFirst().eventType());
    }

    @Test
    void paymentSuccessMovesSagaToInventoryConfirmation() {
        OrderSaga saga = runningSaga();
        saga.inventoryReserved("res-1");
        registerSaga(saga, order());

        listener.paymentCompleted(
                envelope(saga, EventTypes.PAYMENT_COMPLETED, new PaymentSuccessEvent("order-1", "pay-1")),
                "message-8"
        );

        assertEquals(OrderSagaStep.CONFIRM_INVENTORY, saga.getCurrentStep());
        assertEquals("pay-1", saga.getPaymentId());
        assertTrue(saga.getStepDeadline() != null);
        assertEquals(EventTypes.INVENTORY_CONFIRMATION_REQUESTED, emittedCommands().getFirst().eventType());
    }

    // ----- helpers -----

    private OrderSaga runningSaga() {
        return OrderSaga.start("order-1");
    }

    private void registerSaga(OrderSaga saga, Order order) {
        when(sagaRepository.findByIdForUpdate(saga.getId())).thenReturn(Optional.of(saga));
        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));
    }

    private List<SagaCommand> emittedCommands() {
        ArgumentCaptor<SagaCommand> captor = ArgumentCaptor.forClass(SagaCommand.class);
        verify(sagaOutbox, org.mockito.Mockito.atLeastOnce()).emit(any(OrderSaga.class), captor.capture());
        return captor.getAllValues();
    }

    private EventEnvelope<Object> envelope(OrderSaga saga, String eventType, Object payload) {
        return EventEnvelope.v1(eventType, saga.getOrderId(), saga.getId(), payload);
    }

    private Order order() {
        Order order = Order.builder()
                .orderCode("ORD-2026-0001")
                .userId("user-1")
                .idempotencyKey("checkout-1")
                .checkoutId("checkout-1")
                .paymentMethod(PaymentMethod.ONLINE)
                .paymentProvider(PaymentProvider.VNPAY)
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
