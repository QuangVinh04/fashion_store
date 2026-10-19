package com.fashionstore.order.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.contracts.inventory.event.OrderInventoryCancellationRejectedEvent;
import com.fashionstore.contracts.inventory.event.OrderInventoryCancelledEvent;
import com.fashionstore.contracts.payment.event.OrderFundsRefundCreditedEvent;
import com.fashionstore.contracts.payment.event.OrderFundsRefundRejectedEvent;
import com.fashionstore.order.dto.CancellationCarrierWork;
import com.fashionstore.order.dto.ShipmentCancellationResult;
import com.fashionstore.order.dto.VerifyCancellationCarrierRequest;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderSaga;
import com.fashionstore.order.entity.OrderStatusHistory;
import com.fashionstore.order.entity.Shipment;
import com.fashionstore.order.entity.enumeration.OrderSagaStatus;
import com.fashionstore.order.entity.enumeration.OrderSagaStep;
import com.fashionstore.order.entity.enumeration.OrderSagaType;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.entity.enumeration.ShipmentStatus;
import com.fashionstore.order.event.SagaOutbox;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.OrderSagaRepository;
import com.fashionstore.order.repository.OrderStatusHistoryRepository;
import com.fashionstore.order.repository.ShipmentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * Service điều phối quy trình Saga hủy đơn hàng (Order Cancellation Saga).
 * Thứ tự khóa Pessimistic Locking: Order → OrderSaga → Shipment.
 */
@Service
@RequiredArgsConstructor
public class OrderCancellationSagaService {

    private final OrderRepository orders;
    private final OrderSagaRepository sagas;
    private final ShipmentRepository shipments;
    private final SagaOutbox outbox;
    private final PromotionService promotions;
    private final OrderStatusHistoryRepository history;
    private final OrderNotificationService notifications;
    private final ObjectMapper mapper;

    private record SagaContext(Order order, OrderSaga saga) {}

    // =========================================================================
    // 1. KHỞI TẠO TIẾN TRÌNH HỦY ĐƠN (CANCELLATION REQUEST)
    // =========================================================================

    @Transactional
    public OrderSaga request(
            String orderId, String operationId, String reason, String actor, boolean isAdmin) {

        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!isAdmin && !Objects.equals(order.getUserId(), actor)) {
            throw new AppException(OrderErrorCode.ORDER_NOT_FOUND);
        }

        if (operationId == null || operationId.isBlank()) {
            throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
        }

        // Idempotency: Nếu request cùng operationId đã tồn tại thì trả về saga hiện có
        OrderSaga existingSaga = sagas.findByOrderIdAndTypeForUpdate(orderId, OrderSagaType.CANCELLATION).orElse(null);
        if (existingSaga != null) {
            if (!Objects.equals(existingSaga.getRequestOperationId(), operationId)
                    || !Objects.equals(order.getCancelReason(), reason)) {
                throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
            }
            return existingSaga;
        }

        // Chỉ cho phép hủy khi đơn ở các trạng thái hợp lệ
        if (!Set.of(OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.PACKED).contains(order.getStatus())) {
            throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
        }

        // Kiểm tra Checkout Saga đã hoàn tất; ID giữ kho và thanh toán lấy từ saga này
        OrderSaga checkoutSaga = sagas.findByOrderIdAndTypeForUpdate(orderId, OrderSagaType.CHECKOUT).orElse(null);
        if (checkoutSaga == null || checkoutSaga.getStatus() != OrderSagaStatus.COMPLETED) {
            throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
        }

        Shipment shipment = shipments.findByOrderIdForUpdate(orderId).orElse(null);
        if (isShipmentHandedOff(shipment)) {
            throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
        }

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLING);
        order.setCancelReason(reason);

        OrderSaga cancellationSaga = sagas.saveAndFlush(
                OrderSaga.startCancellation(orderId, operationId, checkoutSaga.getPaymentId(), checkoutSaga.getInventoryReservationId()));

        recordHistory(order, previousStatus, OrderStatus.CANCELLING, "CANCELLATION_REQUESTED", actor, reason, null);

        // Nếu đơn chưa tạo vận đơn GHN: Bỏ qua bước 1, nhảy thẳng sang bước 2 (nhả kho)
        boolean hasNoTrackingCode = order.getTrackingCode() == null || order.getTrackingCode().isBlank();
        if (order.getShipmentInitiationToken() == null
                && (shipment == null && hasNoTrackingCode || shipment != null && shipment.getStatus() == ShipmentStatus.CANCELLED)) {
            recordHistory(order, order.getStatus(), order.getStatus(), "CANCELLATION_CARRIER_STOPPED", "SYSTEM", "No active carrier shipment", null);
            transitionToInventoryCancellation(cancellationSaga);
        }

        return cancellationSaga;
    }

    // =========================================================================
    // 2. BƯỚC 1: DỪNG VẬN ĐƠN (STOP_SHIPMENT)
    // =========================================================================

    @Transactional
    public CancellationCarrierWork claimCarrierAttempt(String orderId, String sagaId) {
        SagaContext context = lockOrderAndSaga(orderId, sagaId);
        Order order = context.order();
        OrderSaga saga = context.saga();

        if (saga.getStatus() != OrderSagaStatus.RUNNING || saga.getCurrentStep() != OrderSagaStep.STOP_SHIPMENT) {
            return null;
        }

        Shipment shipment = shipments.findByOrderIdForUpdate(orderId).orElse(null);
        if (isShipmentHandedOff(shipment)) {
            failCarrierHandoff(order, saga, "Carrier handoff discovered during cancellation");
            return null;
        }

        if (order.getShipmentInitiationToken() != null
                || shipment == null
                || shipment.getTrackingCode() == null
                || shipment.getTrackingCode().isBlank()) {
            saga.fail("CARRIER_UNKNOWN", "Carrier label creation/tracking requires manual verification");
            return null;
        }

        return new CancellationCarrierWork(orderId, sagaId, shipment.getTrackingCode(), shipment.getId(), null);
    }

    @Transactional
    public void applyCarrierResult(CancellationCarrierWork work, ShipmentCancellationResult result) {
        SagaContext context = lockOrderAndSaga(work.orderId(), work.sagaId());
        Order order = context.order();
        OrderSaga saga = context.saga();

        if (saga.getStatus() != OrderSagaStatus.RUNNING || saga.getCurrentStep() != OrderSagaStep.STOP_SHIPMENT) {
            return;
        }

        Shipment shipment = shipments.findByOrderIdForUpdate(order.getId()).orElse(null);
        if (isShipmentHandedOff(shipment)
                || (result != null && "HANDED_OFF".equals(result.outcome()) && Objects.equals(work.trackingCode(), result.trackingCode()))) {
            rejectCancellationBeforeCarrierStop(order, saga, shipment,
                    "Carrier handoff confirmed during cancellation");
            return;
        }

        if (order.getShipmentInitiationToken() != null
                || shipment == null
                || !Objects.equals(shipment.getId(), work.shipmentId())
                || !Objects.equals(shipment.getTrackingCode(), work.trackingCode())
                || result == null
                || !Objects.equals(work.trackingCode(), result.trackingCode())) {
            saga.fail("CARRIER_UNKNOWN", "Carrier cancellation does not match current shipment");
            return;
        }

        if (!"CANCELLED".equals(result.outcome())) {
            saga.setFailureCode("CARRIER_RETRY_PENDING");
            saga.setFailureReason(result.reason() != null ? result.reason() : "Carrier outcome unknown");
            return;
        }

        shipment.setStatus(ShipmentStatus.CANCELLED);
        recordHistory(order, order.getStatus(), order.getStatus(), "CANCELLATION_CARRIER_STOPPED", "CARRIER", result.reason(), toJson(result));
        transitionToInventoryCancellation(saga);
    }

    @Transactional
    public void verifyCarrier(String orderId, String sagaId, VerifyCancellationCarrierRequest input, String actor) {
        SagaContext context = lockOrderAndSaga(orderId, sagaId);
        Order order = context.order();
        OrderSaga saga = context.saga();

        if (input == null || !Boolean.TRUE.equals(input.carrierCancelled()) || !Boolean.TRUE.equals(input.noHandoff())) {
            throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
        }

        String payloadJson = toJson(input);
        for (OrderStatusHistory historyEntry : history.findByOrderIdOrderByCreatedAtDescIdDesc(orderId)) {
            if ("CANCELLATION_CARRIER_VERIFIED".equals(historyEntry.getAction())) {
                if (!Objects.equals(historyEntry.getChangedBy(), actor) || !Objects.equals(historyEntry.getDetailsJson(), payloadJson)) {
                    throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
                }
                return;
            }
        }

        if (saga.getCurrentStep() != OrderSagaStep.STOP_SHIPMENT
                || saga.getStatus() == OrderSagaStatus.COMPLETED
                || isCarrierIrreversible(saga)) {
            throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
        }

        Shipment shipment = shipments.findByOrderIdForUpdate(orderId).orElse(null);
        if (isShipmentHandedOff(shipment)) {
            throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
        }

        if (Boolean.TRUE.equals(input.noShipmentExists())) {
            if (shipment != null
                    || (input.trackingCode() != null && !input.trackingCode().isBlank())
                    || (order.getTrackingCode() != null && !order.getTrackingCode().isBlank())
                    || (order.getShipmentInitiationToken() != null
                            && (order.getShipmentInitiationStartedAt() == null
                                    || order.getShipmentInitiationStartedAt().isAfter(Instant.now().minusSeconds(120))))) {
                throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
            }
        } else {
            String expectedTracking = shipment == null ? order.getTrackingCode() : shipment.getTrackingCode();
            if (expectedTracking == null || !Objects.equals(expectedTracking, input.trackingCode())) {
                throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
            }
        }

        if (shipment != null) {
            shipment.setStatus(ShipmentStatus.CANCELLED);
        }

        recordHistory(order, order.getStatus(), order.getStatus(), "CANCELLATION_CARRIER_VERIFIED", actor, input.reason(), payloadJson);
        recordHistory(order, order.getStatus(), order.getStatus(), "CANCELLATION_CARRIER_STOPPED", actor, input.reason(), null);
        transitionToInventoryCancellation(saga);
    }

    @Transactional
    public void lateHandoff(String orderId, String proofDescription) {
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        OrderSaga saga = sagas.findByOrderIdAndTypeForUpdate(orderId, OrderSagaType.CANCELLATION).orElse(null);

        if (saga == null || isCarrierIrreversible(saga)) {
            return;
        }
        if (proofDescription == null || proofDescription.isBlank()) {
            throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
        }

        // Hủy đã xong (kho nhả, tiền hoàn) mà hàng vẫn đi: chỉ ghi nhận để vận hành xử lý
        if (saga.getStatus() == OrderSagaStatus.COMPLETED) {
            recordHistory(order, order.getStatus(), order.getStatus(), "CANCELLATION_LATE_HANDOFF", "CARRIER", proofDescription, null);
            return;
        }

        // Carrier lấy hàng trước khi dừng được vận đơn: chưa nhả kho/hoàn tiền → từ chối hủy, đơn đi tiếp
        if (saga.getCurrentStep() == OrderSagaStep.STOP_SHIPMENT) {
            rejectCancellationBeforeCarrierStop(order, saga,
                    shipments.findByOrderIdForUpdate(orderId).orElse(null), proofDescription);
            return;
        }

        failCarrierHandoff(order, saga, "Carrier handoff after cancellation: " + proofDescription);
    }

    // =========================================================================
    // 3. BƯỚC 2: NHẢ KHO (CANCEL_ORDER_INVENTORY)
    // =========================================================================

    @Transactional
    public void inventoryCancelled(OrderInventoryCancelledEvent reply, String correlationId) {
        SagaContext context = lockOrderAndSaga(reply.orderId(), reply.cancellationId());
        Order order = context.order();
        OrderSaga saga = context.saga();

        if (!Objects.equals(saga.getId(), correlationId)
                || !Objects.equals(saga.getInventoryReservationId(), reply.reservationId())) {
            throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
        }

        if (saga.getCurrentStep() != OrderSagaStep.CANCEL_ORDER_INVENTORY || saga.getStatus() == OrderSagaStatus.COMPLETED) {
            return;
        }
        if (isCarrierIrreversible(saga)) {
            return;
        }

        recordHistory(order, order.getStatus(), order.getStatus(), "CANCELLATION_INVENTORY_CANCELLED", "CATALOG", "Inventory cancellation confirmed", null);

        // Đơn COD: nếu chưa thu tiền COD thì không cần hoàn tiền, đóng đơn luôn
        Shipment shipment = shipments.findByOrderIdForUpdate(order.getId()).orElse(null);
        if (order.getPaymentMethod() == PaymentMethod.COD && isCodSettledWithoutRefund(order, shipment, saga)) {
            return;
        }

        transitionToRefund(saga, order);
    }

    @Transactional
    public void inventoryRejected(OrderInventoryCancellationRejectedEvent reply, String correlationId) {
        SagaContext context = lockOrderAndSaga(reply.orderId(), reply.cancellationId());
        OrderSaga saga = context.saga();

        if (!Objects.equals(saga.getId(), correlationId)) {
            throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
        }
        if (saga.getCurrentStep() != OrderSagaStep.CANCEL_ORDER_INVENTORY
                || saga.getStatus() == OrderSagaStatus.COMPLETED
                || isCarrierIrreversible(saga)) {
            return;
        }

        saga.fail("INVENTORY_REJECTED", reply.code() + ": " + reply.reason());
    }

    // =========================================================================
    // 4. BƯỚC 3: HOÀN TIỀN (REFUND_ORDER_FUNDS)
    // =========================================================================

    @Transactional
    public void refundCredited(OrderFundsRefundCreditedEvent reply, String correlationId) {
        SagaContext context = lockOrderAndSaga(reply.orderId(), reply.sourceId());
        Order order = context.order();
        OrderSaga saga = context.saga();

        if (!Objects.equals(saga.getId(), correlationId)
                || !Objects.equals(saga.getOrderId(), reply.orderId())
                || !Objects.equals(saga.getPaymentId(), reply.paymentId())
                || reply.ledgerEntryId() == null
                || reply.amount() == null
                || order.getTotalAmount() == null
                || reply.amount().compareTo(order.getTotalAmount()) != 0) {
            throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
        }

        if (saga.getRefundLedgerEntryId() != null) {
            if (!Objects.equals(saga.getRefundLedgerEntryId(), reply.ledgerEntryId())) {
                throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
            }
            if (saga.getStatus() == OrderSagaStatus.COMPLETED || isCarrierIrreversible(saga)) {
                return;
            }
        }

        if (saga.getCurrentStep() != OrderSagaStep.REFUND_ORDER_FUNDS) {
            throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
        }

        boolean isFirstReceipt = saga.getRefundLedgerEntryId() == null;
        saga.setRefundLedgerEntryId(reply.ledgerEntryId());
        saga.setUpdatedAt(LocalDateTime.now());

        if (isFirstReceipt) {
            recordHistory(order, order.getStatus(), order.getStatus(), "CANCELLATION_REFUND_CREDITED", "PAYMENT", "Wallet credited: " + reply.ledgerEntryId(), null);
        }

        if (isCarrierIrreversible(saga)) {
            return;
        }

        closeCancellation(order, saga);
    }

    @Transactional
    public void refundRejected(OrderFundsRefundRejectedEvent reply, String correlationId) {
        SagaContext context = lockOrderAndSaga(reply.orderId(), reply.sourceId());
        OrderSaga saga = context.saga();

        if (!Objects.equals(saga.getId(), correlationId)) {
            throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
        }
        if (saga.getStatus() == OrderSagaStatus.COMPLETED
                || saga.getRefundLedgerEntryId() != null
                || isCarrierIrreversible(saga)) {
            return;
        }
        if (saga.getCurrentStep() != OrderSagaStep.REFUND_ORDER_FUNDS) {
            throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
        }

        saga.fail("REFUND_REJECTED", reply.code() + ": " + reply.reason());
    }

    // =========================================================================
    // 5. TIMEOUT & RETRY & HOÀN TẤT
    // =========================================================================

    @Transactional
    public void retry(String orderId, String sagaId) {
        SagaContext context = lockOrderAndSaga(orderId, sagaId);
        Order order = context.order();
        OrderSaga saga = context.saga();

        if (saga.getStatus() != OrderSagaStatus.FAILED || isCarrierIrreversible(saga)) {
            return;
        }

        Shipment shipment = shipments.findByOrderIdForUpdate(orderId).orElse(null);
        if (isShipmentHandedOff(shipment)) {
            saga.fail("CARRIER_HANDOFF", "Carrier handoff discovered during recovery");
            return;
        }

        resetSagaStepState(saga);
        if (saga.getCurrentStep() == OrderSagaStep.CANCEL_ORDER_INVENTORY) {
            outbox.emit(saga, SagaCommands.cancelOrderInventory(saga));
        } else if (saga.getCurrentStep() == OrderSagaStep.REFUND_ORDER_FUNDS) {
            outbox.emit(saga, SagaCommands.refundCancellation(saga, order));
        }
    }

    @Transactional
    public CancellationCarrierWork prepareTimedOutStep(String orderId, String sagaId, LocalDateTime now) {
        Order order = orders.findByIdForUpdate(orderId).orElse(null);
        if (order == null) return null;

        OrderSaga saga = sagas.findByIdForUpdate(sagaId).orElse(null);
        if (saga == null
                || saga.getType() != OrderSagaType.CANCELLATION
                || !Objects.equals(saga.getOrderId(), orderId)
                || saga.getStatus() != OrderSagaStatus.RUNNING
                || saga.getStepDeadline() == null
                || !saga.getStepDeadline().isBefore(now)) {
            return null;
        }

        if (!saga.canRetry()) {
            saga.fail("STEP_RETRY_EXHAUSTED", "Cạn 5 lần thử ở bước " + saga.getCurrentStep());
            recordHistory(order, order.getStatus(), order.getStatus(), "CANCELLATION_RETRY_EXHAUSTED", "SYSTEM", saga.getFailureReason(), null);
            return null;
        }

        Shipment shipment = shipments.findByOrderIdForUpdate(orderId).orElse(null);
        if (isShipmentHandedOff(shipment)) {
            failCarrierHandoff(order, saga, "Carrier handoff discovered during timeout recovery");
            return null;
        }

        saga.retryCurrentStep();
        switch (saga.getCurrentStep()) {
            case STOP_SHIPMENT -> {
                if (shipment == null || shipment.getTrackingCode() == null || shipment.getTrackingCode().isBlank()) {
                    saga.fail("CARRIER_UNKNOWN", "Shipment creation/tracking requires manual verification");
                    return null;
                }
                return new CancellationCarrierWork(orderId, sagaId, shipment.getTrackingCode(), shipment.getId(), null);
            }
            case CANCEL_ORDER_INVENTORY -> outbox.emit(saga, SagaCommands.cancelOrderInventory(saga));
            case REFUND_ORDER_FUNDS -> outbox.emit(saga, SagaCommands.refundCancellation(saga, order));
            default -> throw new IllegalStateException("Invalid cancellation step: " + saga.getCurrentStep());
        }
        return null;
    }

    private void closeCancellation(Order order, OrderSaga saga) {
        OrderStatus previousStatus = order.getStatus();
        order.cancel(order.getCancelReason());
        saga.complete();
        promotions.release(order.getId());

        recordHistory(order, previousStatus, OrderStatus.CANCELLED, "ORDER_CANCELLED", "SYSTEM", order.getCancelReason(), null);
        notifications.sendOrderCancelledNotification(order, order.getCancelReason());
    }

    // =========================================================================
    // 6. HELPER METHODS NỘI BỘ
    // =========================================================================

    private SagaContext lockOrderAndSaga(String orderId, String sagaId) {
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        OrderSaga saga = sagas.findByIdForUpdate(sagaId)
                .orElseThrow(() -> new AppException(OrderErrorCode.CANCELLATION_CONFLICT));

        if (saga.getType() != OrderSagaType.CANCELLATION || !Objects.equals(saga.getOrderId(), orderId)) {
            throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
        }
        return new SagaContext(order, saga);
    }

    private void transitionToInventoryCancellation(OrderSaga saga) {
        saga.setCurrentStep(OrderSagaStep.CANCEL_ORDER_INVENTORY);
        resetSagaStepState(saga);
        outbox.emit(saga, SagaCommands.cancelOrderInventory(saga));
    }

    private void transitionToRefund(OrderSaga saga, Order order) {
        saga.setCurrentStep(OrderSagaStep.REFUND_ORDER_FUNDS);
        resetSagaStepState(saga);
        outbox.emit(saga, SagaCommands.refundCancellation(saga, order));
    }

    private void resetSagaStepState(OrderSaga saga) {
        saga.setStatus(OrderSagaStatus.RUNNING);
        saga.setRetryCount(0);
        saga.setStepDeadline(LocalDateTime.now().plusSeconds(60));
        saga.setUpdatedAt(LocalDateTime.now());
        saga.setFailureCode(null);
        saga.setFailureReason(null);
    }

    private boolean isCarrierIrreversible(OrderSaga saga) {
        return Set.of("CARRIER_HANDOFF", "CARRIER_LABEL_CONFLICT")
                .contains(Objects.toString(saga.getFailureCode(), ""));
    }

    private boolean isShipmentHandedOff(Shipment shipment) {
        return shipment != null && shipment.isHandedOff();
    }

    private void rejectCancellationBeforeCarrierStop(Order order, OrderSaga saga, Shipment shipment, String proofDescription) {
        saga.fail("CARRIER_HANDOFF", "Carrier handoff before cancellation: " + proofDescription);
        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.SHIPPING);
        order.setCancelReason(null);
        recordHistory(order, previousStatus, OrderStatus.SHIPPING, "CANCELLATION_REJECTED", "CARRIER", proofDescription, null);
        if (shipment != null) {
            notifications.sendOrderShippedNotification(order, shipment);
        }
    }

    private void failCarrierHandoff(Order order, OrderSaga saga, String reason) {
        saga.fail("CARRIER_HANDOFF", reason);
        recordHistory(order, order.getStatus(), order.getStatus(), "CANCELLATION_LATE_HANDOFF", "CARRIER", saga.getFailureReason(), null);
    }

    private boolean isCodSettledWithoutRefund(Order order, Shipment shipment, OrderSaga saga) {
        boolean isCollected = shipment != null
                && (shipment.getCodCollectedAt() != null
                        || Set.of("COLLECTED", "TRANSFERRED").contains(Objects.toString(shipment.getCodStatus(), "")));

        if (!isCollected) {
            // COD chưa thu tiền: Không cần hoàn tiền, kết thúc hủy đơn luôn
            closeCancellation(order, saga);
            return true;
        }
        return false; // Đã thu COD: Cần chuyển sang bước hoàn tiền
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize cancellation audit json", e);
        }
    }

    private void recordHistory(
            Order order, OrderStatus fromStatus, OrderStatus toStatus, String action, String changedBy, String reason, String detailsJson) {
        history.save(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .action(action)
                .changedBy(changedBy)
                .reason(reason)
                .detailsJson(detailsJson)
                .build());
    }
}
