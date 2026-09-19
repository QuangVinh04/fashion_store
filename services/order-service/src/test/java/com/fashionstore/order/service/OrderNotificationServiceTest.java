package com.fashionstore.order.service;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.notification.EmailNotificationRequested;
import com.fashionstore.order.client.IdentityClient;
import com.fashionstore.order.dto.InternalUserDto;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.Shipment;
import com.fashionstore.order.entity.enumeration.ShipmentProvider;
import com.fashionstore.order.outbox.OutboxService;
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
    OutboxService outboxService;

    OrderNotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new OrderNotificationService(identityClient, outboxService);
    }

    @Test
    void sendOrderConfirmedNotification_success_emitsOutboxEvent() {
        Order order = Order.builder()
                .orderCode("ORD-100")
                .userId("user-uuid-1")
                .recipientName("Nguyen Van A")
                .recipientPhone("0901234567")
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
        verify(outboxService).saveMessage(eq("order-uuid-1"), eq(EventTypes.NOTIFICATION_EMAIL_REQUESTED), captor.capture());

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
                .recipientName("Snapshot User")
                .shippingAddress("123 Duong Le Loi, TP HCM")
                .paymentMethod(PaymentMethod.COD)
                .totalAmount(BigDecimal.valueOf(200000))
                .build();
        order.setId("order-uuid-101");

        notificationService.sendOrderConfirmedNotification(order);

        ArgumentCaptor<EventEnvelope<EmailNotificationRequested>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxService).saveMessage(eq("order-uuid-101"), eq(EventTypes.NOTIFICATION_EMAIL_REQUESTED), captor.capture());

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

        verify(outboxService, never()).saveMessage(anyString(), anyString(), any());
    }

    @Test
    void sendOrderShippedNotification_success_emitsOutboxEvent() {
        Order order = Order.builder()
                .orderCode("ORD-200")
                .userId("user-uuid-2")
                .recipientName("Tran Thi B")
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
        verify(outboxService).saveMessage(eq("order-uuid-2"), eq(EventTypes.NOTIFICATION_EMAIL_REQUESTED), captor.capture());

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
                .recipientName("Le Van C")
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
        verify(outboxService).saveMessage(eq("order-uuid-3"), eq(EventTypes.NOTIFICATION_EMAIL_REQUESTED), captor.capture());

        EventEnvelope<EmailNotificationRequested> envelope = captor.getValue();
        EmailNotificationRequested payload = envelope.payload();
        assertThat(payload.recipient()).isEqualTo("user3@fashionstore.com");
        assertThat(payload.template()).isEqualTo("order-delivered");
        assertThat(payload.variables()).containsEntry("orderCode", "ORD-300");
    }
}
