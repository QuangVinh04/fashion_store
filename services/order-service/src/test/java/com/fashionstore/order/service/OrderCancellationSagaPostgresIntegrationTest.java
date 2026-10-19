package com.fashionstore.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fashionstore.common.payment.*;
import com.fashionstore.contracts.common.*;
import com.fashionstore.contracts.inventory.event.OrderInventoryCancelledEvent;
import com.fashionstore.contracts.payment.event.OrderFundsRefundCreditedEvent;
import com.fashionstore.order.entity.*;
import com.fashionstore.order.entity.enumeration.*;
import com.fashionstore.order.event.*;
import com.fashionstore.order.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    OrderCancellationSagaService.class,
    SagaOutbox.class,
    OutboxEventRecorder.class,
    OrderCancellationEventListener.class,
    OrderCancellationSagaPostgresIntegrationTest.Configuration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Tag("external-infrastructure")
class OrderCancellationSagaPostgresIntegrationTest {
    static final GenericContainer<?> POSTGRES =
            new GenericContainer<>("postgres:16-alpine")
                    .withEnv("POSTGRES_DB", "cancellation_test")
                    .withEnv("POSTGRES_USER", "cancellation_test")
                    .withEnv("POSTGRES_PASSWORD", "test-only-password")
                    .withExposedPorts(5432)
                    .waitingFor(Wait.forListeningPort());

    static {
        POSTGRES.start();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () ->
                        "jdbc:postgresql://"
                                + POSTGRES.getHost()
                                + ":"
                                + POSTGRES.getMappedPort(5432)
                                + "/cancellation_test");
        registry.add("spring.datasource.username", () -> "cancellation_test");
        registry.add("spring.datasource.password", () -> "test-only-password");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired OrderCancellationSagaService service;
    @Autowired OrderCancellationEventListener listener;
    @Autowired OrderRepository orders;
    @Autowired OrderSagaRepository sagas;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean PromotionService promotions;
    @MockitoBean OrderNotificationService notifications;

    @TestConfiguration
    static class Configuration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }

    Order fixture() {
        return new TransactionTemplate(manager)
                .execute(
                        status -> {
                            var order =
                                    orders.saveAndFlush(
                                            Order.builder()
                                                    .userId("customer-1")
                                                    .orderCode(
                                                            "C-"
                                                                    + UUID.randomUUID()
                                                                            .toString()
                                                                            .substring(0, 20))
                                                    .idempotencyKey(UUID.randomUUID().toString())
                                                    .shippingAddress("Address")
                                                    .address(
                                                            ShippingAddress.builder()
                                                                    .recipientName("Customer")
                                                                    .recipientPhone("0900000000")
                                                                    .build())
                                                    .status(OrderStatus.CONFIRMED)
                                                    .paymentMethod(PaymentMethod.ONLINE)
                                                    .paymentProvider(PaymentProvider.PAYOS)
                                                    .paymentId("payment-1")
                                                    .subtotalAmount(new BigDecimal("100000"))
                                                    .totalAmount(new BigDecimal("100000"))
                                                    .build());
                            var checkout = OrderSaga.start(order.getId());
                            checkout.setPaymentId("payment-1");
                            checkout.setInventoryReservationId("reservation-1");
                            checkout.complete();
                            sagas.saveAndFlush(checkout);
                            return order;
                        });
    }

    @Test
    void twoRequestsCommitOneSagaAndOneInventoryCommand() throws Exception {
        var order = fixture();
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> request =
                    () -> {
                        gate.await();
                        return service.request(
                                        order.getId(), "op", "Changed mind", "customer-1", false)
                                .getId();
                    };
            var first = executor.submit(request);
            var second = executor.submit(request);
            gate.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
        }
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from order_saga where order_id=?",
                                Integer.class,
                                order.getId()))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from outbox_event where aggregate_id=? and"
                                    + " event_type=?",
                                Integer.class,
                                order.getId(),
                                EventTypes.ORDER_INVENTORY_CANCELLATION_REQUESTED))
                .isEqualTo(1);
    }

    @Test
    void inboxReceiptAndHistoryRollbackTogetherAndRedeliveryCompletesOnce() {
        var order = fixture();
        var saga = service.request(order.getId(), "op", "Changed mind", "customer-1", false);
        service.inventoryCancelled(
                new OrderInventoryCancelledEvent(saga.getId(), order.getId(), "reservation-1"),
                saga.getId());
        var reply =
                new OrderFundsRefundCreditedEvent(
                        saga.getRefundId(),
                        order.getId(),
                        "payment-1",
                        "PAID_CANCELLATION",
                        saga.getId(),
                        "ledger-1",
                        order.getTotalAmount());
        var envelope =
                EventEnvelope.v1(
                        EventTypes.ORDER_FUNDS_REFUND_CREDITED, order.getId(), saga.getId(), reply);
        String messageId = UUID.randomUUID().toString();
        doThrow(new IllegalStateException("Notification outbox unavailable"))
                .when(notifications)
                .sendOrderCancelledNotification(any(), any());
        assertThatThrownBy(() -> listener.onReply(envelope, messageId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from processed_message where message_id=?",
                                Integer.class,
                                messageId))
                .isZero();
        assertThat(sagas.findById(saga.getId()).orElseThrow().getRefundLedgerEntryId()).isNull();
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLING);
        doNothing().when(notifications).sendOrderCancelledNotification(any(), any());
        listener.onReply(envelope, messageId);
        listener.onReply(envelope, UUID.randomUUID().toString());
        assertThat(sagas.findById(saga.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderSagaStatus.COMPLETED);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from order_status_history where order_id=? and"
                                    + " action='ORDER_CANCELLED'",
                                Integer.class,
                                order.getId()))
                .isEqualTo(1);
    }

    @Test
    void intentAndOutboxRollbackTogether() {
        var order = fixture();
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(manager)
                                        .execute(
                                                status -> {
                                                    service.request(
                                                            order.getId(),
                                                            "op",
                                                            "Changed mind",
                                                            "customer-1",
                                                            false);
                                                    throw new IllegalStateException(
                                                            "Fail before commit");
                                                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(sagas.findByOrderIdAndType(order.getId(), OrderSagaType.CANCELLATION)).isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from outbox_event where aggregate_id=?",
                                Integer.class,
                                order.getId()))
                .isZero();
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
    }
}
