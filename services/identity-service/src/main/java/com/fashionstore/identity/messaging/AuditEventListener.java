package com.fashionstore.identity.messaging;

import com.fashionstore.contracts.common.AuditEvent;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.identity.config.messaging.RabbitMQConfig;
import com.fashionstore.identity.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Lưu event {@code audit.recorded} của mọi service. Bản gửi lại (cùng {@code eventId}) bị bỏ qua trong service. */
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.AUDIT_EVENTS_QUEUE)
    public void onAuditEvent(EventEnvelope<?> envelope) {
        if (!EventTypes.AUDIT_RECORDED.equals(envelope.eventType())) {
            // Lỗi vĩnh viễn: retry không giúp gì, chuyển thẳng sang DLQ
            throw new AmqpRejectAndDontRequeueException("Unsupported eventType on audit queue: " + envelope.eventType());
        }
        AuditEvent event = objectMapper.convertValue(envelope.payload(), AuditEvent.class);
        auditLogService.record(envelope.eventId(), event, envelope.occurredAt());
    }
}
