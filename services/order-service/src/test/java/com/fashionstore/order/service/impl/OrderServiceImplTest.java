package com.fashionstore.order.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.order.config.ErrorCode;
import com.fashionstore.order.dto.CancelOrderRequest;
import com.fashionstore.order.dto.CreateOrderRequest;
import com.fashionstore.order.dto.OrderResponse;
import com.fashionstore.order.dto.OrderSagaResponse;
import com.fashionstore.order.dto.OrderSummaryResponse;
import com.fashionstore.order.messaging.SagaCommand;
import com.fashionstore.order.messaging.SagaCancellationService;
import com.fashionstore.order.messaging.SagaOutbox;
import com.fashionstore.order.model.Order;
import com.fashionstore.order.model.OrderSaga;
import com.fashionstore.order.model.enumeration.OrderSagaStatus;
import com.fashionstore.order.model.enumeration.OrderSagaStep;
import com.fashionstore.order.model.enumeration.OrderStatus;
import com.fashionstore.order.outbox.OutboxService;
import com.fashionstore.order.repository.CheckoutRepository;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.OrderSagaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CheckoutRepository checkoutRepository;

    @Mock
    private OrderSagaRepository orderSagaRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private SagaOutbox sagaOutbox;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        // Dùng SagaCancellationService thật để test đúng luật hủy theo bước, chỉ mock hạ tầng.
        SagaCancellationService cancellationService =
                new SagaCancellationService(orderSagaRepository, orderRepository, sagaOutbox);
        service = new OrderServiceImpl(
                orderRepository,
                checkoutRepository,
                orderSagaRepository,
                outboxService,
                sagaOutbox,
                cancellationService,
                currentUserProvider
        );
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
    }

    // ----- idempotency của POST /orders -----

    @Test
    void returnsExistingOrderWhenDefaultCheckoutIdempotencyKeyWasAlreadyUsed() {
        Order existing = order(OrderStatus.PENDING);
        when(orderRepository.findByUserIdAndIdempotencyKey("user-1", "checkout-1"))
                .thenReturn(Optional.of(existing));

        OrderResponse response = service.createOrder("checkout-1", null, new CreateOrderRequest());

        assertEquals("order-1", response.getId());
        assertEquals("checkout-1", response.getCheckoutId());
        assertEquals(OrderStatus.PENDING, response.getStatus());
        assertEquals(PaymentMethod.ONLINE, response.getPaymentMethod());
        verify(checkoutRepository, never()).findForUpdateByIdAndUserId("checkout-1", "user-1");
        verify(orderSagaRepository, never()).save(any());
        verify(outboxService, never()).saveMessage(anyString(), anyString(), any());
    }

    // ----- GET /orders -----

    @Test
    void listsMyOrdersAsSummariesWithPageMetadata() {
        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findByUserId("user-1", pageable))
                .thenReturn(new PageImpl<>(List.of(order(OrderStatus.CONFIRMED)), pageable, 1));

        PageResponse<List<OrderSummaryResponse>> page = service.getMyOrders(null, pageable);

        assertEquals(1, page.getItems().size());
        assertEquals("ORD-2026-0001", page.getItems().getFirst().getOrderCode());
        assertEquals(1, page.getTotalPage());
        assertEquals(10, page.getPageSize());
    }

    @Test
    void filtersMyOrdersByStatusWhenRequested() {
        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findByUserIdAndStatus("user-1", OrderStatus.CANCELLED, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.getMyOrders(OrderStatus.CANCELLED, pageable);

        verify(orderRepository).findByUserIdAndStatus("user-1", OrderStatus.CANCELLED, pageable);
        verify(orderRepository, never()).findByUserId(anyString(), any(Pageable.class));
    }

    // ----- POST /orders/{id}/cancel -----

    @Test
    void cancellingBeforeInventoryIsReservedClosesTheSagaImmediately() {
        Order order = order(OrderStatus.PENDING);
        OrderSaga saga = OrderSaga.start("order-1");   // đang ở RESERVE_INVENTORY
        registerOrderAndSaga(order, saga);

        OrderResponse response = service.cancelMyOrder("order-1", new CancelOrderRequest("Đổi ý"));

        assertEquals(OrderStatus.CANCELLED, response.getStatus());
        assertEquals("Đổi ý", response.getCancelReason());
        assertEquals(OrderSagaStatus.COMPENSATED, saga.getStatus());
        assertEquals(EventTypes.ORDER_CANCELLED, emitted().getFirst().eventType());
    }

    @Test
    void cancellingWhilePaymentIsPendingGoesThroughCompensationInsteadOfCancellingOutright() {
        Order order = order(OrderStatus.PENDING);
        OrderSaga saga = OrderSaga.start("order-1");
        saga.inventoryReserved("res-1");   // -> AUTHORIZE_PAYMENT
        registerOrderAndSaga(order, saga);

        OrderResponse response = service.cancelMyOrder("order-1", new CancelOrderRequest(null));

        // Kho đang bị giữ và tiền có thể đang treo, nên đơn chưa được phép CANCELLED ngay.
        assertEquals(OrderStatus.PENDING, response.getStatus());
        assertEquals(OrderSagaStatus.COMPENSATING, saga.getStatus());
        assertEquals(OrderSagaStep.CANCEL_PAYMENT, saga.getCurrentStep());
        assertEquals(EventTypes.PAYMENT_CANCELLATION_REQUESTED, emitted().getFirst().eventType());
    }

    @Test
    void cancellingAfterPaymentWasCapturedIsRejected() {
        Order order = order(OrderStatus.PENDING);
        OrderSaga saga = OrderSaga.start("order-1");
        saga.inventoryReserved("res-1");
        saga.paymentAuthorized("pay-1");   // -> CONFIRM_INVENTORY, tiền đã thu
        registerOrderAndSaga(order, saga);

        AppException exception = assertThrows(AppException.class,
                () -> service.cancelMyOrder("order-1", new CancelOrderRequest(null)));

        assertEquals(ErrorCode.ORDER_CANNOT_BE_CANCELLED, exception.getErrorCode());
        verify(sagaOutbox, never()).emit(any(), any());
    }

    @Test
    void cancellingAConfirmedOrderIsRejectedBecauseThatIsARefund() {
        Order order = order(OrderStatus.CONFIRMED);
        when(orderRepository.findWithItemsById("order-1")).thenReturn(Optional.of(order));

        AppException exception = assertThrows(AppException.class,
                () -> service.cancelMyOrder("order-1", null));

        assertEquals(ErrorCode.ORDER_CANNOT_BE_CANCELLED, exception.getErrorCode());
    }

    @Test
    void cancellingTwiceReturnsTheSameResultWithoutTouchingTheSaga() {
        Order order = order(OrderStatus.CANCELLED);
        order.setCancelReason("Đổi ý");
        when(orderRepository.findWithItemsById("order-1")).thenReturn(Optional.of(order));

        OrderResponse response = service.cancelMyOrder("order-1", new CancelOrderRequest("Lý do khác"));

        assertEquals(OrderStatus.CANCELLED, response.getStatus());
        assertEquals("Đổi ý", response.getCancelReason());
        verify(orderSagaRepository, never()).findByOrderIdForUpdate(anyString());
    }

    @Test
    void cannotCancelSomebodyElsesOrder() {
        Order order = order(OrderStatus.PENDING);
        order.setUserId("another-user");
        when(orderRepository.findWithItemsById("order-1")).thenReturn(Optional.of(order));

        AppException exception = assertThrows(AppException.class,
                () -> service.cancelMyOrder("order-1", null));

        assertEquals(ErrorCode.ORDER_NOT_FOUND, exception.getErrorCode());
    }

    // ----- admin -----

    @Test
    void exposesSagaStateForDiagnostics() {
        OrderSaga saga = OrderSaga.start("order-1");
        saga.fail("STEP_RETRY_EXHAUSTED", "Cạn 5 lần thử ở bước RELEASE_INVENTORY");
        when(orderSagaRepository.findByOrderId("order-1")).thenReturn(Optional.of(saga));

        OrderSagaResponse response = service.getOrderSaga("order-1");

        assertEquals(OrderSagaStatus.FAILED, response.getStatus());
        assertEquals("STEP_RETRY_EXHAUSTED", response.getFailureCode());
        assertEquals(saga.getId(), response.getSagaId());
    }

    @Test
    void reportsMissingSagaSeparatelyFromMissingOrder() {
        when(orderSagaRepository.findByOrderId("order-1")).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> service.getOrderSaga("order-1"));

        assertEquals(ErrorCode.ORDER_SAGA_NOT_FOUND, exception.getErrorCode());
    }

    // ----- helpers -----

    private void registerOrderAndSaga(Order order, OrderSaga saga) {
        when(orderRepository.findWithItemsById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));
        when(orderSagaRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(saga));
    }

    private List<SagaCommand> emitted() {
        ArgumentCaptor<SagaCommand> captor = ArgumentCaptor.forClass(SagaCommand.class);
        verify(sagaOutbox, org.mockito.Mockito.atLeastOnce()).emit(any(OrderSaga.class), captor.capture());
        return captor.getAllValues();
    }

    private Order order(OrderStatus status) {
        Order order = Order.builder()
                .orderCode("ORD-2026-0001")
                .userId("user-1")
                .idempotencyKey("checkout-1")
                .checkoutId("checkout-1")
                .paymentMethod(PaymentMethod.ONLINE)
                .paymentProvider(PaymentProvider.VNPAY)
                .status(status)
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
