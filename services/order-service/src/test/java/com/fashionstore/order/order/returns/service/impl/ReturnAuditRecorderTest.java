package com.fashionstore.order.order.returns.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fashionstore.contracts.common.AuditEvent;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.order.shared.outbox.OutboxEventRecorder;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

class ReturnAuditRecorderTest {
    final OutboxEventRecorder outbox = mock(OutboxEventRecorder.class);
    final ReturnAuditRecorder recorder = new ReturnAuditRecorder(outbox);

    @Test
    void refundStartedSavesAnAuditEnvelopeWithoutPersonalData() {
        recorder.refundStarted("admin-1", "order-1", "return-1", new BigDecimal("150000"));

        var envelope = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).saveMessage(eq("order-1"), eq(EventTypes.AUDIT_RECORDED), envelope.capture());
        assertThat(envelope.getValue().eventType()).isEqualTo(EventTypes.AUDIT_RECORDED);
        var event = (AuditEvent) envelope.getValue().payload();
        assertThat(event.sourceService()).isEqualTo("order-service");
        assertThat(event.actorId()).isEqualTo("admin-1");
        assertThat(event.action()).isEqualTo("RETURN_REFUND");
        assertThat(event.entityType()).isEqualTo("RETURN_REQUEST");
        assertThat(event.entityId()).isEqualTo("return-1");
        assertThat(event.summary()).isEqualTo("Hoàn 150000 VND cho đơn order-1");
    }
}
