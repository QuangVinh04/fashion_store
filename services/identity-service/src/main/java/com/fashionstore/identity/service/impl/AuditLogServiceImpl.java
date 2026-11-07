package com.fashionstore.identity.service.impl;

import static com.fashionstore.identity.service.impl.PreAuthorizeExpressions.GROUP_MANAGE;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.contracts.common.AuditEvent;
import com.fashionstore.identity.dto.audit.AuditLogResponse;
import com.fashionstore.identity.entity.AuditLog;
import com.fashionstore.identity.repository.AuditLogRepository;
import com.fashionstore.identity.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private static final int SUMMARY_MAX_LENGTH = 500;

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public void record(String eventId, AuditEvent event, Instant occurredAt) {
        if (auditLogRepository.existsByEventId(eventId)) {
            return;
        }
        requireFields(event);
        AuditLog auditLog = new AuditLog();
        auditLog.setEventId(eventId);
        auditLog.setSourceService(event.sourceService());
        auditLog.setActorId(event.actorId());
        auditLog.setAction(event.action());
        auditLog.setEntityType(event.entityType());
        auditLog.setEntityId(event.entityId());
        auditLog.setSummary(truncate(event.summary()));
        auditLog.setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneId.systemDefault()));
        auditLogRepository.save(auditLog);
    }

    // Lỗi vĩnh viễn: retry không giúp gì, chuyển thẳng sang DLQ thay vì chờ ConstraintViolation.
    private static void requireFields(AuditEvent event) {
        boolean isComplete = event != null
                && isPresent(event.sourceService())
                && isPresent(event.actorId())
                && isPresent(event.action())
                && isPresent(event.entityType())
                && isPresent(event.entityId());
        if (!isComplete) {
            throw new AmqpRejectAndDontRequeueException("Audit event is missing a required field");
        }
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static String truncate(String summary) {
        if (summary == null || summary.length() <= SUMMARY_MAX_LENGTH) {
            return summary;
        }
        return summary.substring(0, SUMMARY_MAX_LENGTH);
    }

    @Override
    @PreAuthorize(GROUP_MANAGE)
    @Transactional(readOnly = true)
    public PageResponse<List<AuditLogResponse>> search(String actorId, String entityType, String entityId,
                                                       String action, Pageable pageable) {
        Page<AuditLog> logs = auditLogRepository.search(actorId, entityType, entityId, action, pageable);
        return PageResponse.<List<AuditLogResponse>>builder()
                .pageNo(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalPage(logs.getTotalPages())
                .totalElements(logs.getTotalElements())
                .items(logs.getContent().stream().map(AuditLogServiceImpl::toResponse).toList())
                .build();
    }

    private static AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getSourceService(), log.getActorId(), log.getAction(),
                log.getEntityType(), log.getEntityId(), log.getSummary(), log.getOccurredAt());
    }
}
