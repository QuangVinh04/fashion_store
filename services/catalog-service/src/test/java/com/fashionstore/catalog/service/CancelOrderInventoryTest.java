package com.fashionstore.catalog.service;

import com.fashionstore.catalog.entity.*;
import com.fashionstore.catalog.entity.enumeration.InventoryReservationStatus;
import com.fashionstore.catalog.repository.*;
import com.fashionstore.catalog.outbox.OutboxService;
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
    final OrderInventoryCancellationRepository audit=mock(OrderInventoryCancellationRepository.class);
    final OutboxService outbox=mock(OutboxService.class);

    private void cancel(String cancellation,String reservation) throws Exception {
        Class<?> type=Class.forName("com.fashionstore.catalog.service.InventoryCancellationService");
        var instance=type.getConstructors()[0].newInstance(reservations,items,inventory,ledger,audit,outbox);
        type.getMethod("cancelUnshippedOrder",CancelOrderInventoryCommand.class,String.class)
                .invoke(instance,new CancelOrderInventoryCommand(cancellation,"order",reservation),"correlation");
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
        when(audit.saveAndFlush(any())).thenAnswer(call -> {
            when(audit.findByOrderId("order")).thenReturn(Optional.of(call.getArgument(0)));return call.getArgument(0);
        });
        cancel("cancellation","reservation");cancel("cancellation","reservation");
        assertThat(stock.getQuantity()).isEqualTo(10);assertThat(stock.getReservedQuantity()).isZero();
        assertThat(reservation.getStatus()).isEqualTo(InventoryReservationStatus.RELEASED);
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
        reservation(InventoryReservationStatus.RELEASED);
        when(audit.findByOrderId("order")).thenReturn(Optional.of(OrderInventoryCancellation.builder()
                .cancellationId("first").orderId("order").reservationId("reservation").originalStatus("CONFIRMED").build()));
        cancel("second","reservation");verifyNoInteractions(inventory,ledger);
        verify(outbox).saveMessage(eq("order"),eq("inventory.order.cancellation.rejected"),any());
    }
    @Test void missingReservationRequiresReviewInsteadOfSuccess() throws Exception {
        cancel("cancellation","reservation");verifyNoInteractions(inventory,ledger);
        verify(outbox).saveMessage(eq("order"),eq("inventory.order.cancellation.rejected"),any());
    }
}
