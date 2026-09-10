package com.fashionstore.contracts;

import com.fashionstore.contracts.common.EventEnvelope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EventEnvelopeTest {

    @Test
    void createsVersionOneEnvelope() {
        EventEnvelope<String> envelope = EventEnvelope.v1(
                "sample.created",
                "aggregate-1",
                "correlation-1",
                "payload"
        );

        assertNotNull(envelope.eventId());
        assertNotNull(envelope.occurredAt());
        assertEquals(1, envelope.version());
        assertEquals("aggregate-1", envelope.aggregateId());
    }
}
