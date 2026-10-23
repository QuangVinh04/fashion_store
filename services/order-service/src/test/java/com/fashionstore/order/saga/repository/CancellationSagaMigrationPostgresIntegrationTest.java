package com.fashionstore.order.saga.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.UUID;

@Tag("external-infrastructure")
class CancellationSagaMigrationPostgresIntegrationTest {
    @Test
    void oneOrderCanHaveCheckoutAndCancellationButNotTwoCancellations() {
        try (var postgres =
                new GenericContainer<>("postgres:16-alpine")
                        .withEnv("POSTGRES_DB", "order_test")
                        .withEnv("POSTGRES_USER", "order_test")
                        .withEnv("POSTGRES_PASSWORD", "test-only-password")
                        .withExposedPorts(5432)
                        .waitingFor(Wait.forListeningPort())) {
            postgres.start();
            String url =
                    "jdbc:postgresql://"
                            + postgres.getHost()
                            + ":"
                            + postgres.getMappedPort(5432)
                            + "/order_test";
            String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
            Flyway.configure()
                    .dataSource(url, "order_test", "test-only-password")
                    .schemas(schema)
                    .target("27")
                    .load()
                    .migrate();
            var jdbc =
                    new JdbcTemplate(
                            new DriverManagerDataSource(
                                    url + "?currentSchema=" + schema,
                                    "order_test",
                                    "test-only-password"));
            jdbc.update(
                    """
INSERT INTO orders (id,idempotency_key,order_code,user_id,status,recipient_name,recipient_phone,
    shipping_address,subtotal_amount,discount_amount,shipping_fee,total_amount,payment_method)
VALUES ('order-1','checkout-1','MIGRATE-1','user-1','CONFIRMED','Customer','0900000000',
    'Address',100000,0,0,100000,'ONLINE')
""");
            jdbc.update(
                    """
INSERT INTO order_saga (id,order_id,status,current_step,payment_id,
    inventory_reservation_id,created_at,updated_at)
VALUES ('checkout-1','order-1','COMPLETED','DONE','payment-1','reservation-1',now(),now())
""");
            jdbc.update(
                    """
INSERT INTO order_cancellation (id,order_id,operation_id,requested_by,reason,status,
    requested_at,fulfillment_stopped_at,inventory_cancelled_at,refund_id,refund_ledger_entry_id)
VALUES ('cancel-1','order-1','operation-1','original-actor','Customer request','REFUND_PENDING',
    '2026-10-12 10:00:00+07','2026-10-12 10:01:00+07','2026-10-12 10:02:00+07',
    'legacy-refund-1','ledger-1')
""");
            String[][] phases = {
                {"FULFILLMENT_PENDING", "RUNNING", "STOP_SHIPMENT", "false", "false"},
                {"INVENTORY_PENDING", "RUNNING", "CANCEL_ORDER_INVENTORY", "true", "false"},
                {"REFUND_PENDING", "RUNNING", "REFUND_ORDER_FUNDS", "true", "true"},
                {"COMPLETED", "COMPLETED", "DONE", "true", "true"},
                {"REVIEW_REQUIRED", "FAILED", "REFUND_ORDER_FUNDS", "true", "true"},
                {"FULFILLMENT_EXCEPTION", "FAILED", "CANCEL_ORDER_INVENTORY", "true", "false"},
                {"REFUND_PENDING", "FAILED", "CANCEL_ORDER_INVENTORY", "true", "false"}
            };
            for (int index=0; index<phases.length; index++) {
                String id="phase-"+index;
                boolean completed="COMPLETED".equals(phases[index][0]);
                jdbc.update("""
                        INSERT INTO orders (id,idempotency_key,order_code,user_id,status,recipient_name,recipient_phone,
                            shipping_address,subtotal_amount,discount_amount,shipping_fee,total_amount,payment_method)
                        VALUES (?,?,?,'user-1',?,'Customer','0900000000','Address',100000,0,0,100000,'COD')
                        """,id,"checkout-"+id,"MIGRATE-"+id,completed ? "CANCELLED" : "CONFIRMED");
                jdbc.update("""
                        INSERT INTO order_saga (id,order_id,status,current_step,payment_id,inventory_reservation_id,created_at,updated_at)
                        VALUES (?,?,'COMPLETED','DONE','payment-1','reservation-1',now(),now())
                        ""","checkout-"+id,id);
                jdbc.update("""
                        INSERT INTO order_cancellation (id,order_id,operation_id,requested_by,reason,status,requested_at,
                            fulfillment_stopped_at,inventory_cancelled_at,closed_at)
                        VALUES (?,?,?,?,'Customer request',?,'2026-10-12 10:00:00+07',
                            CASE WHEN ? THEN TIMESTAMPTZ '2026-10-12 10:01:00+07' END,
                            CASE WHEN ? THEN TIMESTAMPTZ '2026-10-12 10:02:00+07' END,
                            CASE WHEN ? THEN TIMESTAMPTZ '2026-10-12 10:03:00+07' END)
                        ""","cancel-"+id,id,"op-"+id,"a".repeat(140),phases[index][0],
                        Boolean.parseBoolean(phases[index][3]),Boolean.parseBoolean(phases[index][4]),completed);
            }
            Flyway.configure()
                    .dataSource(url, "order_test", "test-only-password")
                    .schemas(schema)
                    .load()
                    .migrate();
            for (int index=0;index<phases.length;index++) {
                assertThat(jdbc.queryForObject("select status from order_saga where id=?",String.class,"cancel-phase-"+index))
                        .isEqualTo(phases[index][1]);
                assertThat(jdbc.queryForObject("select current_step from order_saga where id=?",String.class,"cancel-phase-"+index))
                        .isEqualTo(phases[index][2]);
            }
            assertThat(jdbc.queryForObject("select to_regclass('order_cancellation')::text",String.class)).isNull();
            assertThat(jdbc.queryForObject("select count(*) from outbox_event",Integer.class)).isZero();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM order_saga WHERE order_id='order-1'",
                                    Integer.class))
                    .isEqualTo(2);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT refund_id FROM order_saga WHERE id='cancel-1'",
                                    String.class))
                    .isEqualTo("legacy-refund-1");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT refund_ledger_entry_id FROM order_saga WHERE"
                                        + " id='cancel-1'",
                                    String.class))
                    .isEqualTo("ledger-1");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT created_at::text FROM order_saga WHERE id='cancel-1'",
                                    String.class))
                    .isEqualTo("2026-10-12 10:00:00");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT details_json FROM order_status_history WHERE"
                                        + " id='cancel-1' AND action='CANCELLATION_LEGACY_IMPORTED'",
                                    String.class))
                    .contains("original-actor", "legacy-refund-1", "ledger-1");
            assertThatThrownBy(
                            () ->
                                    jdbc.update(
                                            """
INSERT INTO order_saga (id,order_id,type,status,current_step,created_at,updated_at)
VALUES ('second-cancel','order-1','CANCELLATION','RUNNING','STOP_SHIPMENT',now(),now())
"""))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
    }
}
