package com.fashionstore.catalog.inventory.repository;

import com.fashionstore.catalog.inventory.entity.InventoryLedger;
import com.fashionstore.catalog.inventory.entity.InventoryLedgerType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Optional filters must not bind untyped nulls: Postgres rejects them with 42P18. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database=postgresql",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InventoryLedgerSearchPostgresIntegrationTest {
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:16-alpine")
            .withEnv("POSTGRES_DB", "ledger_search_test")
            .withEnv("POSTGRES_USER", "ledger_test")
            .withEnv("POSTGRES_PASSWORD", "test-only-password")
            .withExposedPorts(5432).waitingFor(Wait.forListeningPort());
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 10, 1, 9, 0);
    private static final Pageable NEWEST_FIRST = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

    static { POSTGRES.start(); }

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/ledger_search_test");
        registry.add("spring.datasource.username", () -> "ledger_test");
        registry.add("spring.datasource.password", () -> "test-only-password");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @AfterAll static void stopDatabase() { POSTGRES.stop(); }

    @Autowired InventoryLedgerRepository ledger;

    @BeforeEach
    void seedLedger() {
        ledger.saveAndFlush(entry("var-1", InventoryLedgerType.RESERVE, BASE_TIME));
        ledger.saveAndFlush(entry("var-1", InventoryLedgerType.RESTOCK, BASE_TIME.plusDays(1)));
        ledger.saveAndFlush(entry("var-2", InventoryLedgerType.RESERVE, BASE_TIME.plusDays(2)));
    }

    @Test
    void searchWithoutFiltersReturnsAllEntriesNewestFirst() {
        Page<InventoryLedger> page = search(null, null, null, null);

        assertThat(page.getContent()).extracting(InventoryLedger::getCreatedAt)
                .containsExactly(BASE_TIME.plusDays(2), BASE_TIME.plusDays(1), BASE_TIME);
    }

    @Test
    void searchAppliesEachFilterOnItsOwn() {
        assertThat(search("var-1", null, null, null).getContent()).hasSize(2);
        assertThat(search(null, InventoryLedgerType.RESERVE, null, null).getContent()).hasSize(2);
        assertThat(search(null, null, BASE_TIME.plusDays(1), null).getContent()).hasSize(2);
        assertThat(search(null, null, null, BASE_TIME).getContent()).hasSize(1);
    }

    @Test
    void searchTreatsBlankVariantIdAsNoFilter() {
        assertThat(search(" ", null, null, null).getContent()).hasSize(3);
    }

    @Test
    void searchCombinesFilters() {
        Page<InventoryLedger> page = search("var-1", InventoryLedgerType.RESTOCK, BASE_TIME, BASE_TIME.plusDays(1));

        assertThat(page.getContent()).singleElement()
                .extracting(InventoryLedger::getType).isEqualTo(InventoryLedgerType.RESTOCK);
    }

    private Page<InventoryLedger> search(String variantId, InventoryLedgerType type,
                                         LocalDateTime fromTime, LocalDateTime toTime) {
        return ledger.findAll(InventoryLedgerSpecifications.filter(variantId, type, fromTime, toTime), NEWEST_FIRST);
    }

    private static InventoryLedger entry(String variantId, InventoryLedgerType type, LocalDateTime createdAt) {
        return InventoryLedger.builder().variantId(variantId).type(type).quantity(1).createdAt(createdAt).build();
    }
}
