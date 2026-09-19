package com.fashionstore.order.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.client.CatalogClient;
import com.fashionstore.order.client.GhnClient;
import com.fashionstore.order.client.IdentityClient;
import com.fashionstore.order.dto.CreateShipmentRequest;
import com.fashionstore.order.dto.ProductVariantDto;
import com.fashionstore.order.dto.ShipmentResponse;
import com.fashionstore.order.dto.UserAddressDto;
import com.fashionstore.order.dto.ghn.GhnWebhookPayload;
import com.fashionstore.order.entity.Checkout;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderItem;
import com.fashionstore.order.entity.OrderStatusHistory;
import com.fashionstore.order.entity.Shipment;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.entity.enumeration.ShipmentProvider;
import com.fashionstore.order.entity.enumeration.ShipmentStatus;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.repository.CheckoutRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentServiceImplTest {

    @Mock
    ShipmentRepository shipmentRepository;

    @Mock
    OrderRepository orderRepository;

    @Mock
    CheckoutRepository checkoutRepository;

    @Mock
    IdentityClient identityClient;

    @Mock
    GhnClient ghnClient;

    @Mock
    CatalogClient catalogClient;

    @Mock
    CurrentUserProvider currentUserProvider;

    @Mock
    OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    com.fashionstore.order.service.OrderNotificationService orderNotificationService;

    ShipmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShipmentServiceImpl(shipmentRepository, orderRepository, checkoutRepository, identityClient, ghnClient, catalogClient, currentUserProvider, orderStatusHistoryRepository, orderNotificationService);
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void createShipment_success() {
        Order order = Order.builder()
                .orderCode("ORD123")
                .userId("user-1")
                .status(OrderStatus.CONFIRMED)
                .checkoutId("chk-1")
                .recipientName("Nguyen Van A")
                .recipientPhone("0987654321")
                .shippingAddress("123 Le Loi, Q1, HCM")
                .shippingFee(BigDecimal.valueOf(30000))
                .items(new ArrayList<>(List.of(OrderItem.builder()
                        .variantId("var-1")
                        .productName("Áo Khoác")
                        .quantity(1)
                        .unitPrice(BigDecimal.valueOf(300000))
                        .build())))
                .build();
        order.setId("order-1");

        Checkout checkout = Checkout.builder()
                .addressId("addr-1")
                .build();
        checkout.setId("chk-1");

        UserAddressDto addressDto = UserAddressDto.builder()
                .districtId(1444)
                .wardCode("20308")
                .recipientName("Nguyen Van A")
                .phone("0987654321")
                .fullAddress("123 Le Loi, Q1, HCM")
                .build();

        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderId("order-1")).thenReturn(Optional.empty());
        when(checkoutRepository.findById("chk-1")).thenReturn(Optional.of(checkout));
        when(identityClient.getAddress("addr-1")).thenReturn(addressDto);
        when(catalogClient.getVariantsBatch(List.of("var-1")))
                .thenReturn(List.of(ProductVariantDto.builder().variantId("var-1").weightGram(500).build()));
        when(ghnClient.createOrder(eq(order), any(), eq(500))).thenReturn("GHN_TRACK_123");

        ShipmentResponse response = service.createShipment("order-1", CreateShipmentRequest.builder().build());

        assertThat(response).isNotNull();
        assertThat(response.getOrderId()).isEqualTo("order-1");
        assertThat(response.getTrackingCode()).isEqualTo("GHN_TRACK_123");
        assertThat(response.getStatus()).isEqualTo(ShipmentStatus.PENDING);
        assertThat(order.getTrackingCode()).isEqualTo("GHN_TRACK_123");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PACKED);
        verify(shipmentRepository, times(1)).save(any(Shipment.class));

        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository, times(1)).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(OrderStatus.PACKED);
        assertThat(historyCaptor.getValue().getAction()).isEqualTo("SHIPMENT_CREATED");
    }

    @Test
    void createShipment_whenAlreadyExists_returnsExisting() {
        Order order = Order.builder()
                .userId("user-1")
                .status(OrderStatus.CONFIRMED)
                .build();
        order.setId("order-1");

        Shipment existing = Shipment.builder()
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
        Order order = Order.builder()
                .userId("user-1")
                .status(OrderStatus.PENDING)
                .build();
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
        when(orderRepository.findByIdForUpdate("order-shipping")).thenReturn(Optional.of(shippingOrder));

        assertThatThrownBy(() -> service.createShipment("order-shipping", null))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.ORDER_STATUS_INVALID);

        Order deliveredOrder = Order.builder().userId("user-1").status(OrderStatus.DELIVERED).build();
        deliveredOrder.setId("order-delivered");
        when(orderRepository.findByIdForUpdate("order-delivered")).thenReturn(Optional.of(deliveredOrder));

        assertThatThrownBy(() -> service.createShipment("order-delivered", null))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.ORDER_STATUS_INVALID);
    }


    @Test
    void getShipmentByOrderId_success() {
        Order order = Order.builder()
                .userId("user-1")
                .build();
        order.setId("order-1");

        Shipment shipment = Shipment.builder()
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
        Order order = Order.builder()
                .userId("other-user")
                .build();
        order.setId("order-1");

        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.getShipmentByOrderId("order-1"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void handleGhnCallback_delivering_updatesToShipping() {
        Order order = Order.builder()
                .status(OrderStatus.PACKED)
                .build();
        order.setId("order-1");

        Shipment shipment = Shipment.builder()
                .order(order)
                .provider(ShipmentProvider.GHN)
                .ghnOrderCode("GHN_CODE_1")
                .status(ShipmentStatus.PICKED)
                .build();
        shipment.setId("ship-1");

        when(shipmentRepository.findByGhnOrderCode("GHN_CODE_1")).thenReturn(Optional.of(shipment));

        GhnWebhookPayload payload = GhnWebhookPayload.builder()
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

        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository, times(1)).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(OrderStatus.PACKED);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(historyCaptor.getValue().getAction()).isEqualTo("GHN_WEBHOOK");
        assertThat(historyCaptor.getValue().getChangedBy()).isEqualTo("GHN");
        verify(orderNotificationService, times(1)).sendOrderShippedNotification(order, shipment);
    }

    @Test
    void handleGhnCallback_delivered_updatesToDelivered() {
        Order order = Order.builder()
                .status(OrderStatus.SHIPPING)
                .build();
        order.setId("order-1");

        Shipment shipment = Shipment.builder()
                .order(order)
                .provider(ShipmentProvider.GHN)
                .ghnOrderCode("GHN_CODE_1")
                .status(ShipmentStatus.SHIPPING)
                .build();
        shipment.setId("ship-1");

        when(shipmentRepository.findByGhnOrderCode("GHN_CODE_1")).thenReturn(Optional.of(shipment));

        GhnWebhookPayload payload = GhnWebhookPayload.builder()
                .orderCode("GHN_CODE_1")
                .status("delivered")
                .build();

        service.handleGhnCallback(payload);

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(shipmentRepository, times(1)).save(shipment);
        verify(orderRepository, times(1)).save(order);

        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository, times(1)).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(historyCaptor.getValue().getAction()).isEqualTo("GHN_WEBHOOK");
        assertThat(historyCaptor.getValue().getChangedBy()).isEqualTo("GHN");
        verify(orderNotificationService, times(1)).sendOrderDeliveredNotification(order);
    }

    @Test
    void handleGhnCallback_whenAlreadyDelivered_ignoresShippingWebhook() {
        Order order = Order.builder()
                .status(OrderStatus.DELIVERED)
                .build();
        order.setId("order-1");

        Shipment shipment = Shipment.builder()
                .order(order)
                .provider(ShipmentProvider.GHN)
                .ghnOrderCode("GHN_CODE_1")
                .status(ShipmentStatus.DELIVERED)
                .build();
        shipment.setId("ship-1");

        when(shipmentRepository.findByGhnOrderCode("GHN_CODE_1")).thenReturn(Optional.of(shipment));

        GhnWebhookPayload payload = GhnWebhookPayload.builder()
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

