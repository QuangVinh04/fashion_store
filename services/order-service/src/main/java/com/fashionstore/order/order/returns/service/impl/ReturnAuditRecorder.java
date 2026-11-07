package com.fashionstore.order.order.returns.service.impl;

import com.fashionstore.contracts.common.AuditEvent;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.order.shared.outbox.OutboxEventRecorder;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Ghi audit log cho thao tác của nhân viên trên yêu cầu đổi trả, cùng transaction với thao tác đó. */
@Component
@RequiredArgsConstructor
public class ReturnAuditRecorder {

    private static final String SOURCE_SERVICE = "order-service";
    private static final String ENTITY_TYPE = "RETURN_REQUEST";
    private static final String ACTION_REFUND = "RETURN_REFUND";

    private final OutboxEventRecorder outboxEventRecorder;

    public void refundStarted(String adminId, String orderId, String returnId, BigDecimal amount) {
        String summary = "Hoàn " + amount.toPlainString() + " VND cho đơn " + orderId;
        AuditEvent event =
                new AuditEvent(SOURCE_SERVICE, adminId, ACTION_REFUND, ENTITY_TYPE, returnId, summary);
        outboxEventRecorder.saveMessage(
                orderId,
                EventTypes.AUDIT_RECORDED,
                EventEnvelope.v1(EventTypes.AUDIT_RECORDED, returnId, returnId, event));
    }
}
