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
}
