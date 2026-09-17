package com.fashionstore.order.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.client.IdentityClient;
import com.fashionstore.order.dto.CancelOrderRequest;
import com.fashionstore.order.dto.CreateOrderRequest;
import com.fashionstore.order.dto.OrderResponse;
import com.fashionstore.order.dto.OrderSagaResponse;
import com.fashionstore.order.dto.OrderStatusHistoryResponse;
import com.fashionstore.order.dto.OrderSummaryResponse;
import com.fashionstore.order.dto.UpdateOrderStatusRequest;
import com.fashionstore.order.entity.OrderStatusHistory;
import com.fashionstore.order.entity.Shipment;
import com.fashionstore.order.repository.OrderStatusHistoryRepository;
import com.fashionstore.order.repository.ShipmentRepository;
import com.fashionstore.order.saga.SagaCommand;
import com.fashionstore.order.saga.SagaCancellationService;
import com.fashionstore.order.saga.SagaOutbox;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderSaga;
import com.fashionstore.order.entity.enumeration.OrderSagaStatus;
import com.fashionstore.order.entity.enumeration.OrderSagaStep;
import com.fashionstore.order.entity.enumeration.OrderStatus;
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

import com.fashionstore.order.entity.Checkout;
import com.fashionstore.order.entity.CheckoutItem;
import com.fashionstore.order.entity.enumeration.CheckoutStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private IdentityClient identityClient;

    @Mock
    private com.fashionstore.order.service.PromotionService promotionService;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private ShipmentRepository shipmentRepository;

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
                currentUserProvider,
                identityClient,
                promotionService,
                orderStatusHistoryRepository,
                shipmentRepository
        );
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
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
        verify(promotionService, never()).reserve(any(), any(), any(), any(), any());
    }

    @Test
    void createOrder_withCoupon_reservesPromotion() {
        CheckoutItem item = CheckoutItem.builder()
                .variantId("variant-1")
                .productId("product-1")
                .productName("Tee")
                .quantity(1)
                .unitPrice(new BigDecimal("100000"))
                .lineTotal(new BigDecimal("100000"))
                .build();
        Checkout checkout = Checkout.builder()
                .userId("user-1")
                .status(CheckoutStatus.SUBMITTED)
                .paymentMethod(PaymentMethod.COD)
                .paymentProvider(PaymentProvider.COD)
                .couponCode("WELCOME10")
                .subtotalAmount(new BigDecimal("100000"))
                .discountAmount(new BigDecimal("10000"))
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("90000"))
                .items(List.of(item))
                .build();
        checkout.setId("checkout-1");
        when(orderRepository.findByUserIdAndIdempotencyKey("user-1", "checkout-1")).thenReturn(Optional.empty());
        when(checkoutRepository.findForUpdateByIdAndUserId("checkout-1", "user-1")).thenReturn(Optional.of(checkout));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId("order-1");
            return o;
        });

        CreateOrderRequest request = CreateOrderRequest.builder()
                .recipientName("John")
                .recipientPhone("0912345678")
                .shippingAddress("123 Street")
                .build();

        OrderResponse response = service.createOrder("checkout-1", null, request);

        assertEquals("order-1", response.getId());
        ArgumentCaptor<List<com.fashionstore.order.dto.PromotionItemDto>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(promotionService).reserve(eq("WELCOME10"), eq("user-1"), eq("order-1"), eq(new BigDecimal("100000")), itemsCaptor.capture());
        assertEquals("product-1", itemsCaptor.getValue().get(0).getProductId());
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

        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository, org.mockito.Mockito.times(1)).save(historyCaptor.capture());
        assertEquals(OrderStatus.PENDING, historyCaptor.getValue().getFromStatus());
        assertEquals(OrderStatus.CANCELLED, historyCaptor.getValue().getToStatus());
        assertEquals("ORDER_CANCELLED", historyCaptor.getValue().getAction());
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

        assertEquals(OrderErrorCode.ORDER_CANNOT_BE_CANCELLED, exception.getErrorCode());
        verify(sagaOutbox, never()).emit(any(), any());
    }

    @Test
    void cancellingAConfirmedOrderIsRejectedBecauseThatIsARefund() {
        Order order = order(OrderStatus.CONFIRMED);
        when(orderRepository.findWithItemsById("order-1")).thenReturn(Optional.of(order));

        AppException exception = assertThrows(AppException.class,
                () -> service.cancelMyOrder("order-1", null));

        assertEquals(OrderErrorCode.ORDER_CANNOT_BE_CANCELLED, exception.getErrorCode());
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

        assertEquals(OrderErrorCode.ORDER_NOT_FOUND, exception.getErrorCode());
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

        assertEquals(OrderErrorCode.ORDER_SAGA_NOT_FOUND, exception.getErrorCode());
    }

    // ----- updateOrderStatus & order history -----

    @Test
    void updateOrderStatus_toShipping_withoutShipmentOrTrackingCode_throwsShipmentRequired() {
        Order order = order(OrderStatus.PACKED);
        when(orderRepository.findWithItemsById("order-1")).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderId("order-1")).thenReturn(Optional.empty());

        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.SHIPPING);

        AppException ex = assertThrows(AppException.class, () -> service.updateOrderStatus("order-1", request));
        assertEquals(OrderErrorCode.SHIPMENT_REQUIRED, ex.getErrorCode());
    }

    @Test
    void updateOrderStatus_toShipping_whenClientSendsTrackingCodeWithoutShipment_throwsShipmentRequired() {
        Order order = order(OrderStatus.PACKED);
        when(orderRepository.findWithItemsById("order-1")).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderId("order-1")).thenReturn(Optional.empty());

        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.SHIPPING);
        request.setTrackingCode("FAKE_TRACK_123");

        AppException ex = assertThrows(AppException.class, () -> service.updateOrderStatus("order-1", request));
        assertEquals(OrderErrorCode.SHIPMENT_REQUIRED, ex.getErrorCode());
    }

    @Test
    void updateOrderStatus_sameStatus_returnsEarlyWithoutDuplicateHistory() {
        Order order = order(OrderStatus.PROCESSING);
        when(orderRepository.findWithItemsById("order-1")).thenReturn(Optional.of(order));

        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.PROCESSING);

        OrderResponse response = service.updateOrderStatus("order-1", request);
        assertEquals(OrderStatus.PROCESSING, response.getStatus());

        verify(orderStatusHistoryRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateOrderStatus_invalidTransitions_throwsOrderStatusInvalid() {
        // CONFIRMED -> DELIVERED
        Order o1 = order(OrderStatus.CONFIRMED);
        when(orderRepository.findWithItemsById("order-1")).thenReturn(Optional.of(o1));
        UpdateOrderStatusRequest r1 = new UpdateOrderStatusRequest();
        r1.setStatus(OrderStatus.DELIVERED);
        assertEquals(OrderErrorCode.ORDER_STATUS_INVALID,
                assertThrows(AppException.class, () -> service.updateOrderStatus("order-1", r1)).getErrorCode());

        // CANCELLED -> SHIPPING
        Order o2 = order(OrderStatus.CANCELLED);
        when(orderRepository.findWithItemsById("order-2")).thenReturn(Optional.of(o2));
        UpdateOrderStatusRequest r2 = new UpdateOrderStatusRequest();
        r2.setStatus(OrderStatus.SHIPPING);
        assertEquals(OrderErrorCode.ORDER_STATUS_INVALID,
                assertThrows(AppException.class, () -> service.updateOrderStatus("order-2", r2)).getErrorCode());

        // PENDING -> PROCESSING
        Order o3 = order(OrderStatus.PENDING);
        when(orderRepository.findWithItemsById("order-3")).thenReturn(Optional.of(o3));
        UpdateOrderStatusRequest r3 = new UpdateOrderStatusRequest();
        r3.setStatus(OrderStatus.PROCESSING);
        assertEquals(OrderErrorCode.ORDER_STATUS_INVALID,
                assertThrows(AppException.class, () -> service.updateOrderStatus("order-3", r3)).getErrorCode());
    }

    @Test
    void updateOrderStatus_preservesExistingTrackingCodeWhenRequestTrackingCodeIsNull() {
        Order order = order(OrderStatus.PACKED);
        order.setTrackingCode("GHN_ORIGINAL_123");
        when(orderRepository.findWithItemsById("order-1")).thenReturn(Optional.of(order));
        Shipment shipment = Shipment.builder().trackingCode("GHN_ORIGINAL_123").build();
        when(shipmentRepository.findByOrderId("order-1")).thenReturn(Optional.of(shipment));

        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.SHIPPING);
        request.setTrackingCode(null); // Request không gửi trackingCode

        OrderResponse response = service.updateOrderStatus("order-1", request);
        assertEquals(OrderStatus.SHIPPING, response.getStatus());
        assertEquals("GHN_ORIGINAL_123", order.getTrackingCode());
    }

    @Test
    void updateOrderStatus_toShipping_withShipment_succeedsAndRecordsHistory() {
        Order order = order(OrderStatus.PACKED);
        when(orderRepository.findWithItemsById("order-1")).thenReturn(Optional.of(order));
        Shipment shipment = Shipment.builder().trackingCode("GHN123").build();
        when(shipmentRepository.findByOrderId("order-1")).thenReturn(Optional.of(shipment));

        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.SHIPPING);
        request.setReason("Shipped via GHN");

        OrderResponse response = service.updateOrderStatus("order-1", request);
        assertEquals(OrderStatus.SHIPPING, response.getStatus());

        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository, org.mockito.Mockito.times(1)).save(historyCaptor.capture());
        OrderStatusHistory captured = historyCaptor.getValue();
        assertEquals(OrderStatus.PACKED, captured.getFromStatus());
        assertEquals(OrderStatus.SHIPPING, captured.getToStatus());
        assertEquals("ADMIN_UPDATE", captured.getAction());
        assertEquals("Shipped via GHN", captured.getReason());
    }

    @Test
    void updateOrderStatus_fromConfirmedToProcessing_succeedsAndRecordsHistory() {
        Order order = order(OrderStatus.CONFIRMED);
        when(orderRepository.findWithItemsById("order-1")).thenReturn(Optional.of(order));

        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.PROCESSING);
        request.setReason("Preparing items");

        OrderResponse response = service.updateOrderStatus("order-1", request);
        assertEquals(OrderStatus.PROCESSING, response.getStatus());

        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository, org.mockito.Mockito.times(1)).save(historyCaptor.capture());
        OrderStatusHistory captured = historyCaptor.getValue();
        assertEquals(OrderStatus.CONFIRMED, captured.getFromStatus());
        assertEquals(OrderStatus.PROCESSING, captured.getToStatus());
    }

    @Test
    void getOrderHistory_returnsHistoryList() {
        Order order = order(OrderStatus.DELIVERED);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        OrderStatusHistory h1 = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(OrderStatus.CONFIRMED)
                .toStatus(OrderStatus.PACKED)
                .action("SHIPMENT_CREATED")
                .changedBy("admin")
                .reason("Packed")
                .build();
        when(orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtDescIdDesc("order-1"))
                .thenReturn(List.of(h1));

        List<OrderStatusHistoryResponse> result = service.getOrderHistory("order-1");
        assertEquals(1, result.size());
        assertEquals("SHIPMENT_CREATED", result.getFirst().getAction());
    }

    @Test
    void getMyOrderHistory_whenForbiddenUser_throwsOrderNotFound() {
        Order order = order(OrderStatus.DELIVERED);
        order.setUserId("other-user");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        AppException ex = assertThrows(AppException.class, () -> service.getMyOrderHistory("order-1"));
        assertEquals(OrderErrorCode.ORDER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void getMyOrderHistory_whenOwner_returnsHistoryList() {
        Order order = order(OrderStatus.DELIVERED);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        OrderStatusHistory h1 = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(OrderStatus.PENDING)
                .toStatus(OrderStatus.CONFIRMED)
                .action("ORDER_CONFIRMED")
                .changedBy("SYSTEM")
                .build();
        when(orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtDescIdDesc("order-1"))
                .thenReturn(List.of(h1));

        List<OrderStatusHistoryResponse> result = service.getMyOrderHistory("order-1");
        assertEquals(1, result.size());
        assertEquals("ORDER_CONFIRMED", result.getFirst().getAction());
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
