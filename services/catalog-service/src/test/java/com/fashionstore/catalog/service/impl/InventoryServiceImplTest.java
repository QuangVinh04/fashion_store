package com.fashionstore.catalog.service.impl;

import com.fashionstore.catalog.entity.Inventory;
import com.fashionstore.catalog.entity.InventoryReservation;
import com.fashionstore.catalog.entity.InventoryReservationItem;
import com.fashionstore.catalog.entity.enumeration.InventoryReservationStatus;
import com.fashionstore.catalog.mapper.InventoryMapper;
import com.fashionstore.catalog.outbox.OutboxService;
import com.fashionstore.catalog.repository.InventoryRepository;
import com.fashionstore.catalog.repository.InventoryReservationItemRepository;
import com.fashionstore.catalog.repository.InventoryReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryReservationRepository reservationRepository;

    @Mock
    private InventoryReservationItemRepository reservationItemRepository;

    @Mock
    private com.fashionstore.catalog.repository.InventoryLedgerRepository inventoryLedgerRepository;

    @Mock
    private com.fashionstore.catalog.repository.ProductVariantRepository productVariantRepository;

    @Mock
    private com.fashionstore.catalog.repository.ProductRepository productRepository;

    @Mock
    private com.fashionstore.common.security.CurrentUserProvider currentUserProvider;

    @Mock
    private InventoryMapper inventoryMapper;

    @Mock
    private OutboxService outboxService;

    private InventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InventoryServiceImpl(
                inventoryRepository,
                reservationRepository,
                reservationItemRepository,
                inventoryLedgerRepository,
                productVariantRepository,
                productRepository,
                currentUserProvider,
                inventoryMapper,
                outboxService
        );
    }

    @Test
    void restock_whenConfirmed_restocksInventoryAndMarksReleased() {
        InventoryReservation reservation = InventoryReservation.builder()
                .orderId("order-1")
                .status(InventoryReservationStatus.CONFIRMED)
                .build();
        reservation.setId("res-1");

        InventoryReservationItem item1 = InventoryReservationItem.builder()
                .reservationId("res-1")
                .variantId("var-1")
                .quantity(2)
                .build();

        InventoryReservationItem item2 = InventoryReservationItem.builder()
                .reservationId("res-1")
                .variantId("var-2")
                .quantity(3)
                .build();

        List<InventoryReservationItem> items = new ArrayList<>();
        items.add(item1);
        items.add(item2);

        Inventory inv1 = Inventory.builder().variantId("var-1").quantity(10).reservedQuantity(0).build();
        Inventory inv2 = Inventory.builder().variantId("var-2").quantity(5).reservedQuantity(0).build();

        when(reservationRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(reservation));
        when(reservationItemRepository.findByReservationId("res-1")).thenReturn(items);
        when(inventoryRepository.findByVariantIdWithLock("var-1")).thenReturn(Optional.of(inv1));
        when(inventoryRepository.findByVariantIdWithLock("var-2")).thenReturn(Optional.of(inv2));

        service.restock("order-1");

        assertThat(inv1.getQuantity()).isEqualTo(12);
        assertThat(inv1.getReservedQuantity()).isEqualTo(0); // Không bị thay đổi
        assertThat(inv2.getQuantity()).isEqualTo(8);
        assertThat(inv2.getReservedQuantity()).isEqualTo(0);
        assertThat(reservation.getStatus()).isEqualTo(InventoryReservationStatus.RELEASED);

        verify(inventoryRepository, times(1)).save(inv1);
        verify(inventoryRepository, times(1)).save(inv2);
        verify(reservationRepository, times(1)).save(reservation);
    }

    @Test
    void restock_whenReserved_doesNotRestockAndDoesNotChangeReservedQuantity() {
        // Trạng thái RESERVED chưa từng bị trừ quantity thực tế — cấm restock
        InventoryReservation reservation = InventoryReservation.builder()
                .orderId("order-1")
                .status(InventoryReservationStatus.RESERVED)
                .build();
        reservation.setId("res-1");

        when(reservationRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(reservation));

        service.restock("order-1");

        assertThat(reservation.getStatus()).isEqualTo(InventoryReservationStatus.RESERVED);
        verify(reservationItemRepository, never()).findByReservationId(any());
        verify(inventoryRepository, never()).findByVariantIdWithLock(any());
        verify(inventoryRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void restock_whenRejected_doesNotRestock() {
        InventoryReservation reservation = InventoryReservation.builder()
                .orderId("order-1")
                .status(InventoryReservationStatus.REJECTED)
                .build();
        reservation.setId("res-1");

        when(reservationRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(reservation));

        service.restock("order-1");

        assertThat(reservation.getStatus()).isEqualTo(InventoryReservationStatus.REJECTED);
        verify(reservationItemRepository, never()).findByReservationId(any());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void restock_whenAlreadyReleased_isIdempotent() {
        InventoryReservation reservation = InventoryReservation.builder()
                .orderId("order-1")
                .status(InventoryReservationStatus.RELEASED)
                .build();
        reservation.setId("res-1");

        when(reservationRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(reservation));

        service.restock("order-1");

        verify(reservationItemRepository, never()).findByReservationId(any());
        verify(inventoryRepository, never()).findByVariantIdWithLock(any());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void restock_whenNoReservation_skipsGracefully() {
        when(reservationRepository.findByOrderIdForUpdate("order-999")).thenReturn(Optional.empty());

        service.restock("order-999");

        verify(reservationItemRepository, never()).findByReservationId(any());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void restock_whenConfirmed_savesLedgerEntries() {
        InventoryReservation reservation = InventoryReservation.builder()
                .orderId("order-1")
                .status(InventoryReservationStatus.CONFIRMED)
                .build();
        reservation.setId("res-1");

        InventoryReservationItem item1 = InventoryReservationItem.builder()
                .reservationId("res-1")
                .variantId("var-1")
                .quantity(2)
                .build();

        when(reservationRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(reservation));
        when(reservationItemRepository.findByReservationId("res-1")).thenReturn(List.of(item1));
        Inventory inv1 = Inventory.builder().variantId("var-1").quantity(10).reservedQuantity(0).build();
        when(inventoryRepository.findByVariantIdWithLock("var-1")).thenReturn(Optional.of(inv1));

        service.restock("order-1");

        org.mockito.ArgumentCaptor<com.fashionstore.catalog.entity.InventoryLedger> captor =
                org.mockito.ArgumentCaptor.forClass(com.fashionstore.catalog.entity.InventoryLedger.class);
        verify(inventoryLedgerRepository, times(1)).save(captor.capture());
        com.fashionstore.catalog.entity.InventoryLedger savedLedger = captor.getValue();
        assertThat(savedLedger.getVariantId()).isEqualTo("var-1");
        assertThat(savedLedger.getType()).isEqualTo(com.fashionstore.catalog.entity.enumeration.InventoryLedgerType.RESTOCK);
        assertThat(savedLedger.getQuantity()).isEqualTo(2);
        assertThat(savedLedger.getRefOrderId()).isEqualTo("order-1");
    }

    @Test
    void updateStock_recordsLedgerAdjust() {
        Inventory inv = Inventory.builder()
                .variantId("var-1")
                .quantity(10)
                .reservedQuantity(2)
                .build();
        when(inventoryRepository.findByVariantIdWithLock("var-1")).thenReturn(Optional.of(inv));
        when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.updateStock("var-1", 15);

        assertThat(inv.getQuantity()).isEqualTo(15);
        org.mockito.ArgumentCaptor<com.fashionstore.catalog.entity.InventoryLedger> captor =
                org.mockito.ArgumentCaptor.forClass(com.fashionstore.catalog.entity.InventoryLedger.class);
        verify(inventoryLedgerRepository, times(1)).save(captor.capture());
        com.fashionstore.catalog.entity.InventoryLedger saved = captor.getValue();
        assertThat(saved.getVariantId()).isEqualTo("var-1");
        assertThat(saved.getType()).isEqualTo(com.fashionstore.catalog.entity.enumeration.InventoryLedgerType.ADJUST);
        assertThat(saved.getQuantity()).isEqualTo(5); // 15 - 10 = +5
    }

    @Test
    void reserveStock_recordsLedgerReserve() {
        com.fashionstore.catalog.dto.ReserveStockRequest request = new com.fashionstore.catalog.dto.ReserveStockRequest();
        request.setOrderId("order-100");
        com.fashionstore.catalog.dto.ReserveStockRequest.ReserveItem item = new com.fashionstore.catalog.dto.ReserveStockRequest.ReserveItem();
        item.setVariantId("var-1");
        item.setQuantity(3);
        request.setItems(List.of(item));

        when(reservationRepository.findByOrderId("order-100")).thenReturn(Optional.empty());
        Inventory inv = Inventory.builder().variantId("var-1").quantity(10).reservedQuantity(0).build();
        when(inventoryRepository.findByVariantIdWithLock("var-1")).thenReturn(Optional.of(inv));
        InventoryReservation savedRes = InventoryReservation.builder().orderId("order-100").status(InventoryReservationStatus.RESERVED).build();
        savedRes.setId("res-100");
        when(reservationRepository.save(any())).thenReturn(savedRes);

        service.reserveStock(request);

        assertThat(inv.getReservedQuantity()).isEqualTo(3);
        org.mockito.ArgumentCaptor<com.fashionstore.catalog.entity.InventoryLedger> captor =
                org.mockito.ArgumentCaptor.forClass(com.fashionstore.catalog.entity.InventoryLedger.class);
        verify(inventoryLedgerRepository, times(1)).save(captor.capture());
        com.fashionstore.catalog.entity.InventoryLedger saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(com.fashionstore.catalog.entity.enumeration.InventoryLedgerType.RESERVE);
        assertThat(saved.getQuantity()).isEqualTo(3);
        assertThat(saved.getRefOrderId()).isEqualTo("order-100");
    }

    @Test
    void confirmStock_recordsLedgerConfirm() {
        InventoryReservation reservation = InventoryReservation.builder()
                .orderId("order-200")
                .status(InventoryReservationStatus.RESERVED)
                .build();
        reservation.setId("res-200");

        InventoryReservationItem item = InventoryReservationItem.builder()
                .reservationId("res-200")
                .variantId("var-1")
                .quantity(4)
                .build();

        Inventory inv = Inventory.builder().variantId("var-1").quantity(10).reservedQuantity(4).build();

        when(reservationRepository.findByOrderId("order-200")).thenReturn(Optional.of(reservation));
        when(reservationItemRepository.findByReservationId("res-200")).thenReturn(List.of(item));
        when(inventoryRepository.findByVariantIdWithLock("var-1")).thenReturn(Optional.of(inv));

        service.confirmStock("order-200");

        assertThat(inv.getQuantity()).isEqualTo(6);
        assertThat(inv.getReservedQuantity()).isEqualTo(0);
        org.mockito.ArgumentCaptor<com.fashionstore.catalog.entity.InventoryLedger> captor =
                org.mockito.ArgumentCaptor.forClass(com.fashionstore.catalog.entity.InventoryLedger.class);
        verify(inventoryLedgerRepository, times(1)).save(captor.capture());
        com.fashionstore.catalog.entity.InventoryLedger saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(com.fashionstore.catalog.entity.enumeration.InventoryLedgerType.CONFIRM);
        assertThat(saved.getQuantity()).isEqualTo(4);
        assertThat(saved.getRefOrderId()).isEqualTo("order-200");
    }

    @Test
    void releaseStock_recordsLedgerRelease() {
        InventoryReservation reservation = InventoryReservation.builder()
                .orderId("order-300")
                .status(InventoryReservationStatus.RESERVED)
                .build();
        reservation.setId("res-300");

        InventoryReservationItem item = InventoryReservationItem.builder()
                .reservationId("res-300")
                .variantId("var-1")
                .quantity(3)
                .build();

        Inventory inv = Inventory.builder().variantId("var-1").quantity(10).reservedQuantity(3).build();

        when(reservationRepository.findByOrderId("order-300")).thenReturn(Optional.of(reservation));
        when(reservationItemRepository.findByReservationId("res-300")).thenReturn(List.of(item));
        when(inventoryRepository.findByVariantIdWithLock("var-1")).thenReturn(Optional.of(inv));

        com.fashionstore.catalog.dto.ReleaseStockRequest request = new com.fashionstore.catalog.dto.ReleaseStockRequest();
        request.setOrderId("order-300");
        service.releaseStock(request);

        assertThat(inv.getReservedQuantity()).isEqualTo(0);
        org.mockito.ArgumentCaptor<com.fashionstore.catalog.entity.InventoryLedger> captor =
                org.mockito.ArgumentCaptor.forClass(com.fashionstore.catalog.entity.InventoryLedger.class);
        verify(inventoryLedgerRepository, times(1)).save(captor.capture());
        com.fashionstore.catalog.entity.InventoryLedger saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(com.fashionstore.catalog.entity.enumeration.InventoryLedgerType.RELEASE);
        assertThat(saved.getQuantity()).isEqualTo(3);
        assertThat(saved.getRefOrderId()).isEqualTo("order-300");
    }

    @Test
    void getLedger_withVariantId_queriesByVariantId() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        com.fashionstore.catalog.entity.InventoryLedger ledger = com.fashionstore.catalog.entity.InventoryLedger.builder()
                .id("led-1")
                .variantId("var-1")
                .type(com.fashionstore.catalog.entity.enumeration.InventoryLedgerType.RESERVE)
                .quantity(2)
                .refOrderId("ord-1")
                .createdBy("ADMIN")
                .createdAt(java.time.LocalDateTime.now())
                .build();
        org.springframework.data.domain.Page<com.fashionstore.catalog.entity.InventoryLedger> page =
                new org.springframework.data.domain.PageImpl<>(List.of(ledger), pageable, 1);

        when(inventoryLedgerRepository.findByVariantId("var-1", pageable)).thenReturn(page);

        com.fashionstore.common.dto.PageResponse<List<com.fashionstore.catalog.dto.inventory.InventoryLedgerResponse>> response =
                service.getLedger("var-1", pageable);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getId()).isEqualTo("led-1");
        assertThat(response.getItems().get(0).getVariantId()).isEqualTo("var-1");
        assertThat(response.getItems().get(0).getType()).isEqualTo(com.fashionstore.catalog.entity.enumeration.InventoryLedgerType.RESERVE);
    }

    @Test
    void getLedger_withoutVariantId_queriesAll() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<com.fashionstore.catalog.entity.InventoryLedger> page =
                new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0);

        when(inventoryLedgerRepository.findAll(pageable)).thenReturn(page);

        com.fashionstore.common.dto.PageResponse<List<com.fashionstore.catalog.dto.inventory.InventoryLedgerResponse>> response =
                service.getLedger(null, pageable);

        assertThat(response.getItems()).isEmpty();
        verify(inventoryLedgerRepository, times(1)).findAll(pageable);
    }

    @Test
    void getLowStock_enrichesWithProductAndVariantInfo() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        Inventory inv = Inventory.builder()
                .variantId("var-1")
                .productId("prod-1")
                .quantity(5)
                .reservedQuantity(2)
                .build();
        org.springframework.data.domain.Page<Inventory> page = new org.springframework.data.domain.PageImpl<>(List.of(inv), pageable, 1);

        when(inventoryRepository.findLowStockInventories(10, pageable)).thenReturn(page);

        com.fashionstore.catalog.entity.ProductVariant variant = com.fashionstore.catalog.entity.ProductVariant.builder()
                .sku("SKU-VAR-1")
                .build();
        variant.setId("var-1");
        when(productVariantRepository.findAllById(List.of("var-1"))).thenReturn(List.of(variant));

        com.fashionstore.catalog.entity.Product product = com.fashionstore.catalog.entity.Product.builder()
                .name("Áo Thun Nam")
                .build();
        product.setId("prod-1");
        when(productRepository.findAllById(List.of("prod-1"))).thenReturn(List.of(product));

        com.fashionstore.common.dto.PageResponse<List<com.fashionstore.catalog.dto.inventory.LowStockItemResponse>> response =
                service.getLowStock(10, pageable);

        assertThat(response.getItems()).hasSize(1);
        com.fashionstore.catalog.dto.inventory.LowStockItemResponse item = response.getItems().get(0);
        assertThat(item.getVariantId()).isEqualTo("var-1");
        assertThat(item.getSku()).isEqualTo("SKU-VAR-1");
        assertThat(item.getProductId()).isEqualTo("prod-1");
        assertThat(item.getProductName()).isEqualTo("Áo Thun Nam");
        assertThat(item.getQuantity()).isEqualTo(5);
        assertThat(item.getReservedQuantity()).isEqualTo(2);
        assertThat(item.getAvailableQuantity()).isEqualTo(3);
        assertThat(item.getThreshold()).isEqualTo(10);
    }
}
