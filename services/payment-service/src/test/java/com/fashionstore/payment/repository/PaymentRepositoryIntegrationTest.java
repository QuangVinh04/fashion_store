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
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
class PaymentRepositoryIntegrationTest {
    @Autowired TestEntityManager entityManager;
    @Autowired PaymentRepository repository;

    @Test
    void reconciliationSelectsOnlyDueOpenPaymentsWithPersistedReferences() {
        LocalDateTime now = LocalDateTime.now();
        Payment due = persist("order-due", "123", PaymentStatus.INITIATION_UNKNOWN, null);
        persist("order-recent", "456", PaymentStatus.PENDING, now);
        persist("order-paid", "789", PaymentStatus.COMPLETED, null);
        persist("order-unprepared", null, PaymentStatus.PENDING, null);
        entityManager.flush();
        entityManager.clear();
        List<Payment> selected = repository.findForReconciliation(
                List.of(PaymentStatus.PENDING, PaymentStatus.INITIATING, PaymentStatus.INITIATION_UNKNOWN),
                now.minusSeconds(30), PageRequest.of(0, 50));
        assertThat(selected).extracting(Payment::getId).containsExactly(due.getId());
        assertThat(repository.findByMerchantReference("123")).isPresent();
    }

    private Payment persist(String orderId, String reference, PaymentStatus status, LocalDateTime checkedAt) {
        return entityManager.persist(Payment.builder().orderId(orderId).userId("user-1")
                .method(PaymentMethod.ONLINE).provider(PaymentProvider.PAYOS).status(status)
                .amount(BigDecimal.TEN).currency("VND").merchantReference(reference)
                .lastReconciledAt(checkedAt).build());
    }
}
