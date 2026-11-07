package com.fashionstore.identity.dto.audit;

import java.time.LocalDateTime;

public record AuditLogResponse(
        String id,
        String sourceService,
        String actorId,
        String action,
        String entityType,
        String entityId,
        String summary,
        LocalDateTime occurredAt
) {
}
