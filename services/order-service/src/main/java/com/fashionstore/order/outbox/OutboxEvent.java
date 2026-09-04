package com.fashionstore.order.outbox;


import com.fashionstore.common.messaging.outbox.OutboxEventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một message chờ gửi. Entity bám sát đúng bảng outbox_event: exchange là hằng số của hệ thống nên không
 * lưu, và eventType dùng luôn làm routing key thay vì mang thêm một cột message_type song trùng.
 */
@Getter
@Setter
@Entity
@Table(name = "outbox_event")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OutboxEvent {

    @Id
    String id;

    @Column(name = "aggregate_id")
    String aggregateId;

    @Column(name = "event_type", nullable = false, length = 300)
    String eventType;

    @Column(name = "routing_key", nullable = false, length = 120)
    String routingKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    OutboxEventStatus status = OutboxEventStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    LocalDateTime nextAttemptAt;

    @Column(name = "published_at")
    LocalDateTime publishedAt;

    @Column(name = "last_error", length = 1000)
    String lastError;

    @Column(name = "created_at")
    LocalDateTime createdAt;

    @Column(name = "updated_at")
    LocalDateTime updatedAt;

    protected OutboxEvent() {
    }

    public static OutboxEvent pending(String aggregateId, String eventType, String payload) {
        LocalDateTime now = LocalDateTime.now();

        OutboxEvent event = new OutboxEvent();
        event.id = UUID.randomUUID().toString();
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.routingKey = eventType;
        event.payload = payload;
        event.status = OutboxEventStatus.PENDING;
        event.attempts = 0;
        event.nextAttemptAt = now;
        event.createdAt = now;
        event.updatedAt = now;
        return event;
    }

    public void markPublished() {
        status = OutboxEventStatus.PUBLISHED;
        publishedAt = LocalDateTime.now();
        updatedAt = publishedAt;
        lastError = null;
    }

    public void scheduleRetry(String error, int maxAttempts) {
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
        updatedAt = LocalDateTime.now();
        if (attempts >= maxAttempts) {
            status = OutboxEventStatus.FAILED;
            return;
        }
        nextAttemptAt = updatedAt.plusSeconds(Math.min(300, 1L << Math.min(attempts, 8)));
    }
}
