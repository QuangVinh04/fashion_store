package com.fashionstore.identity.service.impl;

import com.fashionstore.common.security.PermissionNames;
import com.fashionstore.identity.repository.AuditLogRepository;
import com.fashionstore.identity.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Quyền group:manage nằm ở @PreAuthorize của service, kiểm bằng method security thật. */
@SpringJUnitConfig(AuditLogServiceSecurityTest.Config.class)
class AuditLogServiceSecurityTest {

    @Configuration
    @EnableMethodSecurity
    @Import(AuditLogServiceImpl.class)
    static class Config {
    }

    @MockitoBean
    AuditLogRepository auditLogRepository;

    @Autowired
    AuditLogService auditLogService;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customerReadCannotSearchAuditLogs() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("kc-staff", null, PermissionNames.CUSTOMER_READ));

        assertThatThrownBy(() -> auditLogService.search(null, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void groupManageCanSearchAuditLogs() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("kc-admin", null, PermissionNames.GROUP_MANAGE));
        when(auditLogRepository.search(any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        assertThat(auditLogService.search(null, null, null, null, PageRequest.of(0, 20)).getTotalElements())
                .isZero();
    }
}
