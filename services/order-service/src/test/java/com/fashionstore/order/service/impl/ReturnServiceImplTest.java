package com.fashionstore.order.service.impl;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.dto.RejectReturnRequest;
import com.fashionstore.order.dto.ReturnOrderRequest;
import com.fashionstore.order.dto.ReturnRequestResponse;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderStatusHistory;
import com.fashionstore.order.entity.ReturnRequest;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.entity.enumeration.ReturnRequestStatus;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.OrderStatusHistoryRepository;
import com.fashionstore.order.repository.ReturnRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ReturnServiceImplTest {

    @Mock private ReturnRequestRepository returnRequestRepository;

    @Mock private OrderRepository orderRepository;

    @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock private CurrentUserProvider currentUserProvider;


    @Mock private com.fashionstore.order.client.CatalogFeignClient catalogFeignClient;
    @Mock private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Mock private com.fashionstore.order.client.GhnClient ghnClient;
    @Mock private com.fashionstore.order.repository.ShipmentRepository shipmentRepository;
    @Mock private com.fashionstore.order.event.OutboxEventRecorder outboxEventRecorder;
    @Mock private com.fashionstore.order.service.OrderNotificationService notifications;
    @Mock private com.fashionstore.order.repository.ReturnShipmentRepository returnShipmentRepository;
    @Mock private com.fashionstore.order.service.ReturnPickupService returnPickupService;

    private ObjectMapper objectMapper;
    private ReturnServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service =
                new ReturnServiceImpl(
                        returnRequestRepository,
                        orderRepository,
                        orderStatusHistoryRepository,
                        currentUserProvider,
                        objectMapper,
                        catalogFeignClient,
                        transactionManager,
                        java.time.Clock.systemUTC(),
                        ghnClient,
                        shipmentRepository,
                        outboxEventRecorder,
                        notifications,
                        returnShipmentRepository,
                        returnPickupService);
        org.mockito.Mockito.lenient()
                .when(transactionManager.getTransaction(any()))
                .thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        org.mockito.Mockito.lenient()
                .when(currentUserProvider.getCurrentUserId())
                .thenReturn("user-1");
    }

    @Test
    void missingDeliveredTimestampDoesNotGrantUnlimitedReturnRights() {
        Order order = Order.builder().userId("user-1").status(OrderStatus.DELIVERED).build();
        order.setId("ord-1");
        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));
        assertThatThrownBy(
                        () ->
                                service.createReturnRequest(
                                        "ord-1",
                                        ReturnOrderRequest.builder().reason("Return").build()))
                .isInstanceOf(AppException.class);
        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    void missingPaymentCannotRequestOrCloseRefund() {
        Order order =
                Order.builder()
                        .userId("user-1")
                        .status(OrderStatus.DELIVERED)
                        .orderCode("ORDER-1")
                        .totalAmount(new BigDecimal("100000"))
                        .build();
        order.setId("ord-1");
        ReturnRequest request =
                ReturnRequest.builder()
                        .order(order)
                        .userId("user-1")
                        .reason("Return")
                        .status(ReturnRequestStatus.RECEIVED)
                        .qcPassed(true)
                        .build();
        request.setId("ret-1");
        when(returnRequestRepository.findByIdForUpdate("ret-1")).thenReturn(Optional.of(request));
        when(returnRequestRepository.findOrderIdByReturnId("ret-1"))
                .thenReturn(Optional.of("ord-1"));
        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));
        org.mockito.Mockito.lenient()
                .when(returnRequestRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        assertThatThrownBy(() -> service.refundReturn("ret-1", null))
                .isInstanceOf(AppException.class);
        assertThat(request.getStatus()).isEqualTo(ReturnRequestStatus.RECEIVED);
        assertThat(request.getClosedAt()).isNull();
        verify(outboxEventRecorder, never()).saveMessage(any(), any(), any());
    }

    @Test
    void customerProvidedRefundAmountCannotOverrideWholeOrderTotal() {
        Order order =
                Order.builder()
                        .userId("user-1")
                        .status(OrderStatus.DELIVERED)
                        .orderCode("ORDER-1")
                        .totalAmount(new BigDecimal("100000"))
                        .build();
        order.setId("ord-1");
        ReturnRequest request =
                ReturnRequest.builder()
                        .order(order)
                        .userId("user-1")
                        .reason("Return")
                        .status(ReturnRequestStatus.RECEIVED)
                        .qcPassed(true)
                        .build();
        request.setId("ret-1");
        when(returnRequestRepository.findByIdForUpdate("ret-1")).thenReturn(Optional.of(request));
        when(returnRequestRepository.findOrderIdByReturnId("ret-1"))
                .thenReturn(Optional.of("ord-1"));
        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));
        org.mockito.Mockito.lenient()
                .when(returnRequestRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        assertThatThrownBy(() -> service.refundReturn("ret-1", new BigDecimal("900000")))
                .isInstanceOf(AppException.class);
    }

    @Test
    void rejectReturnClosesFreezeAndUsesOrderBeforeCaseLock() {
        Order order = Order.builder().userId("user-1").status(OrderStatus.DELIVERED).build();
        order.setId("ord-1");
        ReturnRequest request =
                ReturnRequest.builder()
                        .order(order)
                        .userId("user-1")
                        .reason("wrong item")
                        .status(ReturnRequestStatus.PENDING)
                        .build();
        request.setId("ret-1");
        when(returnRequestRepository.findByIdForUpdate("ret-1")).thenReturn(Optional.of(request));
        if (request.getOrder().getId() == null) request.getOrder().setId("ord-1");
        org.mockito.Mockito.lenient()
                .when(returnRequestRepository.findOrderIdByReturnId("ret-1"))
                .thenReturn(Optional.of(request.getOrder().getId()));
        org.mockito.Mockito.lenient()
                .when(orderRepository.findByIdForUpdate(request.getOrder().getId()))
                .thenReturn(Optional.of(request.getOrder()));
        org.mockito.Mockito.lenient()
                .when(orderRepository.findByIdForUpdate("ord-1"))
                .thenReturn(Optional.of(order));
        when(returnRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.rejectReturn(
                "ret-1", RejectReturnRequest.builder().rejectReason("outside policy").build());
        assertThat(request.getClosedAt()).isNotNull();
        var locks = org.mockito.Mockito.inOrder(orderRepository, returnRequestRepository);
        locks.verify(orderRepository).findByIdForUpdate("ord-1");
        locks.verify(returnRequestRepository).findByIdForUpdate("ret-1");
    }

    @Test
    void createReturnRequest_whenOrderDelivered_createsPendingRequestAndRecordsHistory() {
        Order order = Order.builder().userId("user-1").status(OrderStatus.DELIVERED).build();
        order.setId("ord-1");
        order.setUpdatedAt(LocalDateTime.now().minusDays(1));
        order.setDeliveredAt(java.time.Instant.now().minusSeconds(24 * 3600));

        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));
        when(returnRequestRepository.findByOrderIdForUpdate("ord-1")).thenReturn(Optional.empty());
        when(returnRequestRepository.save(any(ReturnRequest.class)))
                .thenAnswer(
                        inv -> {
                            ReturnRequest req = inv.getArgument(0);
                            req.setId("ret-1");
                            return req;
                        });

        stubEvidence("media-1", "user-1");
        stubEvidence("media-2", "user-1");
        ReturnOrderRequest request =
                ReturnOrderRequest.builder()
                        .reason("Sản phẩm lỗi đường chỉ")
                        .images(List.of("media-1", "media-2"))
                        .build();

        ReturnRequestResponse response = service.createReturnRequest("ord-1", request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("ret-1");
        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.REQUESTED);
        assertThat(response.getReason()).isEqualTo("Sản phẩm lỗi đường chỉ");
        assertThat(response.getImages()).containsExactly("media-1", "media-2");

        // Order vẫn giữ nguyên DELIVERED
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        ArgumentCaptor<OrderStatusHistory> historyCaptor =
                ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository, times(1)).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getAction()).isEqualTo("RETURN_REQUESTED");
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(historyCaptor.getValue().getChangedBy()).isEqualTo("user-1");
    }

    @Test
    void createReturnRequest_whenReasonBlank_fallbacksToDefaultReason() {
        Order order = Order.builder().userId("user-1").status(OrderStatus.DELIVERED).build();
        order.setId("ord-1");
        order.setUpdatedAt(LocalDateTime.now().minusDays(1));
        order.setDeliveredAt(java.time.Instant.now().minusSeconds(24 * 3600));

        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));
        when(returnRequestRepository.findByOrderIdForUpdate("ord-1")).thenReturn(Optional.empty());
        when(returnRequestRepository.save(any(ReturnRequest.class)))
                .thenAnswer(
                        inv -> {
                            ReturnRequest req = inv.getArgument(0);
                            req.setId("ret-1");
                            return req;
                        });

        ReturnOrderRequest request = ReturnOrderRequest.builder().reason("   ").build();

        ReturnRequestResponse response = service.createReturnRequest("ord-1", request);

        assertThat(response.getReason()).isEqualTo("Khách hàng yêu cầu trả hàng");
    }

    @Test
    void createReturnRequest_whenExceeds7DaysReturnWindow_throwsOrderReturnNotAllowed() {
        Order order = Order.builder().userId("user-1").status(OrderStatus.DELIVERED).build();
        order.setId("ord-1");
        order.setUpdatedAt(LocalDateTime.now().minusDays(8));
        order.setDeliveredAt(java.time.Instant.now().minusSeconds(8 * 24 * 3600));

        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));

        ReturnOrderRequest request =
                ReturnOrderRequest.builder().reason("Đổi trả sau 8 ngày").build();

        assertThatThrownBy(() -> service.createReturnRequest("ord-1", request))
                .isInstanceOf(AppException.class)
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(OrderErrorCode.ORDER_RETURN_NOT_ALLOWED));

        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    void createReturnRequest_whenOrderNotDelivered_throwsOrderReturnNotAllowed() {
        Order order = Order.builder().userId("user-1").status(OrderStatus.SHIPPING).build();
        order.setId("ord-1");

        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));

        ReturnOrderRequest request = ReturnOrderRequest.builder().reason("Không ưng ý").build();

        assertThatThrownBy(() -> service.createReturnRequest("ord-1", request))
                .isInstanceOf(AppException.class)
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(OrderErrorCode.ORDER_RETURN_NOT_ALLOWED));

        verify(returnRequestRepository, never()).save(any());
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    void createReturnRequest_whenOrderNotOwnedByUser_throwsOrderNotFound() {
        Order order =
                Order.builder()
                        .userId("user-2") // khác user-1
                        .status(OrderStatus.DELIVERED)
                        .build();
        order.setId("ord-1");

        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));

        ReturnOrderRequest request = ReturnOrderRequest.builder().reason("Muốn đổi trả").build();

        assertThatThrownBy(() -> service.createReturnRequest("ord-1", request))
                .isInstanceOf(AppException.class)
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));

        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    void createReturnRequest_whenAlreadyPending_isIdempotent() {
        Order order = Order.builder().userId("user-1").status(OrderStatus.DELIVERED).build();
        order.setId("ord-1");
        order.setUpdatedAt(LocalDateTime.now().minusDays(1));
        order.setDeliveredAt(java.time.Instant.now().minusSeconds(24 * 3600));

        ReturnRequest existing =
                ReturnRequest.builder()
                        .order(order)
                        .userId("user-1")
                        .reason("Yêu cầu cũ")
                        .status(ReturnRequestStatus.PENDING)
                        .build();
        existing.setId("ret-existing");

        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));
        when(returnRequestRepository.findByOrderIdForUpdate("ord-1"))
                .thenReturn(Optional.of(existing));

        ReturnOrderRequest request = ReturnOrderRequest.builder().reason("Yêu cầu mới").build();
        ReturnRequestResponse response = service.createReturnRequest("ord-1", request);

        assertThat(response.getId()).isEqualTo("ret-existing");
        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.PENDING);
        verify(returnRequestRepository, never()).save(any());
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    void createReturnRequest_whenAlreadyProcessed_throwsReturnRequestAlreadyProcessed() {
        Order order = Order.builder().userId("user-1").status(OrderStatus.DELIVERED).build();
        order.setId("ord-1");
        order.setUpdatedAt(LocalDateTime.now().minusDays(1));
        order.setDeliveredAt(java.time.Instant.now().minusSeconds(24 * 3600));

        ReturnRequest existing =
                ReturnRequest.builder()
                        .order(order)
                        .userId("user-1")
                        .status(ReturnRequestStatus.APPROVED)
                        .build();

        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));
        when(returnRequestRepository.findByOrderIdForUpdate("ord-1"))
                .thenReturn(Optional.of(existing));

        ReturnOrderRequest request = ReturnOrderRequest.builder().reason("Muốn trả tiếp").build();

        assertThatThrownBy(() -> service.createReturnRequest("ord-1", request))
                .isInstanceOf(AppException.class)
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(
                                                OrderErrorCode.RETURN_REQUEST_ALREADY_PROCESSED));
    }

    private Order deliveredOrderWithPickupAddress() {
        Order order =
                Order.builder()
                        .userId("user-1")
                        .status(OrderStatus.DELIVERED)
                        .orderCode("ORD-1")
                        .totalAmount(new BigDecimal("500000"))
                        .paymentId("pay-1")
                        .address(
                                com.fashionstore.order.entity.ShippingAddress.builder()
                                        .recipientName("Nguyen Van A")
                                        .recipientPhone("0987654321")
                                        .province("Hồ Chí Minh")
                                        .ward("Phường Sài Gòn")
                                        .build())
                        .shippingAddress("123 Le Loi, Phường Sài Gòn, Hồ Chí Minh")
                        .items(
                                new java.util.ArrayList<>(
                                        java.util.List.of(
                                                com.fashionstore.order.entity.OrderItem.builder()
                                                        .productName("Áo khoác")
                                                        .variantId("var-1")
                                                        .quantity(1)
                                                        .unitPrice(new BigDecimal("300000"))
                                                        .build())))
                        .build();
        order.setId("ord-1");
        return order;
    }

    private ReturnRequest requestedReturn(Order order) {
        ReturnRequest returnRequest =
                ReturnRequest.builder()
                        .order(order)
                        .userId("user-1")
                        .reason("Hàng rách vải")
                        .status(ReturnRequestStatus.PENDING)
                        .build();
        returnRequest.setId("ret-1");
        when(returnRequestRepository.findByIdForUpdate("ret-1"))
                .thenReturn(Optional.of(returnRequest));
        org.mockito.Mockito.lenient()
                .when(returnRequestRepository.findOrderIdByReturnId("ret-1"))
                .thenReturn(Optional.of("ord-1"));
        org.mockito.Mockito.lenient()
                .when(orderRepository.findByIdForUpdate("ord-1"))
                .thenReturn(Optional.of(order));
        org.mockito.Mockito.lenient()
                .when(returnRequestRepository.save(any(ReturnRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        return returnRequest;
    }

    @Test
    void approveReturn_whenPending_createsTheGhnReturnOrderAndApproves() {
        Order order = deliveredOrderWithPickupAddress();
        ReturnRequest returnRequest = requestedReturn(order);
        when(shipmentRepository.findByOrderId("ord-1"))
                .thenReturn(
                        Optional.of(
                                com.fashionstore.order.entity.Shipment.builder()
                                        .weightGram(600)
                                        .build()));
        when(ghnClient.createReturnOrder(any())).thenReturn("GHN-RET-1");
        java.util.concurrent.atomic.AtomicReference<com.fashionstore.order.entity.ReturnShipment>
                saved = new java.util.concurrent.atomic.AtomicReference<>();
        when(returnShipmentRepository.save(any(com.fashionstore.order.entity.ReturnShipment.class)))
                .thenAnswer(
                        inv -> {
                            saved.set(inv.getArgument(0));
                            return inv.getArgument(0);
                        });
        when(returnShipmentRepository.findByReturnRequestId("ret-1"))
                .thenAnswer(inv -> Optional.ofNullable(saved.get()));

        ReturnRequestResponse response = service.approveReturn("ret-1");

        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.APPROVED);
        assertThat(response.getReviewedBy()).isEqualTo("user-1");
        assertThat(response.getReturnTrackingCode()).isEqualTo("GHN-RET-1");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        ArgumentCaptor<com.fashionstore.order.dto.ReturnPickup> pickup =
                ArgumentCaptor.forClass(com.fashionstore.order.dto.ReturnPickup.class);
        verify(ghnClient).createReturnOrder(pickup.capture());
        assertThat(pickup.getValue().returnId()).isEqualTo("ret-1");
        assertThat(pickup.getValue().orderCode()).isEqualTo("ORD-1");
        assertThat(pickup.getValue().senderName()).isEqualTo("Nguyen Van A");
        assertThat(pickup.getValue().senderPhone()).isEqualTo("0987654321");
        assertThat(pickup.getValue().senderAddress()).isEqualTo("123 Le Loi, Phường Sài Gòn, Hồ Chí Minh");
        assertThat(pickup.getValue().senderWard()).isEqualTo("Phường Sài Gòn");
        assertThat(pickup.getValue().senderProvince()).isEqualTo("Hồ Chí Minh");
        assertThat(pickup.getValue().weightGram()).isEqualTo(600);
        assertThat(pickup.getValue().items()).hasSize(1);

        assertThat(saved.get().getGhnOrderCode()).isEqualTo("GHN-RET-1");
        assertThat(saved.get().getReturnRequest()).isSameAs(returnRequest);
        assertThat(saved.get().getStatus())
                .isEqualTo(com.fashionstore.order.entity.enumeration.ReturnShipmentStatus.READY_TO_PICK);

        ArgumentCaptor<OrderStatusHistory> historyCaptor =
                ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getAction()).isEqualTo("RETURN_APPROVED");
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(notifications)
                .sendReturnStatusNotification(
                        eq(order),
                        eq("Yêu cầu trả hàng đã được duyệt"),
                        org.mockito.ArgumentMatchers.contains("GHN-RET-1"));
    }

    @Test
    void approveReturn_whenGhnFails_keepsTheRequestOpenForARetry() {
        Order order = deliveredOrderWithPickupAddress();
        ReturnRequest returnRequest = requestedReturn(order);
        when(ghnClient.createReturnOrder(any()))
                .thenThrow(new AppException(com.fashionstore.common.exception.ErrorCode.UPSTREAM_SERVICE_ERROR));

        assertThatThrownBy(() -> service.approveReturn("ret-1")).isInstanceOf(AppException.class);

        assertThat(returnRequest.getStatus()).isEqualTo(ReturnRequestStatus.PENDING);
        assertThat(returnRequest.getApprovedAt()).isNull();
        verify(returnShipmentRepository, never()).save(any());
        verify(orderStatusHistoryRepository, never()).save(any());
        verify(notifications, never()).sendReturnStatusNotification(any(), any(), any());
    }

    @Test
    void approveReturn_whenRejectedWhileGhnWasBeingCalled_cancelsTheOrphanGhnOrder() {
        Order order = deliveredOrderWithPickupAddress();
        ReturnRequest returnRequest = requestedReturn(order);
        when(ghnClient.createReturnOrder(any()))
                .thenAnswer(
                        inv -> {
                            returnRequest.setStatus(ReturnRequestStatus.REJECTED);
                            return "GHN-RET-1";
                        });

        assertThatThrownBy(() -> service.approveReturn("ret-1"))
                .isInstanceOfSatisfying(
                        AppException.class,
                        ex ->
                                assertThat(ex.getErrorCode())
                                        .isEqualTo(OrderErrorCode.RETURN_WORKFLOW_CONFLICT));

        verify(ghnClient).cancelPendingShipment("GHN-RET-1");
        verify(returnShipmentRepository, never()).save(any());
        assertThat(returnRequest.getStatus()).isEqualTo(ReturnRequestStatus.REJECTED);
    }

    @Test
    void approveReturn_whenAnotherAdminApprovedMeanwhile_returnsItWithoutTouchingTheSharedGhnOrder() {
        Order order = deliveredOrderWithPickupAddress();
        ReturnRequest returnRequest = requestedReturn(order);
        when(ghnClient.createReturnOrder(any()))
                .thenAnswer(
                        inv -> {
                            returnRequest.setStatus(ReturnRequestStatus.APPROVED);
                            return "GHN-RET-1";
                        });

        ReturnRequestResponse response = service.approveReturn("ret-1");

        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.APPROVED);
        verify(ghnClient, never()).cancelPendingShipment(any());
        verify(returnShipmentRepository, never()).save(any());
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    void approveReturn_whenOrderStatusNotDelivered_throwsOrderStatusInvalid() {
        Order order =
                Order.builder()
                        .userId("user-1")
                        .status(OrderStatus.PROCESSING)
                        .totalAmount(new BigDecimal("300000"))
                        .paymentId("pay-1")
                        .build();
        order.setId("ord-1");

        ReturnRequest returnRequest =
                ReturnRequest.builder()
                        .order(order)
                        .userId("user-1")
                        .reason("Lỗi")
                        .status(ReturnRequestStatus.PENDING)
                        .build();
        returnRequest.setId("ret-1");

        when(returnRequestRepository.findByIdForUpdate("ret-1"))
                .thenReturn(Optional.of(returnRequest));
        if (returnRequest.getOrder().getId() == null) returnRequest.getOrder().setId("ord-1");
        org.mockito.Mockito.lenient()
                .when(returnRequestRepository.findOrderIdByReturnId("ret-1"))
                .thenReturn(Optional.of(returnRequest.getOrder().getId()));
        org.mockito.Mockito.lenient()
                .when(orderRepository.findByIdForUpdate(returnRequest.getOrder().getId()))
                .thenReturn(Optional.of(returnRequest.getOrder()));
        when(orderRepository.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.approveReturn("ret-1"))
                .isInstanceOf(AppException.class)
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(OrderErrorCode.ORDER_STATUS_INVALID));

        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    void approveReturn_whenAlreadyApproved_isIdempotent() {
        Order order = Order.builder().userId("user-1").status(OrderStatus.DELIVERED).build();
        order.setId("ord-1");

        ReturnRequest returnRequest =
                ReturnRequest.builder()
                        .order(order)
                        .userId("user-1")
                        .status(ReturnRequestStatus.RETURN_APPROVED)
                        .build();
        returnRequest.setId("ret-1");

        when(returnRequestRepository.findByIdForUpdate("ret-1"))
                .thenReturn(Optional.of(returnRequest));
        if (returnRequest.getOrder().getId() == null) returnRequest.getOrder().setId("ord-1");
        org.mockito.Mockito.lenient()
                .when(returnRequestRepository.findOrderIdByReturnId("ret-1"))
                .thenReturn(Optional.of(returnRequest.getOrder().getId()));
        org.mockito.Mockito.lenient()
                .when(orderRepository.findByIdForUpdate(returnRequest.getOrder().getId()))
                .thenReturn(Optional.of(returnRequest.getOrder()));

        ReturnRequestResponse response = service.approveReturn("ret-1");

        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.RETURN_APPROVED);
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    void approveReturn_whenAlreadyRejected_throwsReturnRequestAlreadyProcessed() {
        Order order = Order.builder().userId("user-1").status(OrderStatus.DELIVERED).build();
        ReturnRequest returnRequest =
                ReturnRequest.builder().order(order).status(ReturnRequestStatus.REJECTED).build();
        returnRequest.setId("ret-1");

        when(returnRequestRepository.findByIdForUpdate("ret-1"))
                .thenReturn(Optional.of(returnRequest));
        if (returnRequest.getOrder().getId() == null) returnRequest.getOrder().setId("ord-1");
        org.mockito.Mockito.lenient()
                .when(returnRequestRepository.findOrderIdByReturnId("ret-1"))
                .thenReturn(Optional.of(returnRequest.getOrder().getId()));
        org.mockito.Mockito.lenient()
                .when(orderRepository.findByIdForUpdate(returnRequest.getOrder().getId()))
                .thenReturn(Optional.of(returnRequest.getOrder()));

        assertThatThrownBy(() -> service.approveReturn("ret-1"))
                .isInstanceOf(AppException.class)
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(
                                                OrderErrorCode.RETURN_REQUEST_ALREADY_PROCESSED));
    }

    @Test
    void rejectReturn_whenPending_rejectsRequestAndKeepsOrderDelivered() {
        Order order = Order.builder().userId("user-1").status(OrderStatus.DELIVERED).build();
        order.setId("ord-1");

        ReturnRequest returnRequest =
                ReturnRequest.builder()
                        .order(order)
                        .userId("user-1")
                        .reason("Đổi ý không thích")
                        .status(ReturnRequestStatus.PENDING)
                        .build();
        returnRequest.setId("ret-1");

        when(returnRequestRepository.findByIdForUpdate("ret-1"))
                .thenReturn(Optional.of(returnRequest));
        if (returnRequest.getOrder().getId() == null) returnRequest.getOrder().setId("ord-1");
        org.mockito.Mockito.lenient()
                .when(returnRequestRepository.findOrderIdByReturnId("ret-1"))
                .thenReturn(Optional.of(returnRequest.getOrder().getId()));
        org.mockito.Mockito.lenient()
                .when(orderRepository.findByIdForUpdate(returnRequest.getOrder().getId()))
                .thenReturn(Optional.of(returnRequest.getOrder()));
        when(returnRequestRepository.save(any(ReturnRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RejectReturnRequest rejectRequest =
                RejectReturnRequest.builder()
                        .rejectReason("Sản phẩm đã qua sử dụng và cắt mác")
                        .build();

        ReturnRequestResponse response = service.rejectReturn("ret-1", rejectRequest);

        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.REJECTED);
        assertThat(response.getRejectReason()).isEqualTo("Sản phẩm đã qua sử dụng và cắt mác");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED); // Giữ nguyên DELIVERED

        ArgumentCaptor<OrderStatusHistory> historyCaptor =
                ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository, times(1)).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getAction()).isEqualTo("RETURN_REJECTED");
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void rejectReturn_whenMissingReason_throwsReturnRejectReasonRequired() {
        RejectReturnRequest rejectRequest =
                RejectReturnRequest.builder().rejectReason("   ").build();

        assertThatThrownBy(() -> service.rejectReturn("ret-1", rejectRequest))
                .isInstanceOf(AppException.class)
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(OrderErrorCode.RETURN_REJECT_REASON_REQUIRED));
    }

    @Test
    void getReturnRequestByOrderId_whenFoundAndAuthorized_returnsResponse() {
        Order order = Order.builder().userId("user-1").build();
        order.setId("ord-1");

        ReturnRequest returnRequest =
                ReturnRequest.builder()
                        .order(order)
                        .userId("user-1")
                        .reason("Sai size")
                        .status(ReturnRequestStatus.PENDING)
                        .build();
        returnRequest.setId("ret-1");

        when(returnRequestRepository.findByOrderId("ord-1")).thenReturn(Optional.of(returnRequest));

        ReturnRequestResponse response = service.getReturnRequestByOrderId("ord-1");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("ret-1");
        assertThat(response.getOrderId()).isEqualTo("ord-1");
        assertThat(response.getReason()).isEqualTo("Sai size");
    }

    @Test
    void getReturnRequestByOrderId_whenBelongsToDifferentUser_throwsNotFound() {
        Order order = Order.builder().userId("user-2").build();
        ReturnRequest returnRequest =
                ReturnRequest.builder()
                        .order(order)
                        .userId("user-2") // khác currentUserId
                        .build();

        when(returnRequestRepository.findByOrderId("ord-1")).thenReturn(Optional.of(returnRequest));

        assertThatThrownBy(() -> service.getReturnRequestByOrderId("ord-1"))
                .isInstanceOf(AppException.class)
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(OrderErrorCode.RETURN_REQUEST_NOT_FOUND));
    }

    // ----- ảnh khách gửi phải là media RETURN_EVIDENCE của chính khách -----

    @Test
    void createReturnRequest_rejectsImageNotOwnedByCustomer() {
        stubEvidence("media-1", "another-user");

        assertThatThrownBy(
                        () ->
                                service.createReturnRequest(
                                        "ord-1",
                                        ReturnOrderRequest.builder()
                                                .reason("Lỗi")
                                                .images(List.of("media-1"))
                                                .build()))
                .isInstanceOf(AppException.class)
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(OrderErrorCode.RETURN_EVIDENCE_INVALID));
        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    void customerEvidenceReadUrlUsesCustomerAsOwner() {
        Order order = deliveredOrder();
        ReturnRequest request = returnRequest(order, ReturnRequestStatus.REQUESTED);
        request.setImages("[\"media-1\"]");
        when(returnRequestRepository.findById("ret-1")).thenReturn(Optional.of(request));
        when(catalogFeignClient.getEvidenceReadUrl("media-1", "user-1"))
                .thenReturn(
                        com.fashionstore.common.dto.ApiResponse.<String>builder()
                                .data("https://signed")
                                .build());

        assertThat(service.getCustomerEvidenceReadUrl("ret-1", "media-1"))
                .isEqualTo("https://signed");
        assertThatThrownBy(() -> service.getCustomerEvidenceReadUrl("ret-1", "media-x"))
                .isInstanceOf(AppException.class);
    }

    // ----- quá hạn gửi hàng trả -----

    @Test
    void expireUnsentReturns_cancelsApprovedReturnPastSendBackDeadlineAndNotifies() {
        Order order = deliveredOrder();
        ReturnRequest request = returnRequest(order, ReturnRequestStatus.APPROVED);
        java.time.Instant now = java.time.Instant.now();
        request.setApprovedAt(now.minus(java.time.Duration.ofDays(8)));
        when(returnRequestRepository.findUnsentCustomerReturnIds(any()))
                .thenReturn(List.of("ret-1"));
        stubLocks(order, request);

        service.expireUnsentReturns(now);

        assertThat(request.getStatus()).isEqualTo(ReturnRequestStatus.CANCELLED);
        assertThat(request.getClosedAt()).isNotNull();
        assertThat(request.getRejectReason()).isEqualTo("Quá hạn gửi hàng trả");
        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo("RETURN_EXPIRED");
        assertThat(history.getValue().getChangedBy()).isEqualTo("SYSTEM");
        verify(notifications)
                .sendReturnStatusNotification(
                        org.mockito.ArgumentMatchers.eq(order),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.contains("Quá hạn gửi hàng trả"));
    }

    @Test
    void expireUnsentReturns_skipsReturnReceivedMeanwhile() {
        Order order = deliveredOrder();
        ReturnRequest request = returnRequest(order, ReturnRequestStatus.RECEIVED);
        request.setApprovedAt(java.time.Instant.now().minus(java.time.Duration.ofDays(8)));
        request.setReceivedAt(java.time.Instant.now());
        when(returnRequestRepository.findUnsentCustomerReturnIds(any()))
                .thenReturn(List.of("ret-1"));
        stubLocks(order, request);

        service.expireUnsentReturns(java.time.Instant.now());

        assertThat(request.getStatus()).isEqualTo(ReturnRequestStatus.RECEIVED);
        verify(orderStatusHistoryRepository, never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(notifications);
    }

    // ----- thông báo cho khách -----

    @Test
    void approveReturn_notifiesCustomerWithSendBackDeadline() {
        Order order = deliveredOrder();
        ReturnRequest request = returnRequest(order, ReturnRequestStatus.REQUESTED);
        stubLocks(order, request);

        service.approveReturn("ret-1");

        verify(notifications)
                .sendReturnStatusNotification(
                        org.mockito.ArgumentMatchers.eq(order),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.contains("trước ngày"));
    }

    @Test
    void rejectReturn_notifiesCustomerWithReason() {
        Order order = deliveredOrder();
        ReturnRequest request = returnRequest(order, ReturnRequestStatus.REQUESTED);
        stubLocks(order, request);

        service.rejectReturn(
                "ret-1", RejectReturnRequest.builder().rejectReason("Quá thời hạn").build());

        verify(notifications)
                .sendReturnStatusNotification(
                        org.mockito.ArgumentMatchers.eq(order),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.contains("Quá thời hạn"));
    }

    @Test
    void receiveReturn_failedQcTellsCustomerSupportWillContact() {
        Order order = deliveredOrder();
        ReturnRequest request = returnRequest(order, ReturnRequestStatus.APPROVED);
        stubLocks(order, request);
        stubEvidence("qc-1", "user-1");

        service.receiveReturn(
                "ret-1",
                new com.fashionstore.order.dto.ReceiveReturnRequest(
                        false, "Mất tem", List.of("qc-1")));

        verify(notifications)
                .sendReturnStatusNotification(
                        org.mockito.ArgumentMatchers.eq(order),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.contains("CSKH sẽ liên hệ"));
    }

    @Test
    void receiveReturn_acceptsAReturnThatGhnAlreadyPickedUp() {
        Order order = deliveredOrder();
        ReturnRequest request = returnRequest(order, ReturnRequestStatus.IN_TRANSIT);
        stubLocks(order, request);

        var response =
                service.receiveReturn(
                        "ret-1",
                        new com.fashionstore.order.dto.ReceiveReturnRequest(true, "Nguyên tem", List.of()));

        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.RECEIVED);
        assertThat(request.getReceivedAt()).isNotNull();
    }

    // ----- lịch sử ghi đúng trạng thái order -----

    @Test
    void receiveCarrierReturn_recordsReturnedStatusAndDoesNotNotify() {
        Order order = deliveredOrder();
        order.setStatus(OrderStatus.RETURNED);
        ReturnRequest request = returnRequest(order, ReturnRequestStatus.APPROVED);
        request.setKind(com.fashionstore.order.entity.enumeration.ReturnRequestKind.CARRIER_RETURN);
        stubLocks(order, request);

        service.receiveReturn(
                "ret-1",
                new com.fashionstore.order.dto.ReceiveReturnRequest(true, "Nguyên tem", List.of()));

        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository).save(history.capture());
        assertThat(history.getValue().getFromStatus()).isEqualTo(OrderStatus.RETURNED);
        assertThat(history.getValue().getToStatus()).isEqualTo(OrderStatus.RETURNED);
        org.mockito.Mockito.verifyNoInteractions(notifications);
    }

    @Test
    void cancelCarrierReturn_recordsReturnedStatus() {
        Order order = deliveredOrder();
        order.setStatus(OrderStatus.RETURNED);
        ReturnRequest request = returnRequest(order, ReturnRequestStatus.APPROVED);
        request.setKind(com.fashionstore.order.entity.enumeration.ReturnRequestKind.CARRIER_RETURN);
        stubLocks(order, request);

        service.cancelReturn(
                "ret-1", new com.fashionstore.order.dto.CancelReturnRequest("Nhầm đơn"));

        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository).save(history.capture());
        assertThat(history.getValue().getFromStatus()).isEqualTo(OrderStatus.RETURNED);
        assertThat(history.getValue().getToStatus()).isEqualTo(OrderStatus.RETURNED);
    }

    @Test
    void cancelReturn_whenTheReturnHasAGhnShipment_goesThroughThePickupServiceSoGhnIsCancelledToo() {
        Order order = deliveredOrder();
        ReturnRequest request = returnRequest(order, ReturnRequestStatus.APPROVED);
        when(returnRequestRepository.findById("ret-1")).thenReturn(Optional.of(request));
        when(returnShipmentRepository.findByReturnRequestId("ret-1"))
                .thenReturn(Optional.of(com.fashionstore.order.entity.ReturnShipment.builder().ghnOrderCode("GHN-RET-1").build()));

        service.cancelReturn("ret-1", new com.fashionstore.order.dto.CancelReturnRequest("Khách đổi ý"));

        verify(returnPickupService).cancelByAdmin("ret-1", "Khách đổi ý");
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    private Order deliveredOrder() {
        Order order =
                Order.builder()
                        .userId("user-1")
                        .orderCode("ORD-1")
                        .status(OrderStatus.DELIVERED)
                        .totalAmount(new BigDecimal("500000"))
                        .paymentId("pay-1")
                        .deliveredAt(java.time.Instant.now().minusSeconds(24 * 3600))
                        .build();
        order.setId("ord-1");
        return order;
    }

    private ReturnRequest returnRequest(Order order, ReturnRequestStatus status) {
        ReturnRequest request =
                ReturnRequest.builder()
                        .order(order)
                        .userId("user-1")
                        .reason("Lỗi")
                        .status(status)
                        .build();
        request.setId("ret-1");
        return request;
    }

    private void stubLocks(Order order, ReturnRequest request) {
        org.mockito.Mockito.lenient()
                .when(returnRequestRepository.findOrderIdByReturnId("ret-1"))
                .thenReturn(Optional.of("ord-1"));
        org.mockito.Mockito.lenient()
                .when(orderRepository.findByIdForUpdate("ord-1"))
                .thenReturn(Optional.of(order));
        org.mockito.Mockito.lenient()
                .when(returnRequestRepository.findByIdForUpdate("ret-1"))
                .thenReturn(Optional.of(request));
        org.mockito.Mockito.lenient()
                .when(returnRequestRepository.save(any(ReturnRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubEvidence(String mediaId, String ownerId) {
        when(catalogFeignClient.getReturnEvidence(
                        org.mockito.ArgumentMatchers.eq(mediaId),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(
                        com.fashionstore.common.dto.ApiResponse
                                .<com.fashionstore.order.dto.ReturnEvidenceMetadata>builder()
                                .data(
                                        new com.fashionstore.order.dto.ReturnEvidenceMetadata(
                                                mediaId,
                                                ownerId,
                                                "RETURN_EVIDENCE",
                                                "ACTIVE",
                                                "PRIVATE",
                                                "image/jpeg"))
                                .build());
    }
}
