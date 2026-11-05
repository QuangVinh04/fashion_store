package com.fashionstore.order.saga.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@Tag("external-infrastructure")
class CancellationSagaUniquenessPostgresIntegrationTest {
    private static final String DATABASE = "order_test";
    private static final String PASSWORD = "test-only-password";
    private static final int POSTGRES_PORT = 5432;
    private static final String POSTGRES_READY_LOG = ".*database system is ready to accept connections.*\\s";
    private static final int POSTGRES_READY_LOG_COUNT = 2;

    @Test
    void oneOrderCanHaveCheckoutAndCancellationButNotTwoCancellations() {
        try (var postgres = new GenericContainer<>("postgres:16-alpine")
                .withEnv("POSTGRES_DB", DATABASE)
                .withEnv("POSTGRES_USER", DATABASE)
                .withEnv("POSTGRES_PASSWORD", PASSWORD)
                .withExposedPorts(POSTGRES_PORT)
                // The image starts a temporary init server first, so the port opens before the real server is ready.
                .waitingFor(Wait.forLogMessage(POSTGRES_READY_LOG, POSTGRES_READY_LOG_COUNT))) {
            postgres.start();
            String url = "jdbc:postgresql://" + postgres.getHost() + ":"
                    + postgres.getMappedPort(POSTGRES_PORT) + "/" + DATABASE;
            Flyway.configure().dataSource(url, DATABASE, PASSWORD).load().migrate();
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, DATABASE, PASSWORD));

            jdbc.update("""
                    INSERT INTO orders (id,idempotency_key,order_code,user_id,status,recipient_name,recipient_phone,
                        shipping_address,subtotal_amount,discount_amount,shipping_fee,total_amount,payment_method)
                    VALUES ('order-1','checkout-1','UNIQUE-1','user-1','CONFIRMED','Customer','0900000000',
                        'Address',100000,0,0,100000,'COD')
                    """);
            jdbc.update("""
                    INSERT INTO order_saga (id,order_id,status,current_step,created_at,updated_at)
                    VALUES ('checkout-1','order-1','COMPLETED','DONE',now(),now())
                    """);
            jdbc.update("""
                    INSERT INTO order_saga (id,order_id,type,status,current_step,created_at,updated_at)
                    VALUES ('cancel-1','order-1','CANCELLATION','RUNNING','STOP_SHIPMENT',now(),now())
                    """);

            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM order_saga WHERE order_id='order-1'", Integer.class)).isEqualTo(2);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO order_saga (id,order_id,type,status,current_step,created_at,updated_at)
                    VALUES ('cancel-2','order-1','CANCELLATION','RUNNING','STOP_SHIPMENT',now(),now())
                    """)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
