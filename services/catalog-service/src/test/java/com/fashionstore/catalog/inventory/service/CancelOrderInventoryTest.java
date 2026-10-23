package com.fashionstore.catalog.inventory.service;

import com.fashionstore.catalog.inventory.entity.Inventory;
import com.fashionstore.catalog.inventory.entity.InventoryReservation;
import com.fashionstore.catalog.inventory.entity.InventoryReservationItem;
import com.fashionstore.catalog.inventory.entity.InventoryReservationStatus;
import com.fashionstore.catalog.inventory.repository.InventoryLedgerRepository;
import com.fashionstore.catalog.inventory.repository.InventoryRepository;
import com.fashionstore.catalog.inventory.repository.InventoryReservationItemRepository;
import com.fashionstore.catalog.inventory.repository.InventoryReservationRepository;
import com.fashionstore.catalog.shared.outbox.OutboxService;
import com.fashionstore.contracts.inventory.command.CancelOrderInventoryCommand;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class CancelOrderInventoryTest {
    final InventoryReservationRepository reservations=mock(InventoryReservationRepository.class);
    final InventoryReservationItemRepository items=mock(InventoryReservationItemRepository.class);
    final InventoryRepository inventory=mock(InventoryRepository.class);
    final InventoryLedgerRepository ledger=mock(InventoryLedgerRepository.class);
    final OutboxService outbox=mock(OutboxService.class);

    private void cancel(String cancellation,String reservation) {
        var service = new com.fashionstore.catalog.inventory.service.impl.InventoryServiceImpl(
                inventory, reservations, items, ledger, null, null, null, null, outbox);
        service.cancelUnshippedOrder(new CancelOrderInventoryCommand(cancellation, "order", reservation), "correlation");
    }
    private InventoryReservation reservation(InventoryReservationStatus status) {
        var row=InventoryReservation.builder().orderId("order").status(status).build();row.setId("reservation");
        when(reservations.findByOrderIdForUpdate("order")).thenReturn(Optional.of(row));return row;
    }
    private Inventory stock(int quantity,int reserved) {
        var item=InventoryReservationItem.builder().variantId("variant").quantity(2).build();
        when(items.findByReservationId("reservation")).thenReturn(List.of(item));
        var stock=Inventory.builder().variantId("variant").quantity(quantity).reservedQuantity(reserved).build();
        when(inventory.findByVariantIdWithLock("variant")).thenReturn(Optional.of(stock));return stock;
    }
    @Test void confirmedStockRestoredOnce() throws Exception {
        var reservation=reservation(InventoryReservationStatus.CONFIRMED);var stock=stock(8,0);
        cancel("cancellation","reservation");cancel("cancellation","reservation");
        assertThat(stock.getQuantity()).isEqualTo(10);assertThat(stock.getReservedQuantity()).isZero();
        assertThat(reservation.getStatus()).isEqualTo(InventoryReservationStatus.RELEASED);
        assertThat(reservation.getCancellationId()).isEqualTo("cancellation");
        verify(ledger,times(1)).save(any());verify(outbox,times(2)).saveMessage(eq("order"),eq("inventory.order.cancelled"),any());
    }
    @Test void reservedStockOnlyReleasesReservation() throws Exception {
        reservation(InventoryReservationStatus.RESERVED);var stock=stock(10,2);cancel("cancellation","reservation");
        assertThat(stock.getQuantity()).isEqualTo(10);assertThat(stock.getReservedQuantity()).isZero();
    }
    @Test void mismatchedReservationCannotRestoreStock() throws Exception {
        reservation(InventoryReservationStatus.CONFIRMED);cancel("cancellation","foreign");
        verifyNoInteractions(inventory,ledger);verify(outbox).saveMessage(eq("order"),eq("inventory.order.cancellation.rejected"),any());
    }
    @Test void anotherCancellationCannotRestoreTheSameReservation() throws Exception {
        var res = reservation(InventoryReservationStatus.RELEASED);
        res.setCancellationId("first");
        cancel("second","reservation");verifyNoInteractions(inventory,ledger);
        verify(outbox).saveMessage(eq("order"),eq("inventory.order.cancellation.rejected"),any());
    }
    @Test void missingReservationRequiresReviewInsteadOfSuccess() throws Exception {
        cancel("cancellation","reservation");verifyNoInteractions(inventory,ledger);
        verify(outbox).saveMessage(eq("order"),eq("inventory.order.cancellation.rejected"),any());
    }
}
