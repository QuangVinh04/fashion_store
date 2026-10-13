package com.fashionstore.order.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fashionstore.common.payment.*;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.payment.event.OrderFundsRefundCreditedEvent;
import com.fashionstore.order.client.*;
import com.fashionstore.order.entity.*;
import com.fashionstore.order.entity.enumeration.*;
import com.fashionstore.order.event.OutboxEventRecorder;
import com.fashionstore.order.repository.*;
import com.fashionstore.order.service.ReturnService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    ReturnServiceImpl.class,
    OutboxEventRecorder.class,
    ReturnRefundTransactionIntegrationTest.Configuration.class
})
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
            "spring.datasource.url=jdbc:h2:mem:return-refund-queue;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password="
        })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReturnRefundTransactionIntegrationTest {
    @Autowired ReturnService service;
    @Autowired OrderRepository orders;
    @Autowired ReturnRequestRepository returns;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CurrentUserProvider actor;
    @MockitoBean CatalogFeignClient catalog;
    @MockitoBean GhnClient carrier;
    @MockitoBean com.fashionstore.order.service.OrderNotificationService notifications;
    @MockitoSpyBean OutboxEventRecorder outbox;

    @BeforeEach
    void setup() {
        when(actor.getCurrentUserId()).thenReturn("admin");
    }

    @Test
    void intentAndCommandCommitTogetherWhilePaymentIsUnavailable() {
        var returned = receivedReturn();
        service.refundReturn(returned.getId(), null);
        var pending = returns.findById(returned.getId()).orElseThrow();
        assertThat(pending.getRefundStartedAt()).isNotNull();
        assertThat(pending.getStatus()).isEqualTo(ReturnRequestStatus.RECEIVED);
        assertThat(commandCount(returned)).isEqualTo(1);
        assertThat(pending.getClosedAt()).isNull();
    }

    @Test
    void outboxFailureRollsBackIntentAndCommand() {
        var returned = receivedReturn();
        OutboxEventRecorder recorder = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(outbox);
        doAnswer(
                        call -> {
                            call.callRealMethod();
                            throw new IllegalStateException("outbox failed");
                        })
                .when(recorder)
                .saveMessage(eq(returned.getOrder().getId()), anyString(), any());
        assertThatThrownBy(() -> service.refundReturn(returned.getId(), null))
                .isInstanceOf(IllegalStateException.class);
        var pending = returns.findById(returned.getId()).orElseThrow();
        assertThat(pending.getRefundStartedAt()).isNull();
        assertThat(pending.getApprovedRefundAmount()).isNull();
        assertThat(commandCount(returned)).isZero();
    }

    @Test
    void concurrentRepliesCompleteAndRecordHistoryOnce() throws Exception {
        var returned = receivedReturn();
        service.refundReturn(returned.getId(), null);
        var reply =
                new OrderFundsRefundCreditedEvent(
                        "return:" + returned.getId(),
                        returned.getOrder().getId(),
                        returned.getOrder().getPaymentId(),
                        "RETURN_REFUND",
                        returned.getId(),
                        UUID.randomUUID().toString(),
                        new BigDecimal("100000"));
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        Callable<Void> credit =
                () -> {
                    start.await();
                    service.refundCredited(reply);
                    return null;
                };
        try {
            var first = executor.submit(credit);
            var second = executor.submit(credit);
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertThat(returns.findById(returned.getId()).orElseThrow().getStatus())
                .isEqualTo(ReturnRequestStatus.REFUNDED);
        assertThat(orders.findById(returned.getOrder().getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.REFUNDED);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from order_status_history where order_id=? and"
                                    + " action='RETURN_REFUNDED'",
                                Long.class,
                                returned.getOrder().getId()))
                .isEqualTo(1);
        assertThat(service.refundReturn(returned.getId(), null).getRefundLedgerEntryId())
                .isEqualTo(reply.ledgerEntryId());
        assertThat(commandCount(returned)).isEqualTo(1);
    }

    @Test
    void changedAmountCannotPersistIntentOrEmitCommand() {
        var returned = receivedReturn();
        assertThatThrownBy(() -> service.refundReturn(returned.getId(), BigDecimal.ONE))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
        assertThat(returns.findById(returned.getId()).orElseThrow().getRefundStartedAt()).isNull();
        assertThat(commandCount(returned)).isZero();
    }

    private long commandCount(ReturnRequest returned) {
        return jdbc.queryForObject(
                "select count(*) from outbox_event where aggregate_id=? and event_type=?",
                Long.class,
                returned.getOrder().getId(),
                com.fashionstore.contracts.common.EventTypes.ORDER_FUNDS_REFUND_REQUESTED);
    }

    private ReturnRequest receivedReturn() {
        String key = UUID.randomUUID().toString();
        var order =
                orders.saveAndFlush(
                        Order.builder()
                                .orderCode("R-" + key.substring(0, 20))
                                .checkoutId(key)
                                .idempotencyKey(key)
                                .userId("buyer")
                                .paymentId(key)
                                .paymentMethod(PaymentMethod.ONLINE)
                                .paymentProvider(PaymentProvider.PAYOS)
                                .shippingAddress("Test address")
                                .subtotalAmount(new BigDecimal("100000"))
                                .totalAmount(new BigDecimal("100000"))
                                .status(OrderStatus.DELIVERED)
                                .deliveredAt(Instant.now().minusSeconds(3600))
                                .build());
        return returns.saveAndFlush(
                ReturnRequest.builder()
                        .order(order)
                        .userId("buyer")
                        .reason("Wrong item")
                        .status(ReturnRequestStatus.RECEIVED)
                        .receivedAt(Instant.now())
                        .receivedBy("admin")
                        .qcPassed(true)
                        .build());
    }

    @TestConfiguration
    static class Configuration {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        ObjectMapper mapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
