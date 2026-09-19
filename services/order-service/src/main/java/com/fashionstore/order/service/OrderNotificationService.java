package com.fashionstore.order.service;

import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.notification.EmailNotificationRequested;
import com.fashionstore.order.client.IdentityClient;
import com.fashionstore.order.dto.InternalUserDto;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.Shipment;
import com.fashionstore.order.outbox.OutboxService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderNotificationService {

    IdentityClient identityClient;
    OutboxService outboxService;

    public void sendOrderConfirmedNotification(Order order) {
        if (order == null) return;
        String recipientEmail = resolveRecipientEmail(order);
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("[OrderNotification] Cannot send order-confirmed email: No email found for userId={}", order.getUserId());
            return;
        }

        Map<String, String> variables = new HashMap<>();
        variables.put("orderCode", order.getOrderCode());
        variables.put("recipientName", order.getRecipientName() != null ? order.getRecipientName() : "Quý khách");
        variables.put("totalAmount", order.getTotalAmount() != null ? order.getTotalAmount().toPlainString() : "0");
        variables.put("paymentMethod", order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "COD");
        variables.put("shippingAddress", order.getShippingAddress() != null ? order.getShippingAddress() : "");

        EmailNotificationRequested payload = new EmailNotificationRequested(
                recipientEmail,
                "order-confirmed",
                variables
        );

        EventEnvelope<EmailNotificationRequested> envelope = EventEnvelope.v1(
                EventTypes.NOTIFICATION_EMAIL_REQUESTED,
                order.getId(),
                null,
                payload
        );

        outboxService.saveMessage(order.getId(), EventTypes.NOTIFICATION_EMAIL_REQUESTED, envelope);
        log.info("[OrderNotification] Emitted order-confirmed email event for orderId={}", order.getId());
    }

    public void sendOrderShippedNotification(Order order, Shipment shipment) {
        if (order == null) return;
        String recipientEmail = resolveRecipientEmail(order);
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("[OrderNotification] Cannot send order-shipped email: No email found for userId={}", order.getUserId());
            return;
        }

        Map<String, String> variables = new HashMap<>();
        variables.put("orderCode", order.getOrderCode());
        variables.put("recipientName", order.getRecipientName() != null ? order.getRecipientName() : "Quý khách");
        variables.put("shippingProvider", shipment != null && shipment.getProvider() != null ? shipment.getProvider().name() : "GHN");
        variables.put("trackingCode", shipment != null && shipment.getTrackingCode() != null ? shipment.getTrackingCode() : (order.getTrackingCode() != null ? order.getTrackingCode() : "N/A"));
        variables.put("shippingAddress", order.getShippingAddress() != null ? order.getShippingAddress() : "");

        EmailNotificationRequested payload = new EmailNotificationRequested(
                recipientEmail,
                "order-shipped",
                variables
        );

        EventEnvelope<EmailNotificationRequested> envelope = EventEnvelope.v1(
                EventTypes.NOTIFICATION_EMAIL_REQUESTED,
                order.getId(),
                null,
                payload
        );

        outboxService.saveMessage(order.getId(), EventTypes.NOTIFICATION_EMAIL_REQUESTED, envelope);
        log.info("[OrderNotification] Emitted order-shipped email event for orderId={}", order.getId());
    }

    public void sendOrderDeliveredNotification(Order order) {
        if (order == null) return;
        String recipientEmail = resolveRecipientEmail(order);
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("[OrderNotification] Cannot send order-delivered email: No email found for userId={}", order.getUserId());
            return;
        }

        Map<String, String> variables = new HashMap<>();
        variables.put("orderCode", order.getOrderCode());
        variables.put("recipientName", order.getRecipientName() != null ? order.getRecipientName() : "Quý khách");
        variables.put("shippingAddress", order.getShippingAddress() != null ? order.getShippingAddress() : "");

        EmailNotificationRequested payload = new EmailNotificationRequested(
                recipientEmail,
                "order-delivered",
                variables
        );

        EventEnvelope<EmailNotificationRequested> envelope = EventEnvelope.v1(
                EventTypes.NOTIFICATION_EMAIL_REQUESTED,
                order.getId(),
                null,
                payload
        );

        outboxService.saveMessage(order.getId(), EventTypes.NOTIFICATION_EMAIL_REQUESTED, envelope);
        log.info("[OrderNotification] Emitted order-delivered email event for orderId={}", order.getId());
    }

    private String resolveRecipientEmail(Order order) {
        if (order == null) return null;
        if (order.getRecipientEmail() != null && !order.getRecipientEmail().isBlank()) {
            return order.getRecipientEmail().trim();
        }
        String userId = order.getUserId();
        if (userId == null || userId.isBlank() || userId.startsWith("anon:")) {
            return null;
        }
        try {
            InternalUserDto user = identityClient.getUser(userId);
            return user != null ? user.email() : null;
        } catch (Exception e) {
            log.warn("[OrderNotification] Failed to resolve email for userId={}: {}", userId, e.getMessage());
            return null;
        }
    }
}
