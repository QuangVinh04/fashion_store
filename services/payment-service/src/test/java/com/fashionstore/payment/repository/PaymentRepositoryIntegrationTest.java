package com.fashionstore.payment.repository;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.fashionstore.payment.repository.PaymentRepositoryIntegrationTest$SqlCapture"})
class PaymentRepositoryIntegrationTest {
    @Autowired TestEntityManager entityManager;
    @Autowired PaymentRepository repository;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = PaymentProvider.class, names = {"VNPAY", "PAYOS"})
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    void returnResolutionReadsWithoutLocking(PaymentProvider provider) {
        Payment payment = persist("order-return", "return-reference", PaymentStatus.COMPLETED, null);
        payment.setProvider(provider);
        entityManager.flush();
        entityManager.clear();
        var user = org.mockito.Mockito.mock(com.fashionstore.common.security.CurrentUserProvider.class);
        org.mockito.Mockito.when(user.getCurrentUserId()).thenReturn("user-1");
        var service = new com.fashionstore.payment.service.impl.PaymentServiceImpl(
                repository, null, null, user, null, null, null);

        SqlCapture.SQL.get().clear();
        var result = service.resolveReturn(provider, "return-reference");

        assertThat(result.orderId()).isEqualTo("order-return");
        assertThat(SqlCapture.SQL.get()).isNotEmpty()
                .noneMatch(sql -> sql.toLowerCase(java.util.Locale.ROOT).contains("for update"));

    }

    @Test
    void callbackLookupStillAcquiresWriteLock() {
        persist("order-callback", "callback-reference", PaymentStatus.PENDING, null);
        entityManager.flush();
        entityManager.clear();
        SqlCapture.SQL.get().clear();

        assertThat(repository.findByMerchantReference("callback-reference")).isPresent();

        assertThat(SqlCapture.SQL.get()).anyMatch(sql -> sql.toLowerCase(java.util.Locale.ROOT).contains("for update"));
    }

    @org.junit.jupiter.api.AfterEach
    void clearCapturedSql() {
        SqlCapture.SQL.remove();
    }

    public static class SqlCapture implements org.hibernate.resource.jdbc.spi.StatementInspector {
        static final ThreadLocal<java.util.List<String>> SQL = ThreadLocal.withInitial(java.util.ArrayList::new);

        @Override
        public String inspect(String sql) {
            SQL.get().add(sql);
            return sql;
        }
    }

    @Test
    void reconciliationSelectsOnlyDueOpenPaymentsWithIssuedLinks() {
        LocalDateTime now = LocalDateTime.now();
        Payment due = persist("order-due", "123", PaymentStatus.PENDING, null);
        due.setPaymentUrl("https://pay/123");
        persist("order-recent", "456", PaymentStatus.PENDING, now).setPaymentUrl("https://pay/456");
        persist("order-paid", "789", PaymentStatus.COMPLETED, null).setPaymentUrl("https://pay/789");
        persist("order-unprepared", null, PaymentStatus.PENDING, null);
        persist("order-no-link", "321", PaymentStatus.PENDING, null);
        entityManager.flush();
        entityManager.clear();
        List<Payment> selected = repository.findForReconciliation(
                List.of(PaymentStatus.PENDING, PaymentStatus.CANCELLED, PaymentStatus.FAILED),
                now.minusSeconds(30), java.time.Instant.now().minusSeconds(3600), PageRequest.of(0, 50));
        assertThat(selected).extracting(Payment::getId).containsExactly(due.getId());
        assertThat(repository.findByMerchantReference("123")).isPresent();
    }

    @Test
    void closedPaymentsStopBeingReconciledAfterTheLateCaptureWindow() {
        LocalDateTime now = LocalDateTime.now();
        java.time.Instant instant = java.time.Instant.now();
        Payment recent = persist("order-cancelled-recent", "111", PaymentStatus.CANCELLED, null);
        recent.setPaymentUrl("https://pay/111");
        recent.setAuthorizationExpiresAt(instant.minusSeconds(600));
        Payment old = persist("order-cancelled-old", "222", PaymentStatus.CANCELLED, null);
        old.setPaymentUrl("https://pay/222");
        old.setAuthorizationExpiresAt(instant.minusSeconds(7200));
        Payment stalePending = persist("order-pending-old", "333", PaymentStatus.PENDING, null);
        stalePending.setPaymentUrl("https://pay/333");
        stalePending.setAuthorizationExpiresAt(instant.minusSeconds(7200));
        entityManager.flush();
        entityManager.clear();
        List<Payment> selected = repository.findForReconciliation(
                List.of(PaymentStatus.PENDING, PaymentStatus.CANCELLED, PaymentStatus.FAILED),
                now.minusSeconds(30), instant.minusSeconds(3600), PageRequest.of(0, 50));
        assertThat(selected).extracting(Payment::getId)
                .containsExactlyInAnyOrder(recent.getId(), stalePending.getId());
    }

    private Payment persist(String orderId, String reference, PaymentStatus status, LocalDateTime checkedAt) {
        return entityManager.persist(Payment.builder().orderId(orderId).userId("user-1")
                .method(PaymentMethod.ONLINE).provider(PaymentProvider.PAYOS).status(status)
                .amount(BigDecimal.TEN).currency("VND").merchantReference(reference)
                .lastReconciledAt(checkedAt).build());
    }
}
