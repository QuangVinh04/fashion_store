package com.fashionstore.identity.service;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.contracts.common.AuditEvent;
import com.fashionstore.identity.dto.audit.AuditLogResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

public interface AuditLogService {

    /**
     * Ghi một dòng audit, bỏ qua nếu {@code eventId} đã có. Không kiểm quyền: chỉ listener và code nội bộ gọi,
     * không được expose qua controller. Thiếu trường bắt buộc thì ném {@code AmqpRejectAndDontRequeueException}.
     */
    void record(String eventId, AuditEvent event, Instant occurredAt);

    /** Mọi bộ lọc đều tuỳ chọn; bỏ trống là không lọc theo trường đó. */
    PageResponse<List<AuditLogResponse>> search(String actorId, String entityType, String entityId, String action,
                                                Pageable pageable);
}
