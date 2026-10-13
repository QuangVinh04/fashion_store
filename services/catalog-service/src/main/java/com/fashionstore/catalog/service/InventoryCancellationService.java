package com.fashionstore.catalog.service;

import com.fashionstore.catalog.entity.*;
import com.fashionstore.catalog.entity.enumeration.*;
import com.fashionstore.catalog.repository.*;
import com.fashionstore.catalog.outbox.OutboxService;
import com.fashionstore.contracts.common.*;
import com.fashionstore.contracts.inventory.command.CancelOrderInventoryCommand;
import com.fashionstore.contracts.inventory.event.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Reverses stock for fulfillment stopped at the shop. Returned goods use manual inventory operations. */
@Service @RequiredArgsConstructor
public class InventoryCancellationService {
    private final InventoryReservationRepository reservations;
    private final InventoryReservationItemRepository items;
    private final InventoryRepository inventory;
    private final InventoryLedgerRepository ledger;
    private final OrderInventoryCancellationRepository audit;
    private final OutboxService outbox;

    @Transactional
    public void cancelUnshippedOrder(CancelOrderInventoryCommand command,String correlationId) {
        if(command==null || !valid(command.orderId()) || !valid(command.cancellationId()) || !valid(command.reservationId()))
            throw new IllegalArgumentException("Invalid inventory cancellation command");
        var reservation=reservations.findByOrderIdForUpdate(command.orderId()).orElse(null);
        if(reservation==null || !command.reservationId().equals(reservation.getId())) {
            rejected(command,correlationId,"RESERVATION_UNVERIFIED","Inventory reservation does not match the cancellation");return;
        }
        var previous=audit.findByOrderId(command.orderId()).orElse(null);
        if(previous!=null) {
            if(!command.cancellationId().equals(previous.getCancellationId()) || !command.reservationId().equals(previous.getReservationId()))
                rejected(command,correlationId,"CANCELLATION_CONFLICT","Reservation already belongs to another cancellation");
            else acknowledged(command,correlationId);
            return;
        }
        var original=reservation.getStatus();
        if(original==InventoryReservationStatus.CONFIRMED || original==InventoryReservationStatus.RESERVED) {
            var rows=new ArrayList<>(items.findByReservationId(reservation.getId()));
            rows.sort(Comparator.comparing(InventoryReservationItem::getVariantId));
            var stocks=new LinkedHashMap<String,Inventory>();
            if(rows.isEmpty()) {rejected(command,correlationId,"INVENTORY_UNVERIFIED","Reservation items are missing");return;}
            for(var row:rows) {
                var stock=inventory.findByVariantIdWithLock(row.getVariantId()).orElse(null);
                if(stock==null || row.getQuantity()==null || row.getQuantity()<=0 || stocks.containsKey(row.getVariantId())
                        || stock.getQuantity()==null || stock.getQuantity()<0 || stock.getReservedQuantity()==null || stock.getReservedQuantity()<0
                        || original==InventoryReservationStatus.RESERVED && stock.getReservedQuantity()<row.getQuantity()
                        || original==InventoryReservationStatus.CONFIRMED && (long)stock.getQuantity()+row.getQuantity()>Integer.MAX_VALUE) {
                    rejected(command,correlationId,"INVENTORY_UNVERIFIED","Stock cannot safely reverse this reservation");return;
                }
                stocks.put(row.getVariantId(),stock);
            }
            for(var row:rows) {
                var stock=stocks.get(row.getVariantId());int before=stock.getQuantity(),reserved=stock.getReservedQuantity();
                if(original==InventoryReservationStatus.CONFIRMED) stock.setQuantity(Math.addExact(before,row.getQuantity()));
                else stock.setReservedQuantity(reserved-row.getQuantity());
                inventory.save(stock);
                ledger.save(InventoryLedger.builder().variantId(row.getVariantId()).type(InventoryLedgerType.CANCEL_UNSHIPPED)
                        .quantity(row.getQuantity()).refOrderId(command.orderId()).quantityBefore(before).quantityAfter(stock.getQuantity())
                        .reservedBefore(reserved).reservedAfter(stock.getReservedQuantity()).operationId(UUID.randomUUID().toString())
                        .reason("Unshipped cancellation "+command.cancellationId()).createdBy("ORDER_CANCELLATION").build());
            }
        } else if(original!=InventoryReservationStatus.RELEASED && original!=InventoryReservationStatus.REJECTED) {
            rejected(command,correlationId,"RESERVATION_UNVERIFIED","Reservation state cannot be reconciled");return;
        }
        audit.saveAndFlush(OrderInventoryCancellation.builder().cancellationId(command.cancellationId()).orderId(command.orderId())
                .reservationId(command.reservationId()).originalStatus(original.name()).build());
        reservation.setStatus(InventoryReservationStatus.RELEASED);reservations.save(reservation);
        acknowledged(command,correlationId);
    }
    private boolean valid(String value) {return value!=null && !value.isBlank() && value.length()<=36;}
    private void acknowledged(CancelOrderInventoryCommand command,String correlationId) {
        outbox.saveMessage(command.orderId(),EventTypes.ORDER_INVENTORY_CANCELLED,EventEnvelope.v1(EventTypes.ORDER_INVENTORY_CANCELLED,
                command.orderId(),correlationId,new OrderInventoryCancelledEvent(command.cancellationId(),command.orderId(),command.reservationId())));
    }
    private void rejected(CancelOrderInventoryCommand command,String correlationId,String code,String reason) {
        outbox.saveMessage(command.orderId(),EventTypes.ORDER_INVENTORY_CANCELLATION_REJECTED,EventEnvelope.v1(EventTypes.ORDER_INVENTORY_CANCELLATION_REJECTED,
                command.orderId(),correlationId,new OrderInventoryCancellationRejectedEvent(command.cancellationId(),command.orderId(),code,reason)));
    }
}
