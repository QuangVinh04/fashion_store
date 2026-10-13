package com.fashionstore.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.*;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.payment.dto.WalletPaymentSession;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import com.fashionstore.payment.event.OutboxEventRecorder;
import com.fashionstore.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;

@DataJpaTest(
        includeFilters =
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "com.fashionstore.payment.service.WalletService"))
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({WalletPaymentIntegrationTest.Fixtures.class, OutboxEventRecorder.class})
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
            "spring.datasource.url=jdbc:h2:mem:wallet-payment-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password="
        })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WalletPaymentIntegrationTest {
    @TestConfiguration
    static class Fixtures {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        CurrentUserProvider currentUserProvider() {
            return mock(CurrentUserProvider.class);
        }
    }

    @Autowired WalletService walletPaymentService;
    @Autowired PaymentRepository payments;
    @Autowired WalletService ledger;
    @Autowired CurrentUserProvider owner;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @org.springframework.test.context.bean.override.mockito.MockitoBean vn.payos.PayOS payOS;
    String user;

    @BeforeEach
    void owner() {
        user = UUID.randomUUID().toString();
        when(owner.getCurrentUserId()).thenReturn(user);
    }

    @Test
    void pageReadNeverDebits() {
        Payment payment = payment(Instant.now().plusSeconds(900));
        ledger.credit(user, 100000, "TEST", "initial", UUID.randomUUID().toString());
        walletPaymentService.getSession(payment.getId());
        assertThat(ledger.getBalance(user)).isEqualTo(100000);
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void duplicateConfirmReturnsSameSuccess() {
        Payment payment = payment(Instant.now().plusSeconds(900));
        ledger.credit(user, 100000, "TEST", "initial", UUID.randomUUID().toString());
        confirm(payment, 0);
        confirm(payment, 0);
        assertThat(ledger.getBalance(user)).isEqualTo(20000);
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from outbox_event where aggregate_id=? and"
                                        + " event_type='payment.completed'",
                                Long.class,
                                payment.getOrderId()))
                .isEqualTo(1);
    }

    @Test
    void insufficientBalanceBlocksServer() {
        Payment payment = payment(Instant.now().plusSeconds(900));
        ledger.credit(user, 20000, "TEST", "initial", UUID.randomUUID().toString());
        assertThatThrownBy(() -> confirm(payment, 0)).isInstanceOf(AppException.class);
        assertThat(ledger.getBalance(user)).isEqualTo(20000);
    }

    @Test
    void wrongOwner404() {
        Payment payment = payment(Instant.now().plusSeconds(900));
        when(owner.getCurrentUserId()).thenReturn("foreign-owner");
        assertThatThrownBy(() -> walletPaymentService.getSession(payment.getId()))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(com.fashionstore.payment.exception.PaymentErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void expiredAuthorizationCannotDebit() {
        Payment payment = payment(Instant.now().minusSeconds(1));
        ledger.credit(user, 100000, "TEST", "initial", UUID.randomUUID().toString());
        assertThatThrownBy(() -> confirm(payment, 0)).isInstanceOf(AppException.class);
        assertThat(ledger.getBalance(user)).isEqualTo(100000);
    }

    @Test
    void twoTabsDebitOnce() throws Exception {
        Payment payment = payment(Instant.now().plusSeconds(900));
        ledger.credit(user, 100000, "TEST", "initial", UUID.randomUUID().toString());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<WalletPaymentSession> confirm =
                () -> {
                    start.await();
                    return confirm(payment, 0);
                };
        try {
            Future<WalletPaymentSession> a = executor.submit(confirm), b = executor.submit(confirm);
            start.countDown();
            a.get(15, TimeUnit.SECONDS);
            b.get(15, TimeUnit.SECONDS);
            assertThat(ledger.getBalance(user)).isEqualTo(20000);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void cancelVersusConfirmHasSingleWinner() throws Exception {
        Payment payment = payment(Instant.now().plusSeconds(900));
        ledger.credit(user, 100000, "TEST", "initial", UUID.randomUUID().toString());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<WalletPaymentSession> confirmed =
                    executor.submit(
                            () -> {
                                start.await();
                                try {
                                    return confirm(payment, 0);
                                } catch (AppException expected) {
                                    return null;
                                }
                            });
            Future<Boolean> cancelled =
                    executor.submit(
                            () -> {
                                start.await();
                                return walletPaymentService.cancelPending(payment.getId());
                            });
            start.countDown();
            confirmed.get(15, TimeUnit.SECONDS);
            cancelled.get(15, TimeUnit.SECONDS);
            PaymentStatus status = payments.findById(payment.getId()).orElseThrow().getStatus();
            assertThat(status).isIn(PaymentStatus.COMPLETED, PaymentStatus.CANCELLED);
            assertThat(ledger.getBalance(user))
                    .isEqualTo(status == PaymentStatus.COMPLETED ? 20000 : 100000);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void enclosingTransactionFailureRollsBackPaymentDebitAndOutbox() {
        Payment payment = payment(Instant.now().plusSeconds(900));
        ledger.credit(user, 100000, "TEST", "initial", UUID.randomUUID().toString());
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                status -> {
                                                    confirm(payment, 0);
                                                    throw new IllegalStateException(
                                                            "outbox failure");
                                                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ledger.getBalance(user)).isEqualTo(100000);
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from outbox_event where aggregate_id=? and"
                                    + " event_type='payment.completed'",
                                Long.class,
                                payment.getOrderId()))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from outbox_event where aggregate_id=? and"
                                    + " event_type='payment.initiated'",
                                Long.class,
                                payment.getOrderId()))
                .isEqualTo(1);
    }

    protected Payment payment(Instant deadline) {
        Payment payment =
                Payment.builder()
                        .orderId(UUID.randomUUID().toString())
                        .userId(user)
                        .sagaId(UUID.randomUUID().toString())
                        .method(PaymentMethod.WALLET)
                        .provider(PaymentProvider.INTERNAL)
                        .amount(BigDecimal.valueOf(80000))
                        .currency("VND")
                        .status(PaymentStatus.PENDING)
                        .build();
        payment.setAuthorizationExpiresAt(deadline);
        payment = payments.saveAndFlush(payment);
        walletPaymentService.prepare(payment);
        return payment;
    }

    protected WalletPaymentSession confirm(Payment payment, int generation) {
        return walletPaymentService.confirm(payment.getId(), generation);
    }
}
