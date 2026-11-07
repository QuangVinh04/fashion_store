package com.fashionstore.identity.messaging;

import com.fashionstore.contracts.common.AuditEvent;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.identity.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuditEventListenerTest {

    @Mock
    AuditLogService auditLogService;

    AuditEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuditEventListener(auditLogService, new ObjectMapper());
    }

    @Test
    void recordsThePayloadWithTheEnvelopeIdAndTime() {
        AuditEvent event = new AuditEvent("order-service", "staff-1", "RETURN_REFUND", "RETURN_REQUEST", "ret-1",
                "Hoàn 200000 VND");
        EventEnvelope<AuditEvent> envelope = EventEnvelope.v1(EventTypes.AUDIT_RECORDED, "ord-1", "corr-1", event);

        listener.onAuditEvent(envelope);

        verify(auditLogService).record(envelope.eventId(), event, envelope.occurredAt());
    }

    @Test
    void unknownEventTypeIsDeadLettered() {
        EventEnvelope<AuditEvent> envelope = EventEnvelope.v1("audit.other", "x", "c",
                new AuditEvent("s", "a", "A", "E", "1", "x"));

        assertThatThrownBy(() -> listener.onAuditEvent(envelope))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(auditLogService);
    }
}
