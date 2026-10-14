package com.fashionstore.contracts.notification;

public record InAppNotificationRequested(
        String userId, String type, String title, String message, String orderId
) {
}
