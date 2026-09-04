package com.fashionstore.product.modules.inventory.service;

import com.fashionstore.product.modules.inventory.entity.Inventory;
import com.fashionstore.product.modules.inventory.entity.InventoryReservation;
import com.fashionstore.product.modules.inventory.entity.InventoryReservationStatus;
import com.fashionstore.product.modules.inventory.repository.InventoryRepository;
import com.fashionstore.product.modules.inventory.repository.InventoryReservationRepository;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.inventory.command.InventoryItem;
import com.fashionstore.contracts.inventory.command.ReservationInventoryCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReservationServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryReservationRepository reservationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private InventoryReservationService service;

    @BeforeEach
    void setUp() {
        service = new InventoryReservationService(
                inventoryRepository,
                reservationRepository,
                eventPublisher
        );
    }

    @Test
    void reserveMergesDuplicateVariantsAndMovesAvailableStockToReserved() {
        Inventory inventory = Inventory.builder()
                .variantId("variant-1")
                .availableQuantity(10)
                .reservedQuantity(1)
                .build();
        when(reservationRepository.findByOrderId("order-1")).thenReturn(Optional.empty());
        when(inventoryRepository.findByVariantIdForUpdate("variant-1")).thenReturn(Optional.of(inventory));
        when(reservationRepository.save(any())).thenAnswer(invocation -> {
            InventoryReservation reservation = invocation.getArgument(0);
            reservation.setId("reservation-1");
            return reservation;
        });

        service.reserve(new ReservationInventoryCommand(
                "order-1",
                "user-1",
                List.of(new InventoryItem("variant-1", 2), new InventoryItem("variant-1", 3))
        ), "correlation-1");

        assertEquals(5, inventory.getAvailableQuantity());
        assertEquals(6, inventory.getReservedQuantity());

        ArgumentCaptor<InventoryReservation> reservationCaptor =
                ArgumentCaptor.forClass(InventoryReservation.class);
        verify(reservationRepository).save(reservationCaptor.capture());
        InventoryReservation saved = reservationCaptor.getValue();
        assertEquals(InventoryReservationStatus.RESERVED, saved.getStatus());
        assertEquals(1, saved.getItems().size());
        assertEquals(5, saved.getItems().getFirst().getQuantity());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        EventEnvelope<?> envelope = (EventEnvelope<?>) eventCaptor.getValue();
        assertEquals(EventTypes.INVENTORY_RESERVED, envelope.eventType());
        assertEquals("correlation-1", envelope.correlationId());
    }

    @Test
    void reserveRejectsWithoutMutatingStockWhenAnyVariantIsInsufficient() {
        Inventory inventory = Inventory.builder()
                .variantId("variant-1")
                .availableQuantity(1)
                .reservedQuantity(0)
                .build();
        when(reservationRepository.findByOrderId("order-2")).thenReturn(Optional.empty());
        when(inventoryRepository.findByVariantIdForUpdate("variant-1")).thenReturn(Optional.of(inventory));
        when(reservationRepository.save(any())).thenAnswer(invocation -> {
            InventoryReservation reservation = invocation.getArgument(0);
            reservation.setId("reservation-2");
            return reservation;
        });

        service.reserve(new ReservationInventoryCommand(
                "order-2",
                "user-1",
                List.of(new InventoryItem("variant-1", 2))
        ), "correlation-2");

        assertEquals(1, inventory.getAvailableQuantity());
        assertEquals(0, inventory.getReservedQuantity());

        ArgumentCaptor<InventoryReservation> reservationCaptor =
                ArgumentCaptor.forClass(InventoryReservation.class);
        verify(reservationRepository).save(reservationCaptor.capture());
        assertEquals(InventoryReservationStatus.REJECTED, reservationCaptor.getValue().getStatus());
        assertNotNull(reservationCaptor.getValue().getRejectionReason());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(
                EventTypes.INVENTORY_REJECTED,
                ((EventEnvelope<?>) eventCaptor.getValue()).eventType()
        );
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void confirmMovesReservedStockToCommittedAndPublishesAcknowledgement() {
        Inventory inventory = Inventory.builder()
                .variantId("variant-1")
                .availableQuantity(5)
                .reservedQuantity(2)
                .build();
        InventoryReservation reservation = reservation(
                InventoryReservationStatus.RESERVED,
                inventory,
                2
        );
        when(reservationRepository.findWithItemsById("reservation-1"))
                .thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByVariantIdForUpdate("variant-1"))
                .thenReturn(Optional.of(inventory));

        service.confirm(
                new ReservationInventoryCommand("order-1", "reservation-1"),
                "correlation-1"
        );

        assertEquals(0, inventory.getReservedQuantity());
        assertEquals(InventoryReservationStatus.CONFIRMED, reservation.getStatus());
        assertEquals(EventTypes.INVENTORY_CONFIRMED, publishedEvent().eventType());
    }

    @Test
    void releaseRestoresAvailableStockAndPublishesAcknowledgement() {
        Inventory inventory = Inventory.builder()
                .variantId("variant-1")
                .availableQuantity(5)
                .reservedQuantity(2)
                .build();
        InventoryReservation reservation = reservation(
                InventoryReservationStatus.RESERVED,
                inventory,
                2
        );
        when(reservationRepository.findWithItemsById("reservation-1"))
                .thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByVariantIdForUpdate("variant-1"))
                .thenReturn(Optional.of(inventory));

        service.release(
                new ReservationInventoryCommand("order-1", "reservation-1"),
                "correlation-1"
        );

        assertEquals(7, inventory.getAvailableQuantity());
        assertEquals(0, inventory.getReservedQuantity());
        assertEquals(InventoryReservationStatus.RELEASED, reservation.getStatus());
        assertEquals(EventTypes.INVENTORY_RELEASED, publishedEvent().eventType());
    }

    private InventoryReservation reservation(
            InventoryReservationStatus status,
            Inventory inventory,
            int quantity
    ) {
        InventoryReservation reservation = InventoryReservation.builder()
                .orderId("order-1")
                .status(status)
                .build();
        reservation.setId("reservation-1");
        reservation.setItems(List.of(
                com.fashionstore.product.modules.inventory.entity.InventoryReservationItem.builder()
                        .reservation(reservation)
                        .variantId(inventory.getVariantId())
                        .quantity(quantity)
                        .build()
        ));
        return reservation;
    }

    private EventEnvelope<?> publishedEvent() {
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        return (EventEnvelope<?>) eventCaptor.getValue();
    }
}
