package com.fashionstore.order.outbox;

/**
 * Chỉ mang id: listener chạy sau commit và ở thread khác, nên phải đọc lại entity trong transaction
 * của chính nó thay vì dùng lại instance đã detach.
 */
public record OutboxCreatedEvent(String outboxEventId) {
}
