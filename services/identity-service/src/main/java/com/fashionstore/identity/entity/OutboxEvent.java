package com.fashionstore.identity.entity;

import com.fashionstore.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

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

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public OutboxEvent(String routingKey, String payload) {
        this.routingKey = routingKey;
        this.payload = payload;
    }
}
