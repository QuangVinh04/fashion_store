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
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.order.client.CatalogClient;
import com.fashionstore.order.client.GhnClient;
import com.fashionstore.order.dto.CreateShipmentRequest;
import com.fashionstore.order.dto.GhnWebhookPayload;
import com.fashionstore.order.dto.ProductVariantDto;
import com.fashionstore.order.dto.ShipmentResponse;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderItem;
import com.fashionstore.order.entity.OrderStatusHistory;
import com.fashionstore.order.entity.Shipment;
import com.fashionstore.order.entity.ShippingAddress;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.entity.enumeration.ShipmentProvider;
import com.fashionstore.order.entity.enumeration.ShipmentStatus;
import com.fashionstore.order.event.OutboxEventRecorder;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.OrderStatusHistoryRepository;
import com.fashionstore.order.repository.ShipmentRepository;

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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentServiceImplTest {

    @Mock ShipmentRepository shipmentRepository;

    @Mock OrderRepository orderRepository;

    @Mock GhnClient ghnClient;

    @Mock CatalogClient catalogClient;

    @Mock CurrentUserProvider currentUserProvider;

    @Mock OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock com.fashionstore.order.service.OrderNotificationService orderNotificationService;

    @Mock OutboxEventRecorder outboxEventRecorder;

    ShipmentServiceImpl service;
    com.fashionstore.order.service.OrderCancellationSagaService cancellation =
            org.mockito.Mockito.mock(
                    com.fashionstore.order.service.OrderCancellationSagaService.class);

    @BeforeEach
    void setUp() {
        var callbackShipment = new java.util.concurrent.atomic.AtomicReference<Shipment>();
        when(shipmentRepository.findOrderIdByGhnOrderCode(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(
                        call -> {
                            var found = shipmentRepository.findByGhnOrderCode(call.getArgument(0));
                            callbackShipment.set(found.orElse(null));
                            return found.map(value -> value.getOrder().getId());
                        });
        when(shipmentRepository.findOrderIdByTrackingCode(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(
                        call -> {
                            var found = shipmentRepository.findByTrackingCode(call.getArgument(0));
                            callbackShipment.set(found.orElse(null));
                            return found.map(value -> value.getOrder().getId());
                        });
        when(shipmentRepository.findByOrderIdForUpdate(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> java.util.Optional.ofNullable(callbackShipment.get()));
        when(orderRepository.findByIdForUpdate(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(
                        call ->
                                java.util.Optional.ofNullable(callbackShipment.get())
                                        .map(Shipment::getOrder));
        when(orderRepository.findWithItemsById(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> orderRepository.findByIdForUpdate(call.getArgument(0)));
        var receipts =
                new com.fashionstore.order.service.OrderReceiptService(
                        orderRepository,
                        org.mockito.Mockito.mock(
                                com.fashionstore.order.repository.ReturnRequestRepository.class),
                        orderStatusHistoryRepository,
                        currentUserProvider,
                        outboxEventRecorder,
                        orderNotificationService,
                        java.time.Clock.systemUTC());
        var creation =
                new com.fashionstore.order.service.ShipmentCreationTransactionService(
                        orderRepository,
                        shipmentRepository,
                        orderStatusHistoryRepository,
                        currentUserProvider,
                        org.mockito.Mockito.mock(
                                com.fashionstore.order.repository.OrderSagaRepository.class));
        var properties = new com.fashionstore.order.config.GhnProperties();
        properties.setShopId("123");
        var transactionManager =
                org.mockito.Mockito.mock(
                        org.springframework.transaction.PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any()))
                .thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        when(ghnClient.getVerifiedShipment(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(
                        invocation -> {
                            String code = invocation.getArgument(0);
                            Shipment shipment =
                                    shipmentRepository
                                            .findByGhnOrderCode(code)
                                            .or(() -> shipmentRepository.findByTrackingCode(code))
                                            .orElseThrow();
                            boolean cod =
                                    shipment.getOrder().getPaymentMethod()
                                            == com.fashionstore.common.payment.PaymentMethod.COD;
                            return new com.fashionstore.order.dto.VerifiedGhnShipment(
                                    code,
                                    "123",
                                    shipment.getOrder().getOrderCode(),
                                    "delivered",
                                    cod ? shipment.getOrder().getTotalAmount() : BigDecimal.ZERO,
                                    cod,
                                    java.time.Instant.parse("2026-10-01T03:00:00Z"),
                                    "COD-1".equals(code),
                                    java.time.Instant.parse("2026-10-02T03:00:00Z"));
                        });
        service =
                new ShipmentServiceImpl(
                        shipmentRepository,
                        orderRepository,
                        ghnClient,
                        catalogClient,
                        currentUserProvider,
                        orderStatusHistoryRepository,
                        orderNotificationService,
                        outboxEventRecorder,
                        receipts,
                        cancellation,
                        creation,
                        properties,
                        transactionManager,
                        java.time.Clock.systemUTC());
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void onlineDeliveryDoesNotInventCodCollection() {
        Order order = confirmedOrder(null);
        order.setPaymentMethod(com.fashionstore.common.payment.PaymentMethod.ONLINE);
        order.setTotalAmount(new BigDecimal("100000"));
        order.setStatus(OrderStatus.SHIPPING);
        Shipment shipment =
                Shipment.builder()
                        .order(order)
                        .provider(ShipmentProvider.GHN)
                        .ghnOrderCode("ONLINE-1")
                        .status(ShipmentStatus.SHIPPING)
                        .build();
        when(shipmentRepository.findByGhnOrderCode("ONLINE-1")).thenReturn(Optional.of(shipment));

        service.handleGhnCallback(
                GhnWebhookPayload.builder()
                        .shopId("123")
                        .time("2026-10-01T03:00:00Z")
                        .orderCode("ONLINE-1")
                        .status("delivered")
                        .time("2026-10-01T03:00:00Z")
                        .codAmount(BigDecimal.ZERO)
                        .build());

        assertThat(shipment.getCodStatus()).isEqualTo("NOT_APPLICABLE");
    }

    @Test
    void codTransferUsesProviderDateRatherThanCurrentTime() {
        Order order = confirmedOrder(null);
        order.setPaymentMethod(com.fashionstore.common.payment.PaymentMethod.COD);
        order.setTotalAmount(new BigDecimal("100000"));
        Shipment shipment =
                Shipment.builder()
                        .order(order)
                        .provider(ShipmentProvider.GHN)
                        .ghnOrderCode("COD-1")
                        .status(ShipmentStatus.DELIVERED)
                        .codStatus("COLLECTED")
                        .build();
        when(shipmentRepository.findByGhnOrderCode("COD-1")).thenReturn(Optional.of(shipment));

        service.handleGhnCallback(
                GhnWebhookPayload.builder()
                        .shopId("123")
                        .time("2026-10-01T03:00:00Z")
                        .orderCode("COD-1")
                        .type("cod")
                        .codAmount(new BigDecimal("100000"))
                        .codTransferDate("2026-10-02T03:00:00Z")
                        .build());

        assertThat(shipment.getCodTransferredAt())
                .isEqualTo(java.time.Instant.parse("2026-10-02T03:00:00Z"));
    }

    @Test
    void createShipment_success_usesOrderAddressSnapshotOnly() {
        Order order =
                confirmedOrder(
                        ShippingAddress.builder()
                                .recipientName("Nguyen Van A")
                                .recipientPhone("0987654321")
                                .province("Ho Chi Minh")
                                .district("Quan 1")
                                .ward("Ben Nghe")
                                .detailAddress("123 Le Loi")
                                .districtId(1444)
                                .wardCode("20308")
                                .provinceId(1000001)
                                .wardId(1003646)
                                .build());

        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderId("order-1")).thenReturn(Optional.empty());
        when(catalogClient.getVariantsBatch(List.of("var-1")))
                .thenReturn(
                        List.of(
                                ProductVariantDto.builder()
                                        .variantId("var-1")
                                        .weightGram(500)
                                        .build()));
        when(ghnClient.createOrder(eq(order), any(), eq(500))).thenReturn("GHN_TRACK_123");

        ShipmentResponse response =
                service.createShipment("order-1", CreateShipmentRequest.builder().build());

        assertThat(response).isNotNull();
        assertThat(response.getOrderId()).isEqualTo("order-1");
        assertThat(response.getTrackingCode()).isEqualTo("GHN_TRACK_123");
        assertThat(response.getStatus()).isEqualTo(ShipmentStatus.PENDING);
        assertThat(order.getTrackingCode()).isEqualTo("GHN_TRACK_123");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PACKED);

        ArgumentCaptor<Shipment> shipmentCaptor = ArgumentCaptor.forClass(Shipment.class);
        verify(shipmentRepository, times(1)).save(shipmentCaptor.capture());
        assertThat(shipmentCaptor.getValue().getToDistrictId()).isEqualTo(1444);
        assertThat(shipmentCaptor.getValue().getToWardCode()).isEqualTo("20308");

        ArgumentCaptor<OrderStatusHistory> historyCaptor =
                ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository, times(1)).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(OrderStatus.PACKED);
        assertThat(historyCaptor.getValue().getAction()).isEqualTo("SHIPMENT_CREATED");
    }

    @Test
    void acceptedCancellationBlocksEvenExistingShipmentReplay() {
        var order = confirmedOrder(null);
        order.setStatus(OrderStatus.CANCELLING);
        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderId("order-1"))
                .thenReturn(
                        Optional.of(
                                Shipment.builder()
                                        .order(order)
                                        .provider(ShipmentProvider.GHN)
                                        .trackingCode("existing-label")
                                        .status(ShipmentStatus.PENDING)
                                        .build()));
        assertThatThrownBy(() -> service.createShipment("order-1", null))
                .isInstanceOf(AppException.class);
        verify(ghnClient, never()).createOrder(any(), any(), any(Integer.class));
    }

    @Test
    void carrierReadyToPickIsNotActualHandoff() {
        var order = confirmedOrder(null);
        var shipment =
                Shipment.builder()
                        .order(order)
                        .provider(ShipmentProvider.GHN)
                        .trackingCode("tracking")
                        .status(ShipmentStatus.PENDING)
                        .build();
        when(shipmentRepository.findByGhnOrderCode("tracking")).thenReturn(Optional.of(shipment));
        service.handleGhnCallback(
                GhnWebhookPayload.builder()
                        .shopId("123")
                        .time("2026-10-01T03:00:00Z")
                        .orderCode("tracking")
                        .status("ready_to_pick")
                        .build());
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.PENDING);
        assertThat(shipment.getHandedOffAt()).isNull();
    }

    @Test
    void lateCarrierHandoffAfterCancellationKeepsFactAndCreatesException() {
        var order = confirmedOrder(null);
        order.setStatus(OrderStatus.CANCELLED);
        var shipment =
                Shipment.builder()
                        .order(order)
                        .provider(ShipmentProvider.GHN)
                        .trackingCode("tracking")
                        .status(ShipmentStatus.CANCELLED)
                        .build();
        when(shipmentRepository.findByGhnOrderCode("tracking")).thenReturn(Optional.of(shipment));
        service.handleGhnCallback(
                GhnWebhookPayload.builder()
                        .shopId("123")
                        .time("2026-10-01T03:00:00Z")
                        .orderCode("tracking")
                        .status("picked")
                        .build());
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.PICKED);
        assertThat(shipment.getHandedOffAt()).isNotNull();
        verify(cancellation).lateHandoff(eq(order.getId()), any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void firstVerifiedHandoffTimestampIsImmutable() {
        var order = confirmedOrder(null);
        var shipment =
                Shipment.builder()
                        .order(order)
                        .provider(ShipmentProvider.GHN)
                        .trackingCode("tracking")
                        .status(ShipmentStatus.PENDING)
                        .build();
        when(shipmentRepository.findByGhnOrderCode("tracking")).thenReturn(Optional.of(shipment));
        service.handleGhnCallback(
                GhnWebhookPayload.builder()
                        .shopId("123")
                        .time("2026-10-01T03:00:00Z")
                        .orderCode("tracking")
                        .status("storing")
                        .build());
        var first = shipment.getHandedOffAt();
        assertThat(first).isNotNull();
        service.handleGhnCallback(
                GhnWebhookPayload.builder()
                        .shopId("123")
                        .time("2026-10-01T03:00:00Z")
                        .orderCode("tracking")
                        .status("storing")
                        .build());
        assertThat(shipment.getHandedOffAt()).isEqualTo(first);
    }

    @Test
    void carrierAndCatalogHttpDoNotHoldOrderTransaction() {
        var order =
                confirmedOrder(
                        ShippingAddress.builder()
                                .recipientName("Test")
                                .recipientPhone("0900000000")
                                .provinceId(1)
                                .wardId(2)
                                .districtId(3)
                                .wardCode("ward")
                                .build());
        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));
        when(catalogClient.getVariantsBatch(any()))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            org.springframework.transaction.support
                                                    .TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            return List.of();
                        });
        when(ghnClient.createOrder(eq(order), any(), any(Integer.class)))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            org.springframework.transaction.support
                                                    .TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            return "tracking";
                        });
        var proxy = new org.springframework.aop.framework.ProxyFactory(service);
        proxy.addAdvice(
                new org.springframework.transaction.interceptor.TransactionInterceptor(
                        new CheckoutServiceImplTest.TestTransactionManager(),
                        new org.springframework.transaction.annotation
                                .AnnotationTransactionAttributeSource()));
        ((com.fashionstore.order.service.ShipmentService) proxy.getProxy())
                .createShipment("order-1", null);
    }

    @Test
    void cancellationAcceptedDuringCarrierCreationCannotPackOrderOnLateReply() {
        var order =
                confirmedOrder(
                        ShippingAddress.builder()
                                .recipientName("Test")
                                .recipientPhone("0900000000")
                                .provinceId(1)
                                .wardId(2)
                                .districtId(3)
                                .wardCode("ward")
                                .build());
        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));
        when(ghnClient.createOrder(eq(order), any(), any(Integer.class)))
                .thenAnswer(
                        call -> {
                            order.setStatus(OrderStatus.CANCELLING);
                            return "late-label";
                        });
        var result = service.createShipment("order-1", null);
        assertThat(result.getTrackingCode()).isEqualTo("late-label");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLING);
    }

    @Test
    void unknownCarrierCreationRetainsClaimAndNeverCreatesAnotherLabelOnRetry() {
        var order =
                confirmedOrder(
                        ShippingAddress.builder()
                                .recipientName("Test")
                                .recipientPhone("0900000000")
                                .provinceId(1)
                                .wardId(2)
                                .districtId(3)
                                .wardCode("ward")
                                .build());
        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));
        when(ghnClient.createOrder(eq(order), any(), any(Integer.class)))
                .thenThrow(new IllegalStateException("timeout"));
        assertThatThrownBy(() -> service.createShipment("order-1", null))
                .isInstanceOf(IllegalStateException.class);
        String claim = order.getShipmentInitiationToken();
        assertThat(claim).isNotBlank();
        assertThatThrownBy(() -> service.createShipment("order-1", null))
                .isInstanceOf(AppException.class);
        assertThat(order.getShipmentInitiationToken()).isEqualTo(claim);
        verify(ghnClient, times(1)).createOrder(eq(order), any(), any(Integer.class));
    }

    @Test
    void createShipment_whenOrderHasNoAddressSnapshot_throwsShippingAddressInvalid() {
        Order order = confirmedOrder(null);
        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderId("order-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createShipment("order-1", null))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.SHIPPING_ADDRESS_INVALID);
        verify(ghnClient, never()).createOrder(any(), any(), any(Integer.class));
    }

    /** Đơn tạo trước khi có snapshot: còn tên/SĐT (cột cũ) nhưng không có mã GHN. */
    @Test
    void createShipment_whenLegacyOrderHasNoGhnCodes_throwsShippingAddressInvalid() {
        Order order =
                confirmedOrder(
                        ShippingAddress.builder()
                                .recipientName("Nguyen Van A")
                                .recipientPhone("0987654321")
                                .build());
        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderId("order-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createShipment("order-1", null))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.SHIPPING_ADDRESS_INVALID);
        verify(ghnClient, never()).createOrder(any(), any(), any(Integer.class));
    }

    private Order confirmedOrder(ShippingAddress address) {
        Order order =
                Order.builder()
                        .orderCode("ORD123")
                        .userId("user-1")
                        .status(OrderStatus.CONFIRMED)
                        .checkoutId("chk-1")
                        .shippingAddress("123 Le Loi, Ben Nghe, Quan 1, Ho Chi Minh")
                        .address(address)
                        .shippingFee(BigDecimal.valueOf(30000))
                        .items(
                                new ArrayList<>(
                                        List.of(
                                                OrderItem.builder()
                                                        .variantId("var-1")
                                                        .productName("Áo Khoác")
                                                        .quantity(1)
                                                        .unitPrice(BigDecimal.valueOf(300000))
                                                        .build())))
                        .build();
        order.setId("order-1");
        return order;
    }

    @Test
    void createShipment_whenAlreadyExists_returnsExisting() {
        Order order = Order.builder().userId("user-1").status(OrderStatus.CONFIRMED).build();
        order.setId("order-1");

        Shipment existing =
                Shipment.builder()
                        .order(order)
                        .provider(ShipmentProvider.GHN)
                        .trackingCode("EXISTING_CODE")
                        .status(ShipmentStatus.PENDING)
                        .build();
        existing.setId("ship-1");

        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderId("order-1")).thenReturn(Optional.of(existing));

        ShipmentResponse response = service.createShipment("order-1", null);

        assertThat(response.getId()).isEqualTo("ship-1");
        assertThat(response.getTrackingCode()).isEqualTo("EXISTING_CODE");
        verify(ghnClient, never()).createOrder(any(), any(), any(Integer.class));
    }

    @Test
    void createShipment_whenOrderPending_throwsInvalidStatus() {
        Order order = Order.builder().userId("user-1").status(OrderStatus.PENDING).build();
        order.setId("order-1");

        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderId("order-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createShipment("order-1", null))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.ORDER_STATUS_INVALID);
    }

    @Test
    void createShipment_whenOrderShippingOrDelivered_throwsInvalidStatus() {
        Order shippingOrder = Order.builder().userId("user-1").status(OrderStatus.SHIPPING).build();
        shippingOrder.setId("order-shipping");
        when(orderRepository.findByIdForUpdate("order-shipping"))
                .thenReturn(Optional.of(shippingOrder));

        assertThatThrownBy(() -> service.createShipment("order-shipping", null))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.ORDER_STATUS_INVALID);

        Order deliveredOrder =
                Order.builder().userId("user-1").status(OrderStatus.DELIVERED).build();
        deliveredOrder.setId("order-delivered");
        when(orderRepository.findByIdForUpdate("order-delivered"))
                .thenReturn(Optional.of(deliveredOrder));

        assertThatThrownBy(() -> service.createShipment("order-delivered", null))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.ORDER_STATUS_INVALID);
    }

    @Test
    void getShipmentByOrderId_success() {
        Order order = Order.builder().userId("user-1").build();
        order.setId("order-1");

        Shipment shipment =
                Shipment.builder()
                        .order(order)
                        .provider(ShipmentProvider.GHN)
                        .trackingCode("TRACK123")
                        .status(ShipmentStatus.SHIPPING)
                        .build();
        shipment.setId("ship-1");

        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderId("order-1")).thenReturn(Optional.of(shipment));

        ShipmentResponse response = service.getShipmentByOrderId("order-1");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("ship-1");
        assertThat(response.getTrackingCode()).isEqualTo("TRACK123");
    }

    @Test
    void getShipmentByOrderId_wrongUser_throwsNotFound() {
        Order order = Order.builder().userId("other-user").build();
        order.setId("order-1");

        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.getShipmentByOrderId("order-1"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void handleGhnCallback_delivering_updatesToShipping() {
        Order order = Order.builder().status(OrderStatus.PACKED).build();
        order.setId("order-1");

        Shipment shipment =
                Shipment.builder()
                        .order(order)
                        .provider(ShipmentProvider.GHN)
                        .ghnOrderCode("GHN_CODE_1")
                        .status(ShipmentStatus.PICKED)
                        .build();
        shipment.setId("ship-1");

        when(shipmentRepository.findByGhnOrderCode("GHN_CODE_1")).thenReturn(Optional.of(shipment));

        GhnWebhookPayload payload =
                GhnWebhookPayload.builder()
                        .shopId("123")
                        .time("2026-10-01T03:00:00Z")
                        .orderCode("GHN_CODE_1")
                        .status("delivering")
                        .weight(600)
                        .build();

        service.handleGhnCallback(payload);

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.SHIPPING);
        assertThat(shipment.getWeightGram()).isEqualTo(600);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        verify(shipmentRepository, times(1)).save(shipment);
        verify(orderRepository, times(1)).save(order);

        ArgumentCaptor<OrderStatusHistory> historyCaptor =
                ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository, times(1)).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(OrderStatus.PACKED);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(historyCaptor.getValue().getAction()).isEqualTo("GHN_WEBHOOK");
        assertThat(historyCaptor.getValue().getChangedBy()).isEqualTo("GHN");
        verify(orderNotificationService, times(1)).sendOrderShippedNotification(order, shipment);
    }

    @Test
    void handleGhnCallback_delivered_updatesToDelivered() {
        Order order = Order.builder().status(OrderStatus.SHIPPING).build();
        order.setId("order-1");

        Shipment shipment =
                Shipment.builder()
                        .order(order)
                        .provider(ShipmentProvider.GHN)
                        .ghnOrderCode("GHN_CODE_1")
                        .status(ShipmentStatus.SHIPPING)
                        .build();
        shipment.setId("ship-1");

        when(shipmentRepository.findByGhnOrderCode("GHN_CODE_1")).thenReturn(Optional.of(shipment));

        GhnWebhookPayload payload =
                GhnWebhookPayload.builder()
                        .shopId("123")
                        .time("2026-10-01T03:00:00Z")
                        .orderCode("GHN_CODE_1")
                        .status("delivered")
                        .time("2026-10-01T03:00:00Z")
                        .build();

        service.handleGhnCallback(payload);

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(shipmentRepository, times(1)).save(shipment);
        verify(orderRepository, times(1)).save(order);

        ArgumentCaptor<OrderStatusHistory> historyCaptor =
                ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository, times(1)).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(historyCaptor.getValue().getAction()).isEqualTo("DELIVERY_CONFIRMED");
        assertThat(order.getDeliveredAt())
                .isEqualTo(java.time.Instant.parse("2026-10-01T03:00:00Z"));
        assertThat(historyCaptor.getValue().getChangedBy()).isEqualTo("GHN");
        verify(orderNotificationService, times(1)).sendOrderDeliveredNotification(order);
        verify(outboxEventRecorder, times(1))
                .saveMessage(
                        eq("order-1"), eq(EventTypes.ORDER_DELIVERED), any(EventEnvelope.class));
    }

    @Test
    void handleGhnCallback_whenAlreadyDelivered_ignoresShippingWebhook() {
        Order order = Order.builder().status(OrderStatus.DELIVERED).build();
        order.setId("order-1");

        Shipment shipment =
                Shipment.builder()
                        .order(order)
                        .provider(ShipmentProvider.GHN)
                        .ghnOrderCode("GHN_CODE_1")
                        .status(ShipmentStatus.DELIVERED)
                        .build();
        shipment.setId("ship-1");

        when(shipmentRepository.findByGhnOrderCode("GHN_CODE_1")).thenReturn(Optional.of(shipment));

        GhnWebhookPayload payload =
                GhnWebhookPayload.builder()
                        .shopId("123")
                        .time("2026-10-01T03:00:00Z")
                        .orderCode("GHN_CODE_1")
                        .status("delivering")
                        .build();

        service.handleGhnCallback(payload);

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(shipmentRepository, never()).save(any(Shipment.class));
        verify(orderRepository, never()).save(any(Order.class));
    }
}
