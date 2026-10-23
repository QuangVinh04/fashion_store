package com.fashionstore.order.saga.service;

import com.fashionstore.order.order.service.OrderService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.shipment.client.GhnClient;
import com.fashionstore.order.saga.dto.CancelOrderRequest;
import com.fashionstore.order.saga.dto.CancellationCarrierWork;
import com.fashionstore.order.shipment.dto.ShipmentCancellationResult;
import com.fashionstore.order.order.entity.Order;
import com.fashionstore.order.saga.entity.OrderSaga;
import com.fashionstore.order.saga.entity.OrderSagaStep;
import com.fashionstore.order.order.entity.OrderStatus;
import com.fashionstore.order.order.repository.OrderRepository;
import com.fashionstore.order.saga.repository.OrderSagaRepository;

import org.junit.jupiter.api.Test;

import java.util.Optional;

class OrderCancellationServiceTest {
    final OrderCancellationSagaService database = mock(OrderCancellationSagaService.class);
    final GhnClient carrier = mock(GhnClient.class);
    final CurrentUserProvider owner = mock(CurrentUserProvider.class);
    final OrderRepository orders = mock(OrderRepository.class);
    final OrderSagaRepository sagas = mock(OrderSagaRepository.class);
    final CancellationCarrierWork work =
            new CancellationCarrierWork("order", "cancellation", "tracking", "shipment", null);
    final OrderService orderService = mock(OrderService.class);

    private OrderCancellationService service() {
        return new OrderCancellationService(database, carrier, owner, orders, sagas, orderService);
    }

    private void request() {
        service()
                .request(
                        "order",
                        CancelOrderRequest.builder()
                                .operationId("op")
                                .reason("changed mind")
                                .build());
    }

    private OrderSaga fixture() {
        when(owner.getCurrentUserId()).thenReturn("owner");
        var order = Order.builder().userId("owner").status(OrderStatus.CONFIRMED).build();
        order.setId("order");
        when(orders.findById("order")).thenReturn(Optional.of(order));
        var c = OrderSaga.startCancellation("order", "op", "payment", "reservation");
        c.setId("cancellation");
        when(database.request("order", "op", "changed mind", "owner", false)).thenReturn(c);
        when(database.claimCarrierAttempt("order", "cancellation")).thenReturn(work);
        return c;
    }

    @Test
    void verifiedCarrierCancellationCanAdvanceFulfillment() {
        fixture();
        when(carrier.cancelPendingShipment("tracking"))
                .thenReturn(new ShipmentCancellationResult("tracking", "CANCELLED", "verified"));
        request();
        verify(database)
                .applyCarrierResult(
                        work, new ShipmentCancellationResult("tracking", "CANCELLED", "verified"));
    }

    @Test
    void carrierUnknownOnlyAssignsReviewAndCannotAdvanceInventory() {
        fixture();
        when(carrier.cancelPendingShipment("tracking"))
                .thenReturn(ShipmentCancellationResult.unknown("tracking", "timeout"));
        request();
        verify(database)
                .applyCarrierResult(
                        work, ShipmentCancellationResult.unknown("tracking", "timeout"));
    }

    @Test
    void carrierHandoffEvidenceCreatesException() {
        fixture();
        when(carrier.cancelPendingShipment("tracking"))
                .thenReturn(
                        new ShipmentCancellationResult("tracking", "HANDED_OFF", "transporting"));
        request();
        verify(database)
                .applyCarrierResult(
                        work,
                        new ShipmentCancellationResult("tracking", "HANDED_OFF", "transporting"));
    }

    @Test
    void alreadyStoppedFulfillmentDoesNotCallCarrierAgain() {
        var c = fixture();
        c.setCurrentStep(OrderSagaStep.CANCEL_ORDER_INVENTORY);
        when(database.claimCarrierAttempt("order", "cancellation")).thenReturn(null);
        request();
        verifyNoInteractions(carrier);
    }

    @Test
    void pendingOrderIsCancelledThroughCheckoutSaga() {
        when(owner.getCurrentUserId()).thenReturn("owner");
        when(orders.findById("order"))
                .thenReturn(
                        Optional.of(
                                Order.builder()
                                        .userId("owner")
                                        .status(OrderStatus.PENDING)
                                        .build()));
        request();
        verify(orderService).cancelMyOrder(eq("order"), any());
        verifyNoInteractions(database, carrier);
    }
}
