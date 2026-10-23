package com.fashionstore.payment.wallet.service;

import org.junit.jupiter.api.AfterAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/** Runs the ledger concurrency and rollback contract against the production database engine. */
@org.junit.jupiter.api.Tag("external-infrastructure")
class WalletLedgerPostgresIntegrationTest extends WalletLedgerIntegrationTest {
    private static final GenericContainer<?> POSTGRES =
            new GenericContainer<>("postgres:16-alpine")
                    .withEnv("POSTGRES_DB", "wallet_test")
                    .withEnv("POSTGRES_USER", "wallet_test")
                    .withEnv("POSTGRES_PASSWORD", "test-only-password")
                    .withExposedPorts(5432)
                    .waitingFor(Wait.forListeningPort());

    static {
        POSTGRES.start();
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
                                + "/wallet_test");
        registry.add("spring.datasource.username", () -> "wallet_test");
        registry.add("spring.datasource.password", () -> "test-only-password");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add(
                "spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add(
                "spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }
}
