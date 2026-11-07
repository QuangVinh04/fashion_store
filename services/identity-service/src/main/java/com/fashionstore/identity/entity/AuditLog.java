package com.fashionstore.identity.entity;

import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Một thao tác nhạy cảm của nhân viên, nhận từ event {@code audit.recorded}. Chỉ ghi thêm, không sửa. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "audit_log")
public class AuditLog extends BaseEntity {

    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;

    @Column(name = "source_service", nullable = false, updatable = false, length = 60)
    private String sourceService;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 64)
    private String actorId;

    @Column(name = "action", nullable = false, updatable = false, length = 80)
    private String action;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false, length = 100)
    private String entityId;

    @Column(name = "summary", updatable = false, length = 500)
    private String summary;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;
}
