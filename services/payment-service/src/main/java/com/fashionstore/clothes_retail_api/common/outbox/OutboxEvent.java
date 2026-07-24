package com.fashionstore.product.common.outbox;

import com.fashionstore.common.messaging.outbox.OutboxEventStatus;

import com.fashionstore.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@Entity
@Table(name = "outbox_event")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OutboxEvent extends AuditedEntity {

    @Column(name = "event_type", nullable = false, length = 300)
    String eventType;

    @Column(name = "routing_key", nullable = false, length = 120)
    String routingKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    OutboxEventStatus status = OutboxEventStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    Integer attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    LocalDateTime nextAttemptAt;

    @Column(name = "published_at")
    LocalDateTime publishedAt;

    @Column(name = "last_error", length = 1000)
    String lastError;
}
