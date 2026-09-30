package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.entity.Inventory;
import com.fashionstore.catalog.entity.InventoryReservation;
import com.fashionstore.catalog.entity.InventoryReservationItem;
import com.fashionstore.catalog.entity.Product;
import com.fashionstore.catalog.entity.ProductVariant;
import com.fashionstore.catalog.entity.enumeration.InventoryReservationStatus;
import com.fashionstore.catalog.entity.enumeration.InventoryStatus;
import com.fashionstore.catalog.entity.enumeration.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.annotation.DirtiesContext;
import com.fashionstore.catalog.dto.ReserveStockRequest;
import com.fashionstore.catalog.mapper.InventoryMapperImpl;
import com.fashionstore.catalog.service.impl.InventoryServiceImpl;
import com.fashionstore.common.exception.AppException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database=h2",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:inventorytest;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InventoryRepositoryQueryTest {

    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired InventoryRepository inventories;
    @Autowired InventoryReservationRepository reservations;
    @Autowired InventoryReservationItemRepository reservationItems;
    @Autowired InventoryLedgerRepository ledger;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void searchLowStockAndReservationsUsePersistedData() {
        Product product = Product.builder().name("Basic Tee").slug("basic-tee")
                .status(ProductStatus.PUBLISHED).published(true).build();
        product = products.saveAndFlush(product);
        ProductVariant variant = ProductVariant.builder().product(product)
                .sku("TEE-BLK-M").optionSignature("BLACK-M").displayName("Black / M")
                .active(true).build();
        variant = variants.saveAndFlush(variant);
        Inventory inventory = Inventory.builder().productId(product.getId()).variantId(variant.getId())
                .quantity(10).reservedQuantity(3).minThreshold(7).status(InventoryStatus.ACTIVE).build();
        inventories.saveAndFlush(inventory);

        assertThat(inventories.searchForAdmin("%tee%", "LOW", false, ProductStatus.PUBLISHED,
                PageRequest.of(0, 20)).getContent()).hasSize(1);
        assertThat(inventories.searchForAdmin(null, "OUT", false, ProductStatus.PUBLISHED,
                PageRequest.of(0, 20)).getContent()).isEmpty();
        assertThat(inventories.findLowStockInventories(null, ProductStatus.PUBLISHED,
                PageRequest.of(0, 20)).getContent()).hasSize(1);
        assertThat(inventories.countLowStockInventories(6, ProductStatus.PUBLISHED)).isZero();

        InventoryReservation reservation = InventoryReservation.builder().orderId("order-1")
                .status(InventoryReservationStatus.RESERVED).build();
        reservation = reservations.saveAndFlush(reservation);
        reservationItems.saveAndFlush(InventoryReservationItem.builder()
                .reservationId(reservation.getId()).variantId(variant.getId()).quantity(3).build());
        assertThat(reservationItems.findForAdmin(variant.getId(), InventoryReservationStatus.RESERVED,
                PageRequest.of(0, 20)).getContent()).hasSize(1);
        assertThat(ledger.search(null, null, null, null, PageRequest.of(0, 20)).getContent()).isEmpty();

        inventory.setQuantity(3);
        inventories.saveAndFlush(inventory);
        assertThat(inventories.searchForAdmin(null, "LOW", false, ProductStatus.PUBLISHED,
                PageRequest.of(0, 20)).getContent()).hasSize(1);
        assertThat(inventories.searchForAdmin(null, "OUT", false, ProductStatus.PUBLISHED,
                PageRequest.of(0, 20)).getContent()).hasSize(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void concurrentReservationsCannotSellTheLastUnitTwice() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        String variantId = transaction.execute(status -> {
            Product product = products.saveAndFlush(Product.builder().name("Last Tee")
                    .slug("last-tee").status(ProductStatus.PUBLISHED).published(true).build());
            ProductVariant variant = variants.saveAndFlush(ProductVariant.builder().product(product)
                    .sku("LAST-TEE").optionSignature("LAST").displayName("Last")
                    .active(true).build());
            inventories.saveAndFlush(Inventory.builder().productId(product.getId()).variantId(variant.getId())
                    .quantity(1).reservedQuantity(0).minThreshold(10).status(InventoryStatus.ACTIVE).build());
            return variant.getId();
        });
        InventoryServiceImpl service = new InventoryServiceImpl(inventories, reservations, reservationItems,
                ledger, variants, products, null, new InventoryMapperImpl(), null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<Boolean>> results = List.of("order-a", "order-b").stream()
                    .map(orderId -> pool.submit(() -> {
                        ready.countDown();
                        start.await();
                        try {
                            transaction.execute(status -> {
                                ReserveStockRequest request = new ReserveStockRequest();
                                request.setOrderId(orderId);
                                ReserveStockRequest.ReserveItem item = new ReserveStockRequest.ReserveItem();
                                item.setVariantId(variantId);
                                item.setQuantity(1);
                                request.setItems(List.of(item));
                                service.reserveStock(request);
                                return null;
                            });
                            return true;
                        } catch (AppException insufficient) {
                            return false;
                        }
                    }))
                    .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long successes = 0;
            for (var result : results) {
                if (result.get(20, TimeUnit.SECONDS)) successes++;
            }
            assertThat(successes).isEqualTo(1);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
        Inventory stock = transaction.execute(status -> inventories.findByVariantId(variantId).orElseThrow());
        assertThat(stock.getQuantity()).isEqualTo(1);
        assertThat(stock.getReservedQuantity()).isEqualTo(1);
    }
}
