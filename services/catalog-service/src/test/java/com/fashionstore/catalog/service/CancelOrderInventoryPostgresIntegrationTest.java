package com.fashionstore.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.catalog.CatalogServiceApplication;
import com.fashionstore.catalog.entity.*;
import com.fashionstore.catalog.entity.enumeration.*;
import com.fashionstore.catalog.repository.*;
import com.fashionstore.catalog.outbox.OutboxService;
import com.fashionstore.contracts.inventory.command.CancelOrderInventoryCommand;
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

@DataJpaTest(includeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes=InventoryCancellationService.class))
@ContextConfiguration(classes=CatalogServiceApplication.class)
@Import({CancelOrderInventoryPostgresIntegrationTest.Fixtures.class,OutboxService.class})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class CancelOrderInventoryPostgresIntegrationTest {
    private static final GenericContainer<?> POSTGRES=new GenericContainer<>("postgres:16-alpine")
            .withEnv("POSTGRES_DB","cancel_stock_test").withEnv("POSTGRES_USER","cancel_stock_test")
            .withEnv("POSTGRES_PASSWORD","test-only-password").withExposedPorts(5432).waitingFor(Wait.forListeningPort());
    static { POSTGRES.start(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",() -> "jdbc:postgresql://"+POSTGRES.getHost()+":"+POSTGRES.getMappedPort(5432)+"/cancel_stock_test");
        registry.add("spring.datasource.username",() -> "cancel_stock_test");registry.add("spring.datasource.password",() -> "test-only-password");
        registry.add("spring.datasource.driver-class-name",() -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform",() -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.properties.hibernate.dialect",() -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled",() -> "true");registry.add("spring.jpa.hibernate.ddl-auto",() -> "validate");
    }
    @TestConfiguration static class Fixtures { @Bean ObjectMapper objectMapper() {return new ObjectMapper().findAndRegisterModules();} }
    @Autowired InventoryCancellationService service;
    @Autowired InventoryRepository inventory;
    @Autowired InventoryReservationRepository reservations;
    @Autowired InventoryReservationItemRepository items;
    @Autowired InventoryLedgerRepository ledger;
    @Autowired OrderInventoryCancellationRepository audit;
    @AfterAll static void stopDatabase() {POSTGRES.stop();}

    private CancelOrderInventoryCommand fixture(InventoryReservationStatus status) {
        String order=UUID.randomUUID().toString(),variant=UUID.randomUUID().toString();
        inventory.saveAndFlush(Inventory.builder().productId(UUID.randomUUID().toString()).variantId(variant)
                .quantity(status==InventoryReservationStatus.CONFIRMED?8:10).reservedQuantity(status==InventoryReservationStatus.RESERVED?2:0)
                .minThreshold(1).status(InventoryStatus.ACTIVE).build());
        var reservation=reservations.saveAndFlush(InventoryReservation.builder().orderId(order).status(status).build());
        items.saveAndFlush(InventoryReservationItem.builder().reservationId(reservation.getId()).variantId(variant).quantity(2).build());
        return new CancelOrderInventoryCommand(UUID.randomUUID().toString(),order,reservation.getId());
    }
    @Test void committedUnshippedQuantityReversesOnceUnderConcurrentDelivery() throws Exception {
        var command=fixture(InventoryReservationStatus.CONFIRMED);var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{start.await();service.cancelUnshippedOrder(command,"a");return true;});
            var b=pool.submit(()->{start.await();service.cancelUnshippedOrder(command,"b");return true;});start.countDown();
            assertThat(a.get(20,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(b.get(20,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
        var variant=items.findByReservationId(command.reservationId()).getFirst().getVariantId();
        assertThat(inventory.findByVariantId(variant).orElseThrow().getQuantity()).isEqualTo(10);
        assertThat(inventory.findByVariantId(variant).orElseThrow().getReservedQuantity()).isZero();
        assertThat(audit.findByOrderId(command.orderId())).isPresent();
        assertThat(ledger.findAll().stream().filter(row->command.orderId().equals(row.getRefOrderId())).toList()).singleElement()
                .satisfies(row->assertThat(row.getType()).isEqualTo(InventoryLedgerType.CANCEL_UNSHIPPED));
    }
    @Test void reservedCancellationKeepsPhysicalQuantity() {
        var command=fixture(InventoryReservationStatus.RESERVED);service.cancelUnshippedOrder(command,"reserved");
        var variant=items.findByReservationId(command.reservationId()).getFirst().getVariantId();
        var actual=inventory.findByVariantId(variant).orElseThrow();assertThat(actual.getQuantity()).isEqualTo(10);assertThat(actual.getReservedQuantity()).isZero();
    }
}
