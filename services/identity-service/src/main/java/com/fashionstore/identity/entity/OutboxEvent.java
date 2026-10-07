package com.fashionstore.identity.entity;

import com.fashionstore.common.messaging.outbox.OutboxEventStatus;
import com.fashionstore.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Một message chờ gửi. Ghi cùng transaction với thay đổi nghiệp vụ, relay gửi đi sau khi commit.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "outbox_event")
public class OutboxEvent extends AuditedEntity {

    @Column(name = "routing_key", nullable = false, length = 120)
    private String routingKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status = OutboxEventStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt = LocalDateTime.now();

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public OutboxEvent(String routingKey, String payload) {
        this.routingKey = routingKey;
        this.payload = payload;
    }

    public void markPublished() {
        status = OutboxEventStatus.PUBLISHED;
        publishedAt = LocalDateTime.now();
        lastError = null;
    }

    /** Lùi lịch gửi lại theo backoff mũ (2s, 4s, ... trần 5 phút); quá {@code maxAttempts} thì FAILED. */
    public void scheduleRetry(String error, int maxAttempts) {
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
        if (attempts >= maxAttempts) {
            status = OutboxEventStatus.FAILED;
            return;
        }
        nextAttemptAt = LocalDateTime.now().plusSeconds(Math.min(300, 1L << Math.min(attempts, 9)));
    }
}
