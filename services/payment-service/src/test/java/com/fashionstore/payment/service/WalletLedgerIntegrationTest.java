package com.fashionstore.payment.service;

import static org.assertj.core.api.Assertions.*;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.payment.entity.WalletLedgerEntry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.*;

@DataJpaTest(
        includeFilters =
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "com.fashionstore.payment.service.WalletService"))
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
            "spring.datasource.url=jdbc:h2:mem:wallet-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password="
        })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WalletLedgerIntegrationTest {
    @Autowired WalletService ledger;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.fashionstore.common.security.CurrentUserProvider currentUser;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.fashionstore.payment.event.OutboxEventRecorder outbox;
    @org.springframework.test.context.bean.override.mockito.MockitoBean vn.payos.PayOS payOS;

    @Test
    void creditReplayReturnsSameEntry() {
        String user = UUID.randomUUID().toString(), operation = UUID.randomUUID().toString();
        WalletLedgerEntry first = credit(user, 100000, operation);
        WalletLedgerEntry second = credit(user, 100000, operation);
        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(balance(user)).isEqualTo(100000);
        assertThat(entries(user)).isEqualTo(1);
    }

    @Test
    void sameKeyDifferentAmountConflict() {
        String user = UUID.randomUUID().toString(), key = UUID.randomUUID().toString();
        credit(user, 100000, key);
        assertThatThrownBy(() -> credit(user, 200000, key)).isInstanceOf(AppException.class);
        assertThat(balance(user)).isEqualTo(100000);
    }

    @Test
    void sameOperationDifferentSourceIsRejected() {
        String user = UUID.randomUUID().toString(), key = UUID.randomUUID().toString();
        credit(user, 100000, key);
        assertThatThrownBy(() -> ledger.credit(user, 100000L, "TEST", "different-source", key))
                .isInstanceOf(AppException.class);
        assertThat(balance(user)).isEqualTo(100000);
        assertThat(entries(user)).isEqualTo(1);
    }

    @Test
    void parallelFirstCreditsCreateOneAccountAndPreserveBothCredits() throws Exception {
        String user = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Object> credit =
                () -> {
                    start.await();
                    return credit(user, 100000, UUID.randomUUID().toString());
                };
        try {
            Future<Object> a = executor.submit(credit), b = executor.submit(credit);
            start.countDown();
            a.get(15, TimeUnit.SECONDS);
            b.get(15, TimeUnit.SECONDS);
            assertThat(balance(user)).isEqualTo(200000);
            assertThat(entries(user)).isEqualTo(2);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from wallet_account where user_id = ?",
                                    Long.class,
                                    user))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void operationCannotBeReplayedForAnotherOwnerOrDirection() {
        String user = UUID.randomUUID().toString(), key = UUID.randomUUID().toString();
        credit(user, 100000, key);
        assertThatThrownBy(() -> credit(UUID.randomUUID().toString(), 100000, key))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> ledger.debit(user, 100000, "TEST", "source-1", key))
                .isInstanceOf(AppException.class);
        assertThat(balance(user)).isEqualTo(100000);
    }

    @Test
    void debitInsufficientDoesNotChangeBalance() {
        String user = UUID.randomUUID().toString();
        credit(user, 20000, UUID.randomUUID().toString());
        assertThatThrownBy(
                        () ->
                                ledger.debit(
                                        user,
                                        80000,
                                        "TEST",
                                        "source-1",
                                        UUID.randomUUID().toString()))
                .isInstanceOf(AppException.class);
        assertThat(balance(user)).isEqualTo(20000);
        assertThat(entries(user)).isEqualTo(1);
    }

    @Test
    void overflowDoesNotChangePersistedBalance() {
        String user = UUID.randomUUID().toString();
        credit(user, Long.MAX_VALUE, UUID.randomUUID().toString());
        assertThatThrownBy(() -> credit(user, 1, UUID.randomUUID().toString()))
                .isInstanceOf(AppException.class);
        assertThat(balance(user)).isEqualTo(Long.MAX_VALUE);
        assertThat(entries(user)).isEqualTo(1);
    }

    @Test
    void parentTransactionRollbackAlsoRollsBackLedgerAndBalance() {
        String user = UUID.randomUUID().toString();
        credit(user, 100000, UUID.randomUUID().toString());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(
                        () ->
                                transaction.executeWithoutResult(
                                        status -> {
                                            ledger.debit(
                                                    user,
                                                    80000,
                                                    "TEST",
                                                    "source-1",
                                                    UUID.randomUUID().toString());
                                            throw new IllegalStateException(
                                                    "payment/outbox failed");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(balance(user)).isEqualTo(100000);
        assertThat(entries(user)).isEqualTo(1);
    }

    @Test
    void parallelDebitsOnlyOneSucceeds() throws Exception {
        String user = UUID.randomUUID().toString();
        credit(user, 100000, UUID.randomUUID().toString());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Boolean> debit =
                () -> {
                    start.await();
                    try {
                        ledger.debit(user, 80000, "TEST", "source-1", UUID.randomUUID().toString());
                        return true;
                    } catch (AppException expected) {
                        return false;
                    }
                };
        try {
            Future<Boolean> a = executor.submit(debit), b = executor.submit(debit);
            start.countDown();
            assertThat(
                            (a.get(10, TimeUnit.SECONDS) ? 1 : 0)
                                    + (b.get(10, TimeUnit.SECONDS) ? 1 : 0))
                    .isEqualTo(1);
            assertThat(balance(user)).isEqualTo(20000);
            assertThat(entries(user)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    private WalletLedgerEntry credit(String user, long amount, String operation) {
        return ledger.credit(user, amount, "TEST", "source-1", operation);
    }

    private long balance(String user) {
        return jdbc.queryForObject(
                "select balance from wallet_account where user_id = ?", Long.class, user);
    }

    private long entries(String user) {
        return jdbc.queryForObject(
                "select count(*) from wallet_ledger_entry where user_id = ?", Long.class, user);
    }
}
