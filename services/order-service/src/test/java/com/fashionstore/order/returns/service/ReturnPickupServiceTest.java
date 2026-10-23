package com.fashionstore.order.returns.service;

import com.fashionstore.order.order.service.OrderNotificationService;
import com.fashionstore.order.order.service.OrderReceiptService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.order.shipment.client.GhnClient;
import com.fashionstore.order.shipment.config.GhnProperties;
import com.fashionstore.order.returns.config.ReturnProperties;
import com.fashionstore.order.shipment.dto.GhnWebhookPayload;
import com.fashionstore.order.shipment.dto.VerifiedGhnShipment;
import com.fashionstore.order.order.entity.Order;
import com.fashionstore.order.order.entity.OrderStatusHistory;
import com.fashionstore.order.returns.entity.ReturnRequest;
import com.fashionstore.order.returns.entity.ReturnShipment;
import com.fashionstore.order.order.entity.OrderStatus;
import com.fashionstore.order.returns.entity.ReturnRequestStatus;
import com.fashionstore.order.returns.entity.ReturnShipmentStatus;
import com.fashionstore.order.shared.exception.OrderErrorCode;
import com.fashionstore.order.order.repository.OrderRepository;
import com.fashionstore.order.order.repository.OrderStatusHistoryRepository;
import com.fashionstore.order.returns.repository.ReturnRequestRepository;
import com.fashionstore.order.returns.repository.ReturnShipmentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** Webhook GHN chiều trả: không tin nội dung webhook, trạng thái thật đọc lại từ API GHN. */
class ReturnPickupServiceTest {

    static final Instant NOW = Instant.parse("2026-10-12T01:00:00Z");

    final ReturnShipmentRepository shipments = mock(ReturnShipmentRepository.class);
    final ReturnRequestRepository returns = mock(ReturnRequestRepository.class);
    final OrderRepository orders = mock(OrderRepository.class);
    final OrderStatusHistoryRepository history = mock(OrderStatusHistoryRepository.class);
    final GhnClient ghn = mock(GhnClient.class);
    final OrderReceiptService receipts = mock(OrderReceiptService.class);
    final OrderNotificationService notifications = mock(OrderNotificationService.class);
    final com.fashionstore.common.security.CurrentUserProvider currentUser =
            mock(com.fashionstore.common.security.CurrentUserProvider.class);
    final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    final GhnProperties ghnProperties = new GhnProperties();
    ReturnPickupService service;
    Order order;
    ReturnRequest returned;
    ReturnShipment shipment;

    @BeforeEach
    void setUp() {
        ghnProperties.setShopId("42");
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service =
                new ReturnPickupService(
                        shipments,
                        returns,
                        orders,
                        history,
                        ghn,
                        ghnProperties,
                        receipts,
                        notifications,
                        transactions,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        meters,
                        new ReturnProperties(),
                        currentUser);
        when(currentUser.getCurrentUserId()).thenReturn("user-1");
        order = Order.builder().userId("user-1").status(OrderStatus.DELIVERED).orderCode("ORD-1").build();
        order.setId("ord-1");
        returned =
                ReturnRequest.builder()
                        .order(order)
                        .userId("user-1")
                        .reason("Sai size")
                        .status(ReturnRequestStatus.APPROVED)
                        .approvedAt(NOW.minusSeconds(3600))
                        .build();
        returned.setId("ret-1");
        shipment =
                ReturnShipment.builder().returnRequest(returned).ghnOrderCode("GHN-RET-1").build();
        shipment.setId("rs-1");
        when(shipments.findReturnRequestIdByGhnOrderCode("GHN-RET-1")).thenReturn(Optional.of("ret-1"));
        when(returns.findOrderIdByReturnId("ret-1")).thenReturn(Optional.of("ord-1"));
        when(orders.findByIdForUpdate("ord-1")).thenReturn(Optional.of(order));
        when(returns.findByIdForUpdate("ret-1")).thenReturn(Optional.of(returned));
        when(shipments.findByReturnRequestIdForUpdate("ret-1")).thenReturn(Optional.of(shipment));
        when(returns.findByOrderId("ord-1")).thenReturn(Optional.of(returned));
    }

    private GhnWebhookPayload callback(String status) {
        return GhnWebhookPayload.builder()
                .shopId("42")
                .orderCode("GHN-RET-1")
                .clientOrderCode("RET-ret-1")
                .type("switch_status")
                .status(status)
                .time(NOW.toString())
                .build();
    }

    private void ghnSays(String status) {
        ghnSays("GHN-RET-1", "42", "RET-ret-1", status);
    }

    private void ghnSays(String code, String shop, String clientCode, String status) {
        when(ghn.getVerifiedShipment("GHN-RET-1"))
                .thenReturn(
                        new VerifiedGhnShipment(
                                code, shop, clientCode, status, BigDecimal.ZERO, false, null, false, null));
    }

    private List<String> actions() {
        ArgumentCaptor<OrderStatusHistory> saved = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(history, org.mockito.Mockito.atLeast(0)).save(saved.capture());
        return saved.getAllValues().stream().map(OrderStatusHistory::getAction).toList();
    }

    @Test
    void aCodeThatIsNotAReturnShipmentIsLeftToTheOutboundHandler() {
        when(shipments.findReturnRequestIdByGhnOrderCode("GHN-OUT-1")).thenReturn(Optional.empty());

        boolean handled =
                service.handleGhnCallback(
                        GhnWebhookPayload.builder().shopId("42").orderCode("GHN-OUT-1").status("picked").build());

        assertThat(handled).isFalse();
        verifyNoInteractions(ghn);
    }

    @Test
    void pickedLocksTheReturnInTransitAndTellsTheCustomer() {
        ghnSays("picked");

        assertThat(service.handleGhnCallback(callback("picked"))).isTrue();

        assertThat(shipment.getStatus()).isEqualTo(ReturnShipmentStatus.PICKED);
        assertThat(shipment.getPickedAt()).isEqualTo(NOW);
        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.IN_TRANSIT);
        assertThat(actions()).containsExactly("RETURN_PICKED_UP");
        verify(notifications).sendReturnStatusNotification(eq(order), anyString(), anyString());
    }

    @Test
    void theStateIsReadFromGhnNotFromTheWebhookBody() {
        ghnSays("ready_to_pick"); // webhook claims picked, GHN says otherwise

        service.handleGhnCallback(callback("picked"));

        assertThat(shipment.getStatus()).isEqualTo(ReturnShipmentStatus.READY_TO_PICK);
        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.APPROVED);
        verify(history, never()).save(any());
    }

    @Test
    void aCallbackWhoseGhnRecordBelongsToAnotherOrderIsRejected() {
        ghnSays("GHN-RET-1", "42", "RET-other", "picked");

        assertThatThrownBy(() -> service.handleGhnCallback(callback("picked")))
                .isInstanceOfSatisfying(
                        AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(OrderErrorCode.SHIPMENT_EVIDENCE_INVALID));
        assertThat(shipment.getStatus()).isEqualTo(ReturnShipmentStatus.READY_TO_PICK);
    }

    @Test
    void aCallbackForAnotherShopIsRejectedBeforeCallingGhn() {
        GhnWebhookPayload other = callback("picked");
        other.setShopId("99");

        assertThatThrownBy(() -> service.handleGhnCallback(other))
                .isInstanceOfSatisfying(
                        AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(OrderErrorCode.SHIPMENT_EVIDENCE_INVALID));
        verifyNoInteractions(ghn);
    }

    @Test
    void ghnBeingUnreachableFailsTheCallbackSoGhnRetriesItLater() {
        when(ghn.getVerifiedShipment("GHN-RET-1")).thenThrow(new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR));

        assertThatThrownBy(() -> service.handleGhnCallback(callback("picked"))).isInstanceOf(AppException.class);
        assertThat(shipment.getStatus()).isEqualTo(ReturnShipmentStatus.READY_TO_PICK);
    }

    @Test
    void aRepeatedPickedCallbackChangesNothingTheSecondTime() {
        ghnSays("picked");

        service.handleGhnCallback(callback("picked"));
        service.handleGhnCallback(callback("picked"));

        assertThat(actions()).containsExactly("RETURN_PICKED_UP");
        verify(notifications, org.mockito.Mockito.times(1)).sendReturnStatusNotification(any(), any(), any());
    }

    @Test
    void ghnCancellingBeforePickupKeepsTheReturnOpenForTheAdminToDecide() {
        ghnSays("cancel");
        GhnWebhookPayload cancelled = callback("cancel");
        cancelled.setReason("Khách không nghe máy");

        service.handleGhnCallback(cancelled);

        assertThat(shipment.getStatus()).isEqualTo(ReturnShipmentStatus.PICKUP_FAILED);
        assertThat(shipment.getCancelledAt()).isEqualTo(NOW);
        assertThat(shipment.getFailureCode()).isEqualTo("CARRIER_CANCELLED");
        assertThat(shipment.getFailureReason()).contains("Khách không nghe máy");
        assertThat(shipment.getFailedAt()).isEqualTo(NOW);
        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.APPROVED);
        assertThat(returned.getClosedAt()).isNull();
        assertThat(actions()).containsExactly("RETURN_PICKUP_FAILED");
        verify(receipts, never()).completeAfterReturnClosed(any(), any(), any(), any());
        verify(notifications).sendReturnStatusNotification(eq(order), anyString(), anyString());
    }

    @Test
    void aCallbackForARecreatedPickupAcceptsItsSuffixedClientCode() {
        ghnSays("GHN-RET-1", "42", "RET-ret-1-GHN-OLD", "picked");

        service.handleGhnCallback(callback("picked"));

        assertThat(shipment.getStatus()).isEqualTo(ReturnShipmentStatus.PICKED);
    }

    @Test
    void ghnCancellingAfterThePickupIsAnAnomalyNotAClosure() {
        shipment.setStatus(ReturnShipmentStatus.PICKED);
        returned.setStatus(ReturnRequestStatus.IN_TRANSIT);
        ghnSays("cancel");

        service.handleGhnCallback(callback("cancel"));

        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.IN_TRANSIT);
        assertThat(returned.getClosedAt()).isNull();
        assertThat(actions()).containsExactly("RETURN_SHIPMENT_ATTENTION");
        verify(receipts, never()).completeAfterReturnClosed(any(), any(), any(), any());
    }

    @Test
    void arrivalAtTheWarehouseIsRecordedButTheAdminStillMustReceiveAndInspect() {
        ghnSays("delivered");

        service.handleGhnCallback(callback("delivered"));

        assertThat(shipment.getStatus()).isEqualTo(ReturnShipmentStatus.ARRIVED);
        assertThat(shipment.getArrivedAt()).isEqualTo(NOW);
        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.IN_TRANSIT);
        assertThat(actions()).containsExactly("RETURN_PICKED_UP", "RETURN_ARRIVED_AT_WAREHOUSE");
    }

    @Test
    void aFailedPickupAttemptIsReportedToTheCustomerWithoutChangingState() {
        ghnSays("ready_to_pick");
        GhnWebhookPayload attempt = callback("ready_to_pick");
        attempt.setReason("Khách hẹn lại");

        service.handleGhnCallback(attempt);

        assertThat(shipment.getStatus()).isEqualTo(ReturnShipmentStatus.READY_TO_PICK);
        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.APPROVED);
        assertThat(actions()).containsExactly("RETURN_PICKUP_ATTEMPT_FAILED");
        verify(notifications).sendReturnStatusNotification(eq(order), anyString(), contains("Khách hẹn lại"));
    }

    @Test
    void readyToPickWithoutAReasonIsJustTheCreationEcho() {
        ghnSays("ready_to_pick");

        service.handleGhnCallback(callback("ready_to_pick"));

        verify(history, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void failuresOnTheReturnLegNeedAnAdminAndNeverCloseTheReturn() {
        shipment.setStatus(ReturnShipmentStatus.PICKED);
        returned.setStatus(ReturnRequestStatus.IN_TRANSIT);
        ghnSays("returned");

        service.handleGhnCallback(callback("returned"));

        assertThat(shipment.getStatus()).isEqualTo(ReturnShipmentStatus.NEEDS_ATTENTION);
        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.IN_TRANSIT);
        assertThat(returned.getClosedAt()).isNull();
        assertThat(actions()).containsExactly("RETURN_SHIPMENT_ATTENTION");
        assertThat(meters.counter("order.return_shipment.attention", "status", "returned").count())
                .isEqualTo(1.0);
        verify(receipts, never()).completeAfterReturnClosed(any(), any(), any(), any());
    }

    @Test
    void aLatePickedEventForAReturnThatWasAlreadyClosedIsRecordedNotReopened() {
        returned.setStatus(ReturnRequestStatus.CANCELLED);
        returned.setClosedAt(NOW.minusSeconds(60));
        ghnSays("picked");

        service.handleGhnCallback(callback("picked"));

        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.CANCELLED);
        // Có gói hàng đang di chuyển trong khi phiếu đã đóng: admin phải biết.
        assertThat(shipment.getStatus()).isEqualTo(ReturnShipmentStatus.NEEDS_ATTENTION);
        assertThat(actions()).containsExactly("RETURN_SHIPMENT_ATTENTION");
    }

    // ----- hủy phiếu: admin -----

    private void ghnCancels(String outcome) {
        when(ghn.cancelPendingShipment("GHN-RET-1"))
                .thenReturn(new com.fashionstore.order.shipment.dto.ShipmentCancellationResult("GHN-RET-1", outcome, outcome));
    }

    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, com.fashionstore.common.exception.BaseErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test
    void anAdminCancelsBeforeApprovalWithoutTouchingGhn() {
        returned.setStatus(ReturnRequestStatus.REQUESTED);
        when(shipments.findByReturnRequestIdForUpdate("ret-1")).thenReturn(Optional.empty());

        service.cancelByAdmin("ret-1", "Admin cancellation");

        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.CANCELLED);
        assertThat(returned.getClosedAt()).isEqualTo(NOW);
        assertThat(actions()).containsExactly("RETURN_CANCELLED");
        verify(receipts).completeAfterReturnClosed(eq(order), eq(NOW), eq("user-1"), anyString());
        verifyNoInteractions(ghn);
    }

    @Test
    void anAdminCancelsAFailedPickupWithoutCallingGhnAgain() {
        shipment.setStatus(ReturnShipmentStatus.PICKUP_FAILED);

        service.cancelByAdmin("ret-1", "Khách không còn muốn trả");

        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.CANCELLED);
        assertThat(returned.getClosedAt()).isEqualTo(NOW);
        verify(ghn, never()).cancelPendingShipment(any());
        verify(receipts).completeAfterReturnClosed(eq(order), eq(NOW), eq("user-1"), contains("Khách không còn muốn trả"));
    }

    @Test
    void anAdminCancelsAnApprovedReturnOnlyOnceGhnConfirmsTheCancellation() {
        ghnCancels("CANCELLED");

        service.cancelByAdmin("ret-1", "Admin cancellation");

        verify(ghn).cancelPendingShipment("GHN-RET-1");
        assertThat(shipment.getStatus()).isEqualTo(ReturnShipmentStatus.CANCELLED);
        assertThat(shipment.getCancelledAt()).isEqualTo(NOW);
        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.CANCELLED);
        verify(receipts).completeAfterReturnClosed(eq(order), eq(NOW), eq("user-1"), anyString());
    }

    @Test
    void ifGhnAlreadyPickedTheParcelUpTheCancellationIsRefusedAndTheReturnIsLocked() {
        ghnCancels("HANDED_OFF");

        assertConflict(() -> service.cancelByAdmin("ret-1", "Admin cancellation"), OrderErrorCode.RETURN_WORKFLOW_CONFLICT);

        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.IN_TRANSIT);
        assertThat(shipment.getStatus()).isEqualTo(ReturnShipmentStatus.PICKED);
        verify(receipts, never()).completeAfterReturnClosed(any(), any(), any(), any());
    }

    @Test
    void anUnconfirmedGhnCancellationKeepsEverythingAsItWas() {
        ghnCancels("UNKNOWN");

        assertConflict(() -> service.cancelByAdmin("ret-1", "Admin cancellation"), ErrorCode.UPSTREAM_SERVICE_ERROR);

        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.APPROVED);
        assertThat(shipment.getStatus()).isEqualTo(ReturnShipmentStatus.READY_TO_PICK);
    }

    @Test
    void onceTheParcelIsInTransitTheAdminCannotCancel() {
        returned.setStatus(ReturnRequestStatus.IN_TRANSIT);
        shipment.setStatus(ReturnShipmentStatus.PICKED);

        assertConflict(() -> service.cancelByAdmin("ret-1", "Admin cancellation"), OrderErrorCode.RETURN_WORKFLOW_CONFLICT);

        verifyNoInteractions(ghn);
    }

    @Test
    void cancellingTwiceIsIdempotent() {
        returned.setStatus(ReturnRequestStatus.CANCELLED);
        returned.setClosedAt(NOW.minusSeconds(5));

        service.cancelByAdmin("ret-1", "Admin cancellation");

        verifyNoInteractions(ghn);
        verify(history, never()).save(any());
        verify(receipts, never()).completeAfterReturnClosed(any(), any(), any(), any());
    }

    @Test
    void anAdminCancelsAnApprovedReturnThroughGhnToo() {
        when(currentUser.getCurrentUserId()).thenReturn("admin-1");
        ghnCancels("CANCELLED");

        service.cancelByAdmin("ret-1", "Khách yêu cầu qua CSKH");

        verify(ghn).cancelPendingShipment("GHN-RET-1");
        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.CANCELLED);
        assertThat(returned.getRejectReason()).contains("Khách yêu cầu qua CSKH");
        verify(receipts).completeAfterReturnClosed(eq(order), eq(NOW), eq("admin-1"), contains("Khách yêu cầu qua CSKH"));
    }

    @Test
    void ifTheParcelWasPickedWhileGhnWasBeingAskedToCancelNothingIsClosed() {
        when(ghn.cancelPendingShipment("GHN-RET-1"))
                .thenAnswer(
                        call -> {
                            returned.setStatus(ReturnRequestStatus.IN_TRANSIT);
                            shipment.setStatus(ReturnShipmentStatus.PICKED);
                            return new com.fashionstore.order.shipment.dto.ShipmentCancellationResult("GHN-RET-1", "CANCELLED", "ok");
                        });

        assertConflict(() -> service.cancelByAdmin("ret-1", "Admin cancellation"), OrderErrorCode.RETURN_WORKFLOW_CONFLICT);

        assertThat(returned.getClosedAt()).isNull();
        verify(receipts, never()).completeAfterReturnClosed(any(), any(), any(), any());
    }

    @Test
    void theSameAttentionStatusReportedAgainDoesNotSpamTheHistoryOrTheMetric() {
        shipment.setStatus(ReturnShipmentStatus.PICKED);
        returned.setStatus(ReturnRequestStatus.IN_TRANSIT);
        ghnSays("returned");

        service.handleGhnCallback(callback("returned"));
        service.handleGhnCallback(callback("returned"));

        assertThat(actions()).containsExactly("RETURN_SHIPMENT_ATTENTION");
        assertThat(meters.counter("order.return_shipment.attention", "status", "returned").count())
                .isEqualTo(1.0);
    }

    // ----- timeout, đối soát, chỉ số -----

    private void overdue() {
        when(shipments.findPickupOverdueReturnIds(any(), org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenReturn(List.of("ret-1"));
        when(shipments.findByReturnRequestId("ret-1")).thenReturn(Optional.of(shipment));
    }

    @Test
    void anOverduePickupIsCancelledAtGhnAndLeftOpenForTheAdmin() {
        overdue();
        ghnCancels("CANCELLED");

        service.expirePickups(NOW);

        assertThat(shipment.getStatus()).isEqualTo(ReturnShipmentStatus.PICKUP_FAILED);
        assertThat(shipment.getFailureCode()).isEqualTo("PICKUP_FAILED");
        assertThat(shipment.getFailureReason()).contains("quá hạn");
        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.APPROVED);
        assertThat(returned.getClosedAt()).isNull();
        verify(receipts, never()).completeAfterReturnClosed(any(), any(), any(), any());
    }

    @Test
    void anUnconfirmedGhnCancellationIsRetriedLaterAndCountedNeverClosedOrThrown() {
        overdue();
        ghnCancels("UNKNOWN");

        service.expirePickups(NOW);

        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.APPROVED);
        assertThat(shipment.getCancelAttempts()).isEqualTo(1);
        verify(receipts, never()).completeAfterReturnClosed(any(), any(), any(), any());
    }

    @Test
    void ifGhnHadAlreadyPickedTheParcelUpTheOverdueReturnMovesInTransitInsteadOfCancelling() {
        overdue();
        ghnCancels("HANDED_OFF");

        service.expirePickups(NOW);

        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.IN_TRANSIT);
        assertThat(shipment.getCancelAttempts()).isZero();
        verify(receipts, never()).completeAfterReturnClosed(any(), any(), any(), any());
    }

    private void open() {
        when(shipments.findOpenForReconcile(any(), any())).thenReturn(List.of("ret-1"));
        when(shipments.findByReturnRequestId("ret-1")).thenReturn(Optional.of(shipment));
    }

    @Test
    void reconcileRecoversAWebhookThatWasLost() {
        open();
        ghnSays("picked");

        service.reconcile(NOW);

        assertThat(shipment.getStatus()).isEqualTo(ReturnShipmentStatus.PICKED);
        assertThat(returned.getStatus()).isEqualTo(ReturnRequestStatus.IN_TRANSIT);
        assertThat(shipment.getLastReconciledAt()).isEqualTo(NOW);
    }

    @Test
    void reconcileSurvivesGhnBeingDownAndStillSpacesTheNextAttempt() {
        open();
        when(ghn.getVerifiedShipment("GHN-RET-1")).thenThrow(new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR));

        service.reconcile(NOW);

        assertThat(shipment.getStatus()).isEqualTo(ReturnShipmentStatus.READY_TO_PICK);
        assertThat(shipment.getLastReconciledAt()).isEqualTo(NOW);
    }

    @Test
    void reconcileIgnoresAGhnRecordThatDoesNotBelongToThisReturn() {
        open();
        ghnSays("GHN-RET-1", "42", "RET-someone-else", "picked");

        service.reconcile(NOW);

        assertThat(shipment.getStatus()).isEqualTo(ReturnShipmentStatus.READY_TO_PICK);
    }

    @Test
    void theSlaGaugesCountWhatNeedsAnAdmin() {
        when(returns.countAwaitingReviewSince(any())).thenReturn(2L);
        when(shipments.countInTransitSince(any())).thenReturn(3L);
        when(shipments.countArrivedUnreceivedSince(any())).thenReturn(4L);
        when(shipments.countNeedingAttention()).thenReturn(5L);
        when(shipments.countPickupCancelExhausted(org.mockito.ArgumentMatchers.anyInt(), any())).thenReturn(6L);

        service.publishSlaGauges(NOW);

        assertThat(meters.get("order.return.requested.overdue").gauge().value()).isEqualTo(2.0);
        assertThat(meters.get("order.return.in_transit.stale").gauge().value()).isEqualTo(3.0);
        assertThat(meters.get("order.return.arrived.unreceived").gauge().value()).isEqualTo(4.0);
        assertThat(meters.get("order.return_shipment.needs_attention").gauge().value()).isEqualTo(5.0);
        assertThat(meters.get("order.return.pickup_cancel.exhausted").gauge().value()).isEqualTo(6.0);
    }
}
