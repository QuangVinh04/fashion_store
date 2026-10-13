package com.fashionstore.catalog.messaging;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.catalog.CatalogServiceApplication;
import com.fashionstore.catalog.entity.*;
import com.fashionstore.catalog.entity.enumeration.*;
import com.fashionstore.catalog.repository.*;
import com.fashionstore.catalog.service.LegacyReturnRestockGuard;
import com.fashionstore.contracts.common.*;
import com.fashionstore.contracts.inventory.command.RestockInventoryCommand;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(includeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes=LegacyReturnRestockGuard.class))
@ContextConfiguration(classes=CatalogServiceApplication.class)
@Import(LegacyRestockPostgresIntegrationTest.Fixtures.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class LegacyRestockPostgresIntegrationTest {
    private static final GenericContainer<?> POSTGRES=new GenericContainer<>("postgres:16-alpine")
            .withEnv("POSTGRES_DB","legacy_stock_test").withEnv("POSTGRES_USER","legacy_stock_test")
            .withEnv("POSTGRES_PASSWORD","test-only-password").withExposedPorts(5432).waitingFor(Wait.forListeningPort());
    static { POSTGRES.start(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",() -> "jdbc:postgresql://"+POSTGRES.getHost()+":"+POSTGRES.getMappedPort(5432)+"/legacy_stock_test");
        registry.add("spring.datasource.username",() -> "legacy_stock_test");registry.add("spring.datasource.password",() -> "test-only-password");
        registry.add("spring.datasource.driver-class-name",() -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform",() -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.properties.hibernate.dialect",() -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled",() -> "true");registry.add("spring.jpa.hibernate.ddl-auto",() -> "validate");
    }
    @TestConfiguration static class Fixtures { @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); } }
    @Autowired LegacyReturnRestockGuard guard;
    @Autowired LegacyReturnRestockQuarantineRepository quarantine;
    @Autowired InventoryRepository inventories;
    @Autowired InventoryLedgerRepository ledger;
    @AfterAll static void stopDatabase() { POSTGRES.stop(); }
    @Test void pendingLegacyMessageKeepsExactlyOneAuditAndDoesNotIncreaseQuantityOrReserved() {
        var stock=stock(10);String order=UUID.randomUUID().toString(),message=UUID.randomUUID().toString();
        var event=EventEnvelope.v1(EventTypes.INVENTORY_RESTOCK_REQUESTED,order,order,new RestockInventoryCommand(order));
        guard.quarantine(message,event);guard.quarantine(message,event);
        var stored=quarantine.findByMessageId(message).orElseThrow();assertThat(stored.getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(inventories.findById(stock.getId()).orElseThrow().getQuantity()).isEqualTo(10);
        assertThat(inventories.findById(stock.getId()).orElseThrow().getReservedQuantity()).isEqualTo(3);
    }
    @Test void previouslyExecutedStockIsNeverReversedDuringQuarantine() {
        var stock=stock(12);String order=UUID.randomUUID().toString(),message=UUID.randomUUID().toString();
        ledger.saveAndFlush(InventoryLedger.builder().variantId(stock.getVariantId()).type(InventoryLedgerType.RESTOCK).quantity(2).refOrderId(order).build());
        guard.quarantine(message,EventEnvelope.v1(EventTypes.INVENTORY_RESTOCK_REQUESTED,order,order,new RestockInventoryCommand(order)));
        assertThat(quarantine.findByMessageId(message).orElseThrow().getStatus()).isEqualTo("ALREADY_EXECUTED");
        assertThat(inventories.findById(stock.getId()).orElseThrow().getQuantity()).isEqualTo(12);
    }
    private Inventory stock(int quantity) {
        return inventories.saveAndFlush(Inventory.builder().productId(UUID.randomUUID().toString()).variantId(UUID.randomUUID().toString())
                .quantity(quantity).reservedQuantity(3).minThreshold(1).status(InventoryStatus.ACTIVE).build());
    }
}
