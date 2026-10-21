package com.fashionstore.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fashionstore.order.dto.OrderResponse;
import com.fashionstore.order.entity.*;
import com.fashionstore.order.entity.enumeration.*;
import com.fashionstore.order.repository.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

class OrderCancellationViewServiceTest {
    final OrderSagaRepository sagas = mock(OrderSagaRepository.class);
    final OrderStatusHistoryRepository history = mock(OrderStatusHistoryRepository.class);
    final ShipmentRepository shipments = mock(ShipmentRepository.class);

    private OrderResponse project(Order order) {
        return new OrderCancellationViewService(sagas, shipments, history)
                .enrich(order, new OrderResponse());
    }

    private Order order(OrderStatus status) {
        var order = Order.builder().status(status).build();
        order.setId("order");
        return order;
    }

    @Test
    void confirmedOrderCanCancelBeforeHandoff() {
        assertThat(project(order(OrderStatus.CONFIRMED)).isCanCancel()).isEqualTo(true);
    }

    @Test
    void pendingOrderWithoutCheckoutSagaCannotCancel() {
        assertThat(project(order(OrderStatus.PENDING)).isCanCancel()).isEqualTo(false);
    }

    @Test
    void pendingOrderAwaitingPaymentCanCancel() {
        var saga = OrderSaga.start("order");
        saga.inventoryReserved("reservation");
        when(sagas.findByOrderIdAndType("order", OrderSagaType.CHECKOUT)).thenReturn(Optional.of(saga));
        assertThat(project(order(OrderStatus.PENDING)).isCanCancel()).isTrue();
    }

    @Test
    void pendingOrderAlreadyBeingCancelledCannotCancelAgain() {
        var saga = OrderSaga.start("order");
        saga.inventoryReserved("reservation");
        saga.startCompensation(OrderSagaStep.CANCEL_PAYMENT, "CANCELLED_BY_CUSTOMER", "Đổi ý");
        when(sagas.findByOrderIdAndType("order", OrderSagaType.CHECKOUT)).thenReturn(Optional.of(saga));
        assertThat(project(order(OrderStatus.PENDING)).isCanCancel()).isFalse();
    }

    @Test
    void pendingOrderAlreadyPaidCannotCancel() {
        var saga = OrderSaga.start("order");
        saga.inventoryReserved("reservation");
        saga.paymentAuthorized("payment");
        when(sagas.findByOrderIdAndType("order", OrderSagaType.CHECKOUT)).thenReturn(Optional.of(saga));
        assertThat(project(order(OrderStatus.PENDING)).isCanCancel()).isFalse();
    }

    @Test
    void handedOffShipmentCannotOfferCancellation() {
        var order = order(OrderStatus.PACKED);
        when(shipments.findByOrderId("order"))
                .thenReturn(
                        Optional.of(
                                Shipment.builder()
                                        .status(ShipmentStatus.PENDING)
                                        .handedOffAt(Instant.now())
                                        .build()));
        assertThat(project(order).isCanCancel()).isEqualTo(false);
    }

    @Test
    void pendingRefundRemainsPendingInOrderResponse() {
        var c = OrderSaga.startCancellation("order", "op", "payment", "reservation");
        c.setCurrentStep(OrderSagaStep.REFUND_ORDER_FUNDS);
        c.setId("cancellation");
        when(sagas.findByOrderIdAndType("order", OrderSagaType.CANCELLATION))
                .thenReturn(Optional.of(c));
        var response = project(order(OrderStatus.CANCELLING));
        assertThat(response.isCanCancel()).isEqualTo(false);
        var summary = response.getCancellation();
        assertThat(summary.status()).isEqualTo("REFUND_PENDING");
    }

    @Test
    void handoffBeforeCarrierStopIsShownAsRejected() {
        var c = OrderSaga.startCancellation("order", "op", "payment", "reservation");
        c.setId("cancellation");
        c.fail("CARRIER_HANDOFF", "GHN status picked");
        when(sagas.findByOrderIdAndType("order", OrderSagaType.CANCELLATION))
                .thenReturn(Optional.of(c));
        var response = project(order(OrderStatus.SHIPPING));
        assertThat(response.isCanCancel()).isFalse();
        assertThat(response.getCancellation().status()).isEqualTo("REJECTED");
    }

    @Test
    void handoffWhileStillCancellingNeedsFulfillmentReview() {
        var c = OrderSaga.startCancellation("order", "op", "payment", "reservation");
        c.setId("cancellation");
        c.setCurrentStep(OrderSagaStep.CANCEL_ORDER_INVENTORY);
        c.fail("CARRIER_HANDOFF", "GHN status picked");
        when(sagas.findByOrderIdAndType("order", OrderSagaType.CANCELLATION))
                .thenReturn(Optional.of(c));
        var response = project(order(OrderStatus.CANCELLING));
        assertThat(response.getCancellation().status()).isEqualTo("FULFILLMENT_EXCEPTION");
    }

    @Test
    void cancellingOrderCannotOfferCancellationEvenBeforeSagaIsLoaded() {
        assertThat(project(order(OrderStatus.CANCELLING)).isCanCancel()).isFalse();
    }

    @Test void legacyProjectionPreservesInstantsAndDoesNotExposePrivateEvidence() throws Exception {
        var order=order(OrderStatus.CANCELLING);
        var saga=OrderSaga.startCancellation(order.getId(),"op","payment","reservation");
        saga.setCurrentStep(OrderSagaStep.REFUND_ORDER_FUNDS);
        saga.setCreatedAt(java.time.LocalDateTime.parse("2026-10-12T10:00:00"));
        var checkpoint=OrderStatusHistory.builder().order(order).action("CANCELLATION_LEGACY_IMPORTED")
                .detailsJson("""
                    {"fulfillment_stopped_at":"2026-10-12T10:01:00+07:00",
                     "inventory_cancelled_at":"2026-10-12T10:02:00+07:00",
                     "carrier_verified_at":null,"review_due_at":null,"review_owner":null,
                     "carrier_evidence_ids":"private-media-id","requested_by":"private-original-actor"}
                    """).build();
        var response=com.fashionstore.order.dto.OrderCancellationResponse.from(saga,order,java.util.List.of(checkpoint));
        assertThat(response.requestedAt()).isEqualTo(Instant.parse("2026-10-12T03:00:00Z"));
        assertThat(response.fulfillmentStoppedAt()).isEqualTo(Instant.parse("2026-10-12T03:01:00Z"));
        assertThat(response.inventoryCancelledAt()).isEqualTo(Instant.parse("2026-10-12T03:02:00Z"));
        String json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(response);
        assertThat(json).doesNotContain("private-media-id","private-original-actor","detailsJson");
    }
}
