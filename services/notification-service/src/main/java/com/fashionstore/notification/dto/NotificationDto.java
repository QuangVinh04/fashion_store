package com.fashionstore.notification.dto;

import java.time.Instant;

public record NotificationDto(String id, String type, String title, String message,
                              String orderId, Instant createdAt, Instant readAt) {
}
