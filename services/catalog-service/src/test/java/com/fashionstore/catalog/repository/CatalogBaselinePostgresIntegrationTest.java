package com.fashionstore.catalog.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the same reload tests against the actual fresh-install Flyway schema. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database=postgresql",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CatalogBaselinePostgresIntegrationTest extends CatalogEntityPersistenceTest {
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:16-alpine")
            .withEnv("POSTGRES_DB", "catalog_baseline_test")
            .withEnv("POSTGRES_USER", "catalog_test")
            .withEnv("POSTGRES_PASSWORD", "test-only-password")
            .withExposedPorts(5432).waitingFor(Wait.forListeningPort());
    static { POSTGRES.start(); }

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/catalog_baseline_test");
        registry.add("spring.datasource.username", () -> "catalog_test");
        registry.add("spring.datasource.password", () -> "test-only-password");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @AfterAll static void stopDatabase() { POSTGRES.stop(); }
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test void hasOneValidatedMigrationAndNoRetiredTablesOrDuplicateProductColumns() {
        assertThat(flyway.info().applied()).hasSize(1);
        assertThat(flyway.info().pending()).isEmpty();
        flyway.validate();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='public' "
                + "and table_name in ('order_inventory_cancellation','product_option','product_option_value','variant_option_value')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema='public' and table_name='product' "
                + "and column_name in ('published','price','category_id','weight_gram','length_mm','width_mm','height_mm')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conname in "
                + "('ck_product_weight_range','ck_product_length_range','ck_product_width_range','ck_product_height_range',"
                + "'ck_variant_weight_range','ck_variant_length_range','ck_variant_width_range','ck_variant_height_range')", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("select count(*) from pg_indexes where schemaname='public' "
                + "and indexname='uq_inventory_reservation_cancellation_id'", Integer.class)).isEqualTo(1);
    }
}
