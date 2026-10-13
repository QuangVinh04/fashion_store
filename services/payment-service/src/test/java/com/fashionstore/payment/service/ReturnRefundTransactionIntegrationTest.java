package com.fashionstore.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fashionstore.common.payment.*;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.payment.command.RefundOrderFundsCommand;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import com.fashionstore.payment.event.OutboxEventRecorder;
import com.fashionstore.payment.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.annotation.*;

import vn.payos.PayOS;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@DataJpaTest(
        includeFilters =
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "com.fashionstore.payment.service.WalletService"))
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OutboxEventRecorder.class, ReturnRefundTransactionIntegrationTest.Configuration.class})
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
            "spring.datasource.url=jdbc:h2:mem:return-refund-payment;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password="
        })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReturnRefundTransactionIntegrationTest {
    @Autowired WalletService service;
    @Autowired PaymentRepository payments;
    @Autowired PaymentRefundRepository refunds;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PayOS provider;
    @MockitoBean CurrentUserProvider actor;
    @MockitoSpyBean OutboxEventRecorder outbox;

    @Test
    void duplicateCommandWithNewMessageIdCreditsWalletOnceAndReplaysReceipt() {
        var payment = paidCod();
        var command = command(payment);
        service.refundOrderFunds(command, "first-message", command.sourceId());
        var refund = refunds.findByBusinessRefundId(command.refundId()).orElseThrow();
        service.refundOrderFunds(command, "retry-message", command.sourceId());
        assertThat(service.getBalance(payment.getUserId())).isEqualTo(100000);
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(
                        refunds.findByBusinessRefundId(command.refundId())
                                .orElseThrow()
                                .getLedgerEntryId())
                .isEqualTo(refund.getLedgerEntryId());
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from wallet_ledger_entry where user_id=?",
                                Long.class,
                                payment.getUserId()))
                .isEqualTo(1);
        assertThat(resultCount(payment)).isEqualTo(2);
    }

    @Test
    void resultOutboxFailureRollsBackRefundLedgerBalanceAndPayment() {
        var payment = paidCod();
        var command = command(payment);
        OutboxEventRecorder recorder = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(outbox);
        doAnswer(
                        call -> {
                            call.callRealMethod();
                            throw new IllegalStateException("result publish preparation failed");
                        })
                .when(recorder)
                .saveMessage(
                        eq(payment.getOrderId()),
                        eq(EventTypes.ORDER_FUNDS_REFUND_CREDITED),
                        any());
        assertThatThrownBy(() -> service.refundOrderFunds(command, "message", command.sourceId()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.getBalance(payment.getUserId())).isZero();
        assertThat(refunds.findByBusinessRefundId(command.refundId())).isEmpty();
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from wallet_ledger_entry where user_id=?",
                                Long.class,
                                payment.getUserId()))
                .isZero();
        assertThat(resultCount(payment)).isZero();
    }

    @Test
    void unpaidCodPublishesBusinessRejectionWithoutCreditingMoney() {
        var payment = paidCod();
        payment.setCodCollectedAt(null);
        payments.saveAndFlush(payment);
        var command = command(payment);
        service.refundOrderFunds(command, "message", command.sourceId());
        assertThat(service.getBalance(payment.getUserId())).isZero();
        assertThat(refunds.findByBusinessRefundId(command.refundId())).isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from outbox_event where aggregate_id=? and"
                                    + " event_type=?",
                                Long.class,
                                payment.getOrderId(),
                                EventTypes.ORDER_FUNDS_REFUND_REJECTED))
                .isEqualTo(1);
        assertThat(resultCount(payment)).isZero();
    }

    private long resultCount(Payment payment) {
        return jdbc.queryForObject(
                "select count(*) from outbox_event where aggregate_id=? and event_type=?",
                Long.class,
                payment.getOrderId(),
                EventTypes.ORDER_FUNDS_REFUND_CREDITED);
    }

    private Payment paidCod() {
        return payments.saveAndFlush(
                Payment.builder()
                        .orderId(UUID.randomUUID().toString())
                        .userId(UUID.randomUUID().toString())
                        .amount(new BigDecimal("100000"))
                        .currency("VND")
                        .method(PaymentMethod.COD)
                        .provider(PaymentProvider.COD)
                        .status(PaymentStatus.COMPLETED)
                        .paidAt(LocalDateTime.now())
                        .codCollectedAt(Instant.now())
                        .build());
    }

    private RefundOrderFundsCommand command(Payment payment) {
        String source = UUID.randomUUID().toString();
        return new RefundOrderFundsCommand(
                "return:" + source,
                payment.getOrderId(),
                payment.getId(),
                new BigDecimal("100000"),
                "VND",
                "RETURN_REFUND",
                source);
    }

    @TestConfiguration
    static class Configuration {
        @Bean
        ObjectMapper mapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
