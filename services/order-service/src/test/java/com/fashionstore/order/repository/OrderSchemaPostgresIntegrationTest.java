package com.fashionstore.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.ReturnRequest;
import com.fashionstore.order.entity.ShippingAddress;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.entity.enumeration.ReturnRequestStatus;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.math.BigDecimal;
import java.time.Instant;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.junit.jupiter.api.Tag("external-infrastructure")
class OrderSchemaPostgresIntegrationTest {

    private static final GenericContainer<?> POSTGRES =
            new GenericContainer<>("postgres:16-alpine")
                    .withEnv("POSTGRES_DB", "order_test")
                    .withEnv("POSTGRES_USER", "order_test")
                    .withEnv("POSTGRES_PASSWORD", "test-only-password")
                    .withExposedPorts(5432)
                    .waitingFor(Wait.forListeningPort());

    static {
        POSTGRES.start();
    }

    @Autowired TestEntityManager entityManager;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () ->
                        "jdbc:postgresql://"
                                + POSTGRES.getHost()
                                + ":"
                                + POSTGRES.getMappedPort(5432)
                                + "/order_test");
        registry.add("spring.datasource.username", () -> "order_test");
        registry.add("spring.datasource.password", () -> "test-only-password");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add(
                "spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void migratedSchemaPersistsDeliveryAndWholeOrderReturn() {
        Instant deliveredAt = Instant.parse("2026-10-12T01:00:00Z");
        Order order =
                entityManager.persistAndFlush(
                        Order.builder()
                                .orderCode("LEAN-SCHEMA-001")
                                .checkoutId("checkout-schema-1")
                                .idempotencyKey("checkout-schema-1")
                                .userId("customer-1")
                                .shippingAddress("123 Test Street")
                                .address(
                                        ShippingAddress.builder()
                                                .recipientName("Test Customer")
                                                .recipientPhone("0900000000")
                                                .build())
                                .paymentMethod(PaymentMethod.COD)
                                .paymentProvider(PaymentProvider.COD)
                                .status(OrderStatus.DELIVERED)
                                .subtotalAmount(new BigDecimal("150000"))
                                .totalAmount(new BigDecimal("150000"))
                                .deliveredAt(deliveredAt)
                                .build());
        ReturnRequest request =
                entityManager.persistAndFlush(
                        ReturnRequest.builder()
                                .order(order)
                                .userId("customer-1")
                                .reason("Sai san pham")
                                .status(ReturnRequestStatus.REQUESTED)
                                .build());
        entityManager.clear();

        ReturnRequest persisted = entityManager.find(ReturnRequest.class, request.getId());
        assertThat(persisted.getOrder().getDeliveredAt()).isEqualTo(deliveredAt);
        assertThat(persisted.getStatus()).isEqualTo(ReturnRequestStatus.REQUESTED);
        assertThat(persisted.getOrder().getTotalAmount()).isEqualByComparingTo("150000");
    }
}
