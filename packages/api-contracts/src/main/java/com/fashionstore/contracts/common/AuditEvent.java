package com.fashionstore.contracts.common;

/**
 * Payload của {@code audit.recorded}: một thao tác nhạy cảm của nhân viên.
 * {@code actorId} là {@code sub} của Keycloak. {@code summary} là một câu mô tả, không chứa dữ liệu cá nhân.
 */
public record AuditEvent(
        String sourceService,
        String actorId,
        String action,
        String entityType,
        String entityId,
        String summary
) {
}
