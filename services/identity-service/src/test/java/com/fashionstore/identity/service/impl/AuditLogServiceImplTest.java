package com.fashionstore.identity.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.contracts.common.AuditEvent;
import com.fashionstore.identity.dto.audit.AuditLogResponse;
import com.fashionstore.identity.entity.AuditLog;
import com.fashionstore.identity.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceImplTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-11-07T02:00:00Z");

    @Mock
    AuditLogRepository auditLogRepository;

    @InjectMocks
    AuditLogServiceImpl service;

    @Test
    void searchMapsRowsAndPaging() {
        AuditLog row = new AuditLog();
        row.setEventId("e-1");
        row.setSourceService("catalog-service");
        row.setActorId("staff-1");
        row.setAction("PRODUCT_PUBLISH");
        row.setEntityType("PRODUCT");
        row.setEntityId("p-1");
        row.setSummary("Đăng bán sản phẩm");
        row.setOccurredAt(LocalDateTime.of(2026, 11, 7, 9, 0));
        Pageable pageable = PageRequest.of(0, 20);
        when(auditLogRepository.search(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

        PageResponse<List<AuditLogResponse>> result = service.search("staff-1", null, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.actorId()).isEqualTo("staff-1");
            assertThat(item.action()).isEqualTo("PRODUCT_PUBLISH");
            assertThat(item.entityId()).isEqualTo("p-1");
            assertThat(item.summary()).isEqualTo("Đăng bán sản phẩm");
        });
    }

    @Test
    void recordStoresRow() {
        AuditEvent event = new AuditEvent("order-service", "staff-1", "RETURN_REFUND", "RETURN_REQUEST", "ret-1",
                "Hoàn 200000 VND");

        service.record("e-1", event, OCCURRED_AT);

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(saved.capture());
        AuditLog row = saved.getValue();
        assertThat(row.getEventId()).isEqualTo("e-1");
        assertThat(row.getSourceService()).isEqualTo("order-service");
        assertThat(row.getActorId()).isEqualTo("staff-1");
        assertThat(row.getAction()).isEqualTo("RETURN_REFUND");
        assertThat(row.getEntityType()).isEqualTo("RETURN_REQUEST");
        assertThat(row.getEntityId()).isEqualTo("ret-1");
        assertThat(row.getSummary()).isEqualTo("Hoàn 200000 VND");
        assertThat(row.getOccurredAt()).isNotNull();
    }

    @Test
    void recordSkipsExistingEventId() {
        when(auditLogRepository.existsByEventId("e-1")).thenReturn(true);

        service.record("e-1", new AuditEvent("s", "staff-1", "A", "E", "1", "x"), OCCURRED_AT);

        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void recordRejectsMissingActor() {
        AuditEvent event = new AuditEvent("order-service", null, "RETURN_REFUND", "RETURN_REQUEST", "ret-1", "x");

        assertThatThrownBy(() -> service.record("e-1", event, OCCURRED_AT))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void recordTruncatesLongSummary() {
        AuditEvent event = new AuditEvent("s", "staff-1", "A", "E", "1", "x".repeat(600));

        service.record("e-1", event, OCCURRED_AT);

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(saved.capture());
        assertThat(saved.getValue().getSummary()).hasSize(500);
    }
}
