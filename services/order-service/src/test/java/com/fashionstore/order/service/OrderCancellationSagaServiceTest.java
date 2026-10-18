package com.fashionstore.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.contracts.inventory.event.*;
import com.fashionstore.contracts.payment.event.*;
import com.fashionstore.order.dto.*;
import com.fashionstore.order.entity.*;
import com.fashionstore.order.entity.enumeration.*;
import com.fashionstore.order.event.SagaOutbox;
import com.fashionstore.order.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

class OrderCancellationSagaServiceTest {
    final OrderRepository orders = mock(OrderRepository.class);
    final OrderSagaRepository sagas = mock(OrderSagaRepository.class);
    final ShipmentRepository shipments = mock(ShipmentRepository.class);
    final SagaOutbox outbox = mock(SagaOutbox.class);
    final PromotionService promotions = mock(PromotionService.class);
    final OrderStatusHistoryRepository history = mock(OrderStatusHistoryRepository.class);
    final OrderNotificationService notifications = mock(OrderNotificationService.class);
    final OrderCancellationSagaService service =
            new OrderCancellationSagaService(
                    orders,
                    sagas,
                    shipments,
                    outbox,
                    promotions,
                    history,
                    notifications,
                    new ObjectMapper());

    Order order() {
        var order =
                Order.builder()
                        .userId("user-1")
                        .status(OrderStatus.CONFIRMED)
                        .paymentMethod(PaymentMethod.ONLINE)
                        .paymentId("payment-1")
                        .inventoryReservationId("reservation-1")
                        .currency("VND")
                        .subtotalAmount(new BigDecimal("100000"))
                        .totalAmount(new BigDecimal("100000"))
                        .build();
        order.setId("order-1");
        when(orders.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));
        return order;
    }

    OrderSaga cancellation(Order order, OrderSagaStep step) {
        var saga =
                OrderSaga.startCancellation(
                        order.getId(), "operation-1", "payment-1", "reservation-1");
        saga.setCurrentStep(step);
        order.setStatus(OrderStatus.CANCELLING);
        when(sagas.findByIdForUpdate(saga.getId())).thenReturn(Optional.of(saga));
        when(sagas.findByOrderIdAndTypeForUpdate(order.getId(), OrderSagaType.CANCELLATION))
                .thenReturn(Optional.of(saga));
        return saga;
    }

    @Test
    void requestWithoutShipmentBlocksFulfillmentAndEmitsInventoryCancellation() {
        var order = order();
        var checkout = OrderSaga.start(order.getId());
        checkout.setPaymentId("payment-1");
        checkout.setInventoryReservationId("reservation-1");
        checkout.complete();
        when(sagas.findByOrderIdAndTypeForUpdate(order.getId(), OrderSagaType.CHECKOUT))
                .thenReturn(Optional.of(checkout));
        when(sagas.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        var saga = service.request(order.getId(), "operation-1", "Changed mind", "user-1", false);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLING);
        assertThat(saga.getType()).isEqualTo(OrderSagaType.CANCELLATION);
        assertThat(saga.getCurrentStep()).isEqualTo(OrderSagaStep.CANCEL_ORDER_INVENTORY);
        assertThat(checkout.getStatus()).isEqualTo(OrderSagaStatus.COMPLETED);
        verify(outbox).emit(eq(saga), any());
    }

    @Test
    void unknownLabelCreationCannotReleaseStock() {
        var order = order();
        order.setShipmentInitiationToken("creating");
        var saga = cancellation(order, OrderSagaStep.STOP_SHIPMENT);
        assertThat(service.claimCarrierAttempt(order.getId(), saga.getId())).isNull();
        assertThat(saga.getStatus()).isEqualTo(OrderSagaStatus.FAILED);
        assertThat(saga.getFailureCode()).isEqualTo("CARRIER_UNKNOWN");
        verifyNoInteractions(outbox);
    }

    @Test
    void carrierSnapshotCannotApplyToChangedTracking() {
        var order = order();
        var saga = cancellation(order, OrderSagaStep.STOP_SHIPMENT);
        var shipment =
                Shipment.builder()
                        .trackingCode("new-tracking")
                        .status(ShipmentStatus.PENDING)
                        .build();
        shipment.setId("shipment-1");
        when(shipments.findByOrderIdForUpdate(order.getId())).thenReturn(Optional.of(shipment));
        var work =
                new CancellationCarrierWork(
                        order.getId(), saga.getId(), "old-tracking", "shipment-1", null);
        service.applyCarrierResult(
                work, new ShipmentCancellationResult("old-tracking", "CANCELLED", "stopped"));
        assertThat(saga.getStatus()).isEqualTo(OrderSagaStatus.FAILED);
        verifyNoInteractions(outbox);
    }

    @Test
    void carrierHandoffResultRejectsCancellationBeforeCompensationAndDuplicateWebhookIsHarmless() {
        var order = order();
        var saga = cancellation(order, OrderSagaStep.STOP_SHIPMENT);
        order.setCancelReason("Changed mind");
        var shipment = Shipment.builder().order(order).trackingCode("tracking-1")
                .status(ShipmentStatus.PENDING).build();
        shipment.setId("shipment-1");
        when(shipments.findByOrderIdForUpdate(order.getId())).thenReturn(Optional.of(shipment));
        var work = new CancellationCarrierWork(order.getId(), saga.getId(), "tracking-1", "shipment-1", null);
        var result = new ShipmentCancellationResult("tracking-1", "HANDED_OFF", "GHN picked parcel");

        service.applyCarrierResult(work, result);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(order.getCancelReason()).isNull();
        assertThat(saga.getStatus()).isEqualTo(OrderSagaStatus.FAILED);
        assertThat(saga.getFailureCode()).isEqualTo("CARRIER_HANDOFF");
        service.applyCarrierResult(work, result);
        service.lateHandoff(order.getId(), "GHN status picked");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        verify(history, times(1)).save(argThat(row -> "CANCELLATION_REJECTED".equals(row.getAction())));
        verify(notifications, times(1)).sendOrderShippedNotification(order, shipment);
        verifyNoInteractions(outbox, promotions);
    }

    @Test
    void handoffBlocksOrdinaryRetry() {
        var order = order();
        var saga = cancellation(order, OrderSagaStep.STOP_SHIPMENT);
        service.lateHandoff(order.getId(), "Carrier picked up parcel");
        service.retry(order.getId(), saga.getId());
        assertThat(saga.getStatus()).isEqualTo(OrderSagaStatus.FAILED);
        assertThat(saga.getFailureCode()).isEqualTo("CARRIER_HANDOFF");
        verifyNoInteractions(outbox);
    }

    @Test
    void handoffBeforeCarrierStopRejectsCancellationAndResumesShipping() {
        var order = order();
        var saga = cancellation(order, OrderSagaStep.STOP_SHIPMENT);
        order.setCancelReason("Changed mind");
        var shipment = Shipment.builder().order(order).status(ShipmentStatus.PICKED).build();
        when(shipments.findByOrderIdForUpdate(order.getId())).thenReturn(Optional.of(shipment));

        service.lateHandoff(order.getId(), "GHN status picked");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(order.getCancelReason()).isNull();
        assertThat(saga.getStatus()).isEqualTo(OrderSagaStatus.FAILED);
        assertThat(saga.getFailureCode()).isEqualTo("CARRIER_HANDOFF");
        var row = org.mockito.ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(history).save(row.capture());
        assertThat(row.getValue().getAction()).isEqualTo("CANCELLATION_REJECTED");
        assertThat(row.getValue().getFromStatus()).isEqualTo(OrderStatus.CANCELLING);
        assertThat(row.getValue().getToStatus()).isEqualTo(OrderStatus.SHIPPING);
        verify(notifications).sendOrderShippedNotification(order, shipment);
        verifyNoInteractions(outbox);
    }

    @Test
    void handoffAfterInventoryCancellationStaysCancellingForReview() {
        var order = order();
        var saga = cancellation(order, OrderSagaStep.CANCEL_ORDER_INVENTORY);

        service.lateHandoff(order.getId(), "GHN status picked");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLING);
        assertThat(saga.getStatus()).isEqualTo(OrderSagaStatus.FAILED);
        assertThat(saga.getFailureCode()).isEqualTo("CARRIER_HANDOFF");
        verifyNoInteractions(notifications);
    }

    @Test
    void handoffAfterCompletedCancellationDoesNotReopenSaga() {
        var order = order();
        var saga = cancellation(order, OrderSagaStep.REFUND_ORDER_FUNDS);
        saga.complete();
        order.cancel("Changed mind");

        service.lateHandoff(order.getId(), "GHN status picked");

        assertThat(saga.getStatus()).isEqualTo(OrderSagaStatus.COMPLETED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(history).save(any());
    }

    @Test
    void inventoryAcknowledgementEmitsRefundOnce() {
        var order = order();
        var saga = cancellation(order, OrderSagaStep.CANCEL_ORDER_INVENTORY);
        var reply = new OrderInventoryCancelledEvent(saga.getId(), order.getId(), "reservation-1");
        service.inventoryCancelled(reply, saga.getId());
        service.inventoryCancelled(reply, saga.getId());
        assertThat(saga.getCurrentStep()).isEqualTo(OrderSagaStep.REFUND_ORDER_FUNDS);
        verify(outbox, times(1)).emit(eq(saga), any());
    }

    @Test
    void uncollectedCodClosesAfterInventoryWithoutRefund() {
        var order = order();
        order.setPaymentMethod(PaymentMethod.COD);
        var saga = cancellation(order, OrderSagaStep.CANCEL_ORDER_INVENTORY);
        service.inventoryCancelled(
                new OrderInventoryCancelledEvent(saga.getId(), order.getId(), "reservation-1"),
                saga.getId());
        assertThat(saga.getStatus()).isEqualTo(OrderSagaStatus.COMPLETED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verifyNoInteractions(outbox);
        verify(promotions).release(order.getId());
    }

    @Test
    void codCollectionEvidenceRequiresRefund() {
        var order = order();
        order.setPaymentMethod(PaymentMethod.COD);
        var saga = cancellation(order, OrderSagaStep.CANCEL_ORDER_INVENTORY);
        when(shipments.findByOrderIdForUpdate(order.getId()))
                .thenReturn(
                        Optional.of(
                                Shipment.builder()
                                        .status(ShipmentStatus.CANCELLED)
                                        .codStatus("COLLECTED")
                                        .codCollectedAt(Instant.now())
                                        .codAmount(order.getTotalAmount())
                                        .build()));
        service.inventoryCancelled(
                new OrderInventoryCancelledEvent(saga.getId(), order.getId(), "reservation-1"),
                saga.getId());
        assertThat(saga.getCurrentStep()).isEqualTo(OrderSagaStep.REFUND_ORDER_FUNDS);
        verify(outbox).emit(eq(saga), any());
    }

    OrderFundsRefundCreditedEvent credit(OrderSaga saga) {
        return new OrderFundsRefundCreditedEvent(
                saga.getRefundId(),
                "order-1",
                "payment-1",
                "PAID_CANCELLATION",
                saga.getId(),
                "ledger-1",
                new BigDecimal("100000"));
    }

    @Test
    void creditedReplyCompletesAndDuplicateDoesNotNotifyTwice() {
        var order = order();
        var saga = cancellation(order, OrderSagaStep.REFUND_ORDER_FUNDS);
        service.refundCredited(credit(saga), saga.getId());
        service.refundCredited(credit(saga), saga.getId());
        assertThat(saga.getRefundLedgerEntryId()).isEqualTo("ledger-1");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(notifications, times(1)).sendOrderCancelledNotification(eq(order), any());
    }

    @Test
    void lateCreditAfterRetryExhaustedStillCloses() {
        var order = order();
        var saga = cancellation(order, OrderSagaStep.REFUND_ORDER_FUNDS);
        saga.fail("STEP_RETRY_EXHAUSTED", "Timeout");
        service.refundCredited(credit(saga), saga.getId());
        assertThat(saga.getStatus()).isEqualTo(OrderSagaStatus.COMPLETED);
    }

    @Test
    void lateCreditAfterCarrierHandoffStoresReceiptButDoesNotClose() {
        var order = order();
        var saga = cancellation(order, OrderSagaStep.REFUND_ORDER_FUNDS);
        saga.fail("CARRIER_HANDOFF", "Handoff");
        service.refundCredited(credit(saga), saga.getId());
        service.refundCredited(credit(saga), saga.getId());
        assertThat(saga.getRefundLedgerEntryId()).isEqualTo("ledger-1");
        assertThat(saga.getStatus()).isEqualTo(OrderSagaStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLING);
        verifyNoInteractions(notifications, promotions);
    }

    @Test
    void wrongCorrelationCannotCredit() {
        var order = order();
        var saga = cancellation(order, OrderSagaStep.REFUND_ORDER_FUNDS);
        assertThatThrownBy(() -> service.refundCredited(credit(saga), "wrong-saga"))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
        assertThat(saga.getRefundLedgerEntryId()).isNull();
    }

    @Test
    void rejectionAfterCreditCannotUndoCompletion() {
        var order = order();
        var saga = cancellation(order, OrderSagaStep.REFUND_ORDER_FUNDS);
        service.refundCredited(credit(saga), saga.getId());
        service.refundRejected(
                new OrderFundsRefundRejectedEvent(
                        saga.getRefundId(),
                        order.getId(),
                        "payment-1",
                        "PAID_CANCELLATION",
                        saga.getId(),
                        "REJECTED",
                        "Late reply"),
                saga.getId());
        assertThat(saga.getStatus()).isEqualTo(OrderSagaStatus.COMPLETED);
    }

    @Test
    void wrongPaymentAmountAndLedgerCannotOverwriteReceipt() {
        var order = order();
        var saga = cancellation(order, OrderSagaStep.REFUND_ORDER_FUNDS);
        var invalidAmount =
                new OrderFundsRefundCreditedEvent(
                        saga.getRefundId(),
                        order.getId(),
                        "payment-1",
                        "PAID_CANCELLATION",
                        saga.getId(),
                        "ledger-1",
                        BigDecimal.ONE);
        assertThatThrownBy(() -> service.refundCredited(invalidAmount, saga.getId()))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
        var invalidPayment =
                new OrderFundsRefundCreditedEvent(
                        saga.getRefundId(),
                        order.getId(),
                        "wrong-payment",
                        "PAID_CANCELLATION",
                        saga.getId(),
                        "ledger-1",
                        order.getTotalAmount());
        assertThatThrownBy(() -> service.refundCredited(invalidPayment, saga.getId()))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
        service.refundCredited(credit(saga), saga.getId());
        var conflictingLedger =
                new OrderFundsRefundCreditedEvent(
                        saga.getRefundId(),
                        order.getId(),
                        "payment-1",
                        "PAID_CANCELLATION",
                        saga.getId(),
                        "ledger-2",
                        order.getTotalAmount());
        assertThatThrownBy(() -> service.refundCredited(conflictingLedger, saga.getId()))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
        assertThat(saga.getRefundLedgerEntryId()).isEqualTo("ledger-1");
    }

    @Test
    void wrongReservationAndCheckoutSagaCannotAdvanceInventory() {
        var order = order();
        var saga = cancellation(order, OrderSagaStep.CANCEL_ORDER_INVENTORY);
        assertThatThrownBy(
                        () ->
                                service.inventoryCancelled(
                                        new OrderInventoryCancelledEvent(
                                                saga.getId(), order.getId(), "wrong-reservation"),
                                        saga.getId()))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
        saga.setType(OrderSagaType.CHECKOUT);
        assertThatThrownBy(
                        () ->
                                service.inventoryCancelled(
                                        new OrderInventoryCancelledEvent(
                                                saga.getId(), order.getId(), "reservation-1"),
                                        saga.getId()))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
        verifyNoInteractions(outbox);
    }

    @Test
    void manualNoShipmentProofKeepsOriginalClaimAndVerificationReplayIsStrict() {
        var order = order();
        order.setShipmentInitiationToken("creation-token");
        order.setShipmentInitiationStartedAt(Instant.now().minusSeconds(200));
        var saga = cancellation(order, OrderSagaStep.STOP_SHIPMENT);
        var input =
                new VerifyCancellationCarrierRequest(
                        "verify-1",
                        "Carrier confirmed no shipment",
                        true,
                        true,
                        true,
                        null,
                        java.util.List.of("media-1"));
        service.verifyCarrier(order.getId(), saga.getId(), input, "admin-1");
        assertThat(order.getShipmentInitiationToken()).isEqualTo("creation-token");
        assertThat(saga.getCurrentStep()).isEqualTo(OrderSagaStep.CANCEL_ORDER_INVENTORY);
        var captured = org.mockito.ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(history, times(2)).save(captured.capture());
        when(history.findByOrderIdOrderByCreatedAtDescIdDesc(order.getId()))
                .thenReturn(captured.getAllValues());
        service.verifyCarrier(order.getId(), saga.getId(), input, "admin-1");
        assertThatThrownBy(
                        () -> service.verifyCarrier(order.getId(), saga.getId(), input, "admin-2"))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
        verify(outbox, times(1)).emit(eq(saga), any());
    }

    @Test
    void noShipmentProofMustWaitForCreationUncertaintyWindow() {
        var order = order();
        order.setShipmentInitiationToken("creation-token");
        order.setShipmentInitiationStartedAt(Instant.now());
        var saga = cancellation(order, OrderSagaStep.STOP_SHIPMENT);
        var input =
                new VerifyCancellationCarrierRequest(
                        "verify-1",
                        "No shipment",
                        true,
                        true,
                        true,
                        null,
                        java.util.List.of("media-1"));
        assertThatThrownBy(
                        () -> service.verifyCarrier(order.getId(), saga.getId(), input, "admin-1"))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
        verifyNoInteractions(outbox);
    }
}
