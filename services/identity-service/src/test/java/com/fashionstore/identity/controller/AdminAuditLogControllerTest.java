package com.fashionstore.identity.controller;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.GlobalExceptionHandler;
import com.fashionstore.identity.dto.audit.AuditLogResponse;
import com.fashionstore.identity.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogControllerTest {

    MockMvc mockMvc;

    @Mock
    AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminAuditLogController(auditLogService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void searchPassesFiltersAndReturnsItems() throws Exception {
        AuditLogResponse item = new AuditLogResponse("a-1", "order-service", "staff-1", "RETURN_REFUND",
                "RETURN_REQUEST", "ret-1", "Hoàn tiền", LocalDateTime.of(2026, 11, 7, 9, 0));
        when(auditLogService.search(eq("staff-1"), eq("RETURN_REQUEST"), eq("ret-1"), eq(null), any(Pageable.class)))
                .thenReturn(PageResponse.<List<AuditLogResponse>>builder().items(List.of(item)).build());

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("actorId", "staff-1").param("entityType", "RETURN_REQUEST").param("entityId", "ret-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].action").value("RETURN_REFUND"))
                .andExpect(jsonPath("$.data.items[0].actorId").value("staff-1"));
    }
}
