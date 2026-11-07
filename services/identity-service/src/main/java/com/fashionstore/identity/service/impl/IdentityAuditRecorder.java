package com.fashionstore.identity.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.contracts.common.AuditEvent;
import com.fashionstore.identity.service.AuditLogService;
import com.fashionstore.identity.service.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

import java.time.Instant;
import java.util.UUID;

/**
 * Ghi audit log cho thao tác của nhân viên trong identity-service. Ghi thẳng vào bảng thay vì qua RabbitMQ:
 * dữ liệu nằm ở Keycloak nên không có transaction để outbox bảo đảm thêm điều gì.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityAuditRecorder {

    static final String GROUP_CREATE = "GROUP_CREATE";
    static final String GROUP_UPDATE = "GROUP_UPDATE";
    static final String GROUP_DELETE = "GROUP_DELETE";
    static final String GROUP_MEMBERS_ADD = "GROUP_MEMBERS_ADD";
    static final String GROUP_MEMBERS_REMOVE = "GROUP_MEMBERS_REMOVE";
    static final String USER_GROUPS_UPDATE = "USER_GROUPS_UPDATE";
    static final String STAFF_CREATE = "STAFF_CREATE";
    static final String USER_LOCK = "USER_LOCK";
    static final String USER_UNLOCK = "USER_UNLOCK";

    private static final String SOURCE_SERVICE = "identity-service";

    private final AuditLogService auditLogService;
    private final CurrentUserProvider currentUserProvider;

    public void record(String action, String entityType, String entityId, String summary) {
        try {
            AuditEvent event = new AuditEvent(SOURCE_SERVICE, currentUserProvider.getCurrentUserId(), action,
                    entityType, entityId, summary);
            auditLogService.record(UUID.randomUUID().toString(), event, Instant.now());
        } catch (DataAccessException | TransactionException | AppException
                 | AmqpRejectAndDontRequeueException failure) {
            // Thay đổi đã áp dụng ở Keycloak: trả lỗi lúc này làm người dùng tưởng chưa đổi, nên chỉ log để xử lý tay.
            log.error("Audit log lost: action={} entity={}/{}", action, entityType, entityId, failure);
        }
    }
}
