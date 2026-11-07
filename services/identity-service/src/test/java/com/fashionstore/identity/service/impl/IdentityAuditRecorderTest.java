package com.fashionstore.identity.service.impl;

import com.fashionstore.contracts.common.AuditEvent;
import com.fashionstore.identity.service.AuditLogService;
import com.fashionstore.identity.service.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityAuditRecorderTest {

    @Mock
    AuditLogService auditLogService;

    @Mock
    CurrentUserProvider currentUserProvider;

    IdentityAuditRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new IdentityAuditRecorder(auditLogService, currentUserProvider);
    }

    @Test
    void recordsWithTheCurrentStaffAsActor() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("staff-1");

        recorder.record(IdentityAuditRecorder.USER_LOCK, "USER", "kc-1", "Khoá tài khoản");

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogService).record(anyString(), event.capture(), any(Instant.class));
        assertThat(event.getValue()).isEqualTo(
                new AuditEvent("identity-service", "staff-1", "USER_LOCK", "USER", "kc-1", "Khoá tài khoản"));
    }

    @Test
    void failureResolvingTheActorIsLoggedNotThrown() {
        when(currentUserProvider.getCurrentUserId()).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatCode(() -> recorder.record(IdentityAuditRecorder.USER_LOCK, "USER", "kc-1", "x"))
                .doesNotThrowAnyException();
        verifyNoInteractions(auditLogService);
    }

    @Test
    void transactionFailureIsLoggedNotThrown() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("staff-1");
        doThrow(new CannotCreateTransactionException("db down"))
                .when(auditLogService).record(anyString(), any(AuditEvent.class), any(Instant.class));

        assertThatCode(() -> recorder.record(IdentityAuditRecorder.USER_LOCK, "USER", "kc-1", "x"))
                .doesNotThrowAnyException();
    }

    @Test
    void storageFailureIsLoggedNotThrownBecauseTheChangeIsAlreadyApplied() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("staff-1");
        doThrow(new DataAccessResourceFailureException("db down"))
                .when(auditLogService).record(anyString(), any(AuditEvent.class), any(Instant.class));

        assertThatCode(() -> recorder.record(IdentityAuditRecorder.USER_LOCK, "USER", "kc-1", "x"))
                .doesNotThrowAnyException();
    }
}
