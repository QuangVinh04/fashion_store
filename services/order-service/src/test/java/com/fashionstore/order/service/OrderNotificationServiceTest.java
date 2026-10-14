package com.fashionstore.order.service;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.notification.EmailNotificationRequested;
import com.fashionstore.order.client.IdentityClient;
import com.fashionstore.order.dto.InternalUserDto;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.ShippingAddress;
import com.fashionstore.order.entity.Shipment;
import com.fashionstore.order.entity.enumeration.ShipmentProvider;
import com.fashionstore.order.event.OutboxEventRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderNotificationServiceTest {

    @Mock
    IdentityClient identityClient;

    @Mock
    OutboxEventRecorder outboxEventRecorder;

    OrderNotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new OrderNotificationService(identityClient, outboxEventRecorder);
    }

    @Test
    void sendOrderConfirmedNotification_success_emitsOutboxEvent() {
        Order order = Order.builder()
                .orderCode("ORD-100")
                .userId("user-uuid-1")
                .address(ShippingAddress.builder().recipientName("Nguyen Van A").recipientPhone("0901234567").build())
                .shippingAddress("123 Duong Le Loi, TP HCM")
                .paymentMethod(PaymentMethod.COD)
                .totalAmount(BigDecimal.valueOf(500000))
                .build();
        order.setId("order-uuid-1");

        InternalUserDto userDto = new InternalUserDto(
                "user-uuid-1",
                "test@fashionstore.com",
                "Nguyen Van A",
                "0901234567"
        );

        when(identityClient.getUser("user-uuid-1")).thenReturn(userDto);

        notificationService.sendOrderConfirmedNotification(order);

        ArgumentCaptor<EventEnvelope<EmailNotificationRequested>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventRecorder).saveMessage(eq("order-uuid-1"), eq(EventTypes.NOTIFICATION_EMAIL_REQUESTED), captor.capture());

        EventEnvelope<EmailNotificationRequested> envelope = captor.getValue();
        assertThat(envelope.eventType()).isEqualTo(EventTypes.NOTIFICATION_EMAIL_REQUESTED);
        EmailNotificationRequested payload = envelope.payload();
        assertThat(payload.recipient()).isEqualTo("test@fashionstore.com");
        assertThat(payload.template()).isEqualTo("order-confirmed");
        assertThat(payload.variables()).containsEntry("orderCode", "ORD-100");
        assertThat(payload.variables()).containsEntry("recipientName", "Nguyen Van A");
    }

    @Test
    void sendOrderConfirmedNotification_usesOrderRecipientEmailDirectly_withoutCallingIdentityClient() {
        Order order = Order.builder()
                .orderCode("ORD-101")
                .userId("user-uuid-99")
                .recipientEmail("direct-snapshot@fashionstore.com")
                .address(ShippingAddress.builder().recipientName("Snapshot User").build())
                .shippingAddress("123 Duong Le Loi, TP HCM")
                .paymentMethod(PaymentMethod.COD)
                .totalAmount(BigDecimal.valueOf(200000))
                .build();
        order.setId("order-uuid-101");

        notificationService.sendOrderConfirmedNotification(order);

        ArgumentCaptor<EventEnvelope<EmailNotificationRequested>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventRecorder).saveMessage(eq("order-uuid-101"), eq(EventTypes.NOTIFICATION_EMAIL_REQUESTED), captor.capture());

        EventEnvelope<EmailNotificationRequested> envelope = captor.getValue();
        EmailNotificationRequested payload = envelope.payload();
        assertThat(payload.recipient()).isEqualTo("direct-snapshot@fashionstore.com");
        verify(identityClient, never()).getUser(anyString());
    }

    @Test
    void sendOrderConfirmedNotification_whenUserHasNoEmail_doesNotEmitEvent() {
        Order order = Order.builder()
                .orderCode("ORD-100")
                .userId("user-uuid-1")
                .build();
        order.setId("order-uuid-1");

        when(identityClient.getUser("user-uuid-1")).thenReturn(null);

        notificationService.sendOrderConfirmedNotification(order);

        verify(outboxEventRecorder, never()).saveMessage(anyString(), eq(EventTypes.NOTIFICATION_EMAIL_REQUESTED), any());
        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventRecorder).saveMessage(eq("order-uuid-1"), eq("notification.in-app.requested"), captor.capture());
        var payload = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(captor.getValue().payload());
        assertThat(payload.get("userId").asText()).isEqualTo("user-uuid-1");
        assertThat(payload.get("orderId").asText()).isEqualTo("order-uuid-1");
    }

    @Test
    void sendOrderShippedNotification_success_emitsOutboxEvent() {
        Order order = Order.builder()
                .orderCode("ORD-200")
                .userId("user-uuid-2")
                .address(ShippingAddress.builder().recipientName("Tran Thi B").build())
                .shippingAddress("456 Nguyen Trai, Ha Noi")
                .trackingCode("GHN-TRK-999")
                .build();
        order.setId("order-uuid-2");

        Shipment shipment = Shipment.builder()
                .provider(ShipmentProvider.GHN)
                .trackingCode("GHN-TRK-999")
                .build();

        InternalUserDto userDto = new InternalUserDto(
                "user-uuid-2",
                "user2@fashionstore.com",
                "Tran Thi B",
                "0901234568"
        );

        when(identityClient.getUser("user-uuid-2")).thenReturn(userDto);

        notificationService.sendOrderShippedNotification(order, shipment);

        ArgumentCaptor<EventEnvelope<EmailNotificationRequested>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventRecorder).saveMessage(eq("order-uuid-2"), eq(EventTypes.NOTIFICATION_EMAIL_REQUESTED), captor.capture());

        EventEnvelope<EmailNotificationRequested> envelope = captor.getValue();
        EmailNotificationRequested payload = envelope.payload();
        assertThat(payload.recipient()).isEqualTo("user2@fashionstore.com");
        assertThat(payload.template()).isEqualTo("order-shipped");
        assertThat(payload.variables()).containsEntry("trackingCode", "GHN-TRK-999");
        assertThat(payload.variables()).containsEntry("shippingProvider", "GHN");
    }

    @Test
    void sendOrderDeliveredNotification_success_emitsOutboxEvent() {
        Order order = Order.builder()
                .orderCode("ORD-300")
                .userId("user-uuid-3")
                .address(ShippingAddress.builder().recipientName("Le Van C").build())
                .shippingAddress("789 Vo Van Tan, TP HCM")
                .build();
        order.setId("order-uuid-3");

        InternalUserDto userDto = new InternalUserDto(
                "user-uuid-3",
                "user3@fashionstore.com",
                "Le Van C",
                "0901234569"
        );

        when(identityClient.getUser("user-uuid-3")).thenReturn(userDto);

        notificationService.sendOrderDeliveredNotification(order);

        ArgumentCaptor<EventEnvelope<EmailNotificationRequested>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventRecorder).saveMessage(eq("order-uuid-3"), eq(EventTypes.NOTIFICATION_EMAIL_REQUESTED), captor.capture());

        EventEnvelope<EmailNotificationRequested> envelope = captor.getValue();
        EmailNotificationRequested payload = envelope.payload();
        assertThat(payload.recipient()).isEqualTo("user3@fashionstore.com");
        assertThat(payload.template()).isEqualTo("order-delivered");
        assertThat(payload.variables()).containsEntry("orderCode", "ORD-300");
    }

    @Test
    void sendOrderCancelledNotification_emitsCancelledTemplateWithReason() {
        Order order = Order.builder()
                .orderCode("ORD-200")
                .userId("user-uuid-2")
                .recipientEmail("buyer@fashionstore.com")
                .address(ShippingAddress.builder().recipientName("Tran Thi B").recipientPhone("0907654321").build())
                .totalAmount(BigDecimal.valueOf(750000))
                .build();
        order.setId("order-uuid-2");

        notificationService.sendOrderCancelledNotification(order, "Thanh toán thất bại");

        ArgumentCaptor<EventEnvelope<EmailNotificationRequested>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventRecorder).saveMessage(eq("order-uuid-2"), eq(EventTypes.NOTIFICATION_EMAIL_REQUESTED), captor.capture());
        EmailNotificationRequested payload = captor.getValue().payload();
        assertThat(payload.recipient()).isEqualTo("buyer@fashionstore.com");
        assertThat(payload.template()).isEqualTo("order-cancelled");
        assertThat(payload.variables())
                .containsEntry("orderCode", "ORD-200")
                .containsEntry("recipientName", "Tran Thi B")
                .containsEntry("totalAmount", "750000")
                .containsEntry("reason", "Thanh toán thất bại");
    }

    @Test
    void allOrderMilestonesEmitOwnedInAppNotifications() {
        Order order = Order.builder().userId("buyer-1").orderCode("ORD-42")
                .recipientEmail("buyer@example.com").build();
        order.setId("order-42");
        notificationService.sendOrderConfirmedNotification(order);
        notificationService.sendOrderShippedNotification(order, null);
        notificationService.sendOrderDeliveredNotification(order);
        notificationService.sendOrderCancelledNotification(order, "Khách yêu cầu");
        notificationService.sendReturnStatusNotification(order, "Đã hoàn tiền", "Tiền đã vào ví.");

        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventRecorder, org.mockito.Mockito.times(5))
                .saveMessage(eq("order-42"), eq("notification.in-app.requested"), captor.capture());
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var payloads = captor.getAllValues().stream().map(event -> mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(event.payload())).toList();
        assertThat(payloads).allSatisfy(payload -> {
            assertThat(payload.get("userId").asText()).isEqualTo("buyer-1");
            assertThat(payload.get("orderId").asText()).isEqualTo("order-42");
            assertThat(payload.get("title").asText()).isNotBlank();
            assertThat(payload.get("message").asText()).isNotBlank();
        });
        assertThat(payloads.stream().map(payload -> payload.get("type").asText()))
                .containsExactly("ORDER_CONFIRMED", "ORDER_SHIPPED", "ORDER_DELIVERED", "ORDER_CANCELLED", "RETURN_STATUS");
    }

    @Test
    void anonymousCheckoutDoesNotEmitInAppNotification() {
        Order order = Order.builder().userId("anon:checkout-1").orderCode("ORD-43")
                .recipientEmail("guest@example.com").build();
        order.setId("order-43");
        notificationService.sendOrderConfirmedNotification(order);
        verify(outboxEventRecorder, never()).saveMessage(anyString(), eq("notification.in-app.requested"), any());
        verify(outboxEventRecorder).saveMessage(eq("order-43"), eq(EventTypes.NOTIFICATION_EMAIL_REQUESTED), any());
    }
}
