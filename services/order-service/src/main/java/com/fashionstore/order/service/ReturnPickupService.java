package com.fashionstore.order.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.order.client.GhnClient;
import com.fashionstore.order.config.GhnProperties;
import com.fashionstore.order.config.ReturnProperties;
import com.fashionstore.order.dto.GhnWebhookPayload;
import com.fashionstore.order.dto.VerifiedGhnShipment;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderStatusHistory;
import com.fashionstore.order.entity.ReturnRequest;
import com.fashionstore.order.entity.ReturnShipment;
import com.fashionstore.order.entity.enumeration.ReturnRequestStatus;
import com.fashionstore.order.entity.enumeration.ReturnShipmentStatus;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.OrderStatusHistoryRepository;
import com.fashionstore.order.repository.ReturnRequestRepository;
import com.fashionstore.order.repository.ReturnShipmentRepository;

import io.micrometer.core.instrument.MeterRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Set;

/**
 * Vòng đời vận đơn chiều về (khách -> kho) của yêu cầu trả hàng: webhook GHN, hủy, quá hạn lấy
 * hàng và đối soát. Không tin nội dung webhook: trạng thái thật luôn đọc lại từ API GHN, và gọi GHN
 * luôn nằm ngoài transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnPickupService {
    private static final Set<String> IN_TRANSIT_STATUSES =
            Set.of(
                    "picked",
                    "storing",
                    "transporting",
                    "sorting",
                    "delivering",
                    "money_collect_delivering");
    /** Giao thất bại / hoàn / thất lạc / hư hỏng trên chiều về: cần người xử lý, không tự đóng phiếu. */
    private static final Set<String> ATTENTION_STATUSES =
            Set.of(
                    "delivery_fail",
                    "waiting_to_return",
                    "return",
                    "return_transporting",
                    "return_sorting",
                    "returning",
                    "return_fail",
                    "returned",
                    "lost",
                    "damage",
                    "exception",
                    "scrap");

    private final ReturnShipmentRepository shipments;
    private final ReturnRequestRepository returns;
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final GhnClient ghnClient;
    private final GhnProperties ghnProperties;
    private final OrderReceiptService receipts;
    private final OrderNotificationService notifications;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final ReturnProperties properties;
    private final com.fashionstore.common.security.CurrentUserProvider currentUser;
    private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    /**
     * @return true nếu mã GHN thuộc một vận đơn trả (đã xử lý), false để handler chiều giao đi xử lý.
     */
    @Transactional(propagation = Propagation.NEVER)
    public boolean handleGhnCallback(GhnWebhookPayload payload) {
        String code = payload.getOrderCode() == null ? "" : payload.getOrderCode().trim();
        String returnId = shipments.findReturnRequestIdByGhnOrderCode(code).orElse(null);
        if (returnId == null) {
            return false;
        }
        if (payload.getShopId() == null || !payload.getShopId().equals(ghnProperties.getShopId())) {
            throw new AppException(OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
        }

        VerifiedGhnShipment verified = ghnClient.getVerifiedShipment(code);
        if (verified == null
                || !code.equals(verified.orderCode())
                || !payload.getShopId().equals(verified.shopId())
                || !isReturnClientCode(verified.clientOrderCode(), returnId)) {
            throw new AppException(OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
        }

        Instant eventTime = parseTime(payload.getTime());
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        tx ->
                                apply(
                                        returnId,
                                        verified.status().toLowerCase(Locale.ROOT).trim(),
                                        payload.getReason(),
                                        eventTime == null ? clock.instant() : eventTime));
        return true;
    }

    @Transactional(propagation = Propagation.NEVER)
    @PreAuthorize("hasRole('ADMIN')")
    public void cancelByAdmin(String returnRequestId, String reason) {
        cancel(returnRequestId, currentUser.getCurrentUserId(), reason, false);
    }

    private enum CancelOutcome {
        CLOSED,
        ALREADY_PICKED,
        UNCONFIRMED
    }

    /**
     * Hủy lượt lấy hàng: chưa có đơn GHN (hoặc lượt đã thất bại) thì xử lý ngay; đã có thì GHN phải
     * xác nhận hủy (ngoài transaction). Admin hủy thì đóng phiếu; quá hạn thì chỉ đưa lượt lấy hàng
     * sang PICKUP_FAILED để admin quyết định. GHN cho biết hàng đã được lấy thì khóa hủy, phiếu sang
     * IN_TRANSIT.
     */
    private void cancel(String returnId, String actor, String reason, boolean overdue) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        String ghnCode =
                transaction.execute(status -> prepareCancel(returnId, actor, reason));
        if (ghnCode == null) {
            return;
        }
        var result = ghnClient.cancelPendingShipment(ghnCode);
        CancelOutcome outcome =
                transaction.execute(
                        status -> finishCancel(returnId, actor, reason, overdue, result.outcome()));
        if (outcome == CancelOutcome.ALREADY_PICKED) {
            throw new AppException(OrderErrorCode.RETURN_WORKFLOW_CONFLICT);
        }
        if (outcome == CancelOutcome.UNCONFIRMED) {
            throw new AppException(com.fashionstore.common.exception.ErrorCode.UPSTREAM_SERVICE_ERROR);
        }
    }

    /** @return mã đơn GHN cần hủy, hoặc null nếu đã xử lý xong (đã đóng / không cần GHN). */
    private String prepareCancel(String returnId, String actor, String reason) {
        Order order = lockOrder(returnId);
        ReturnRequest returned = lockReturn(returnId);
        if (returned.getStatus() == ReturnRequestStatus.CANCELLED) {
            return null;
        }
        if (returned.getKind() != com.fashionstore.order.entity.enumeration.ReturnRequestKind.CUSTOMER_RETURN) {
            throw new AppException(OrderErrorCode.RETURN_WORKFLOW_CONFLICT);
        }
        if (returned.getStatus() == ReturnRequestStatus.REQUESTED
                || returned.getStatus() == ReturnRequestStatus.PENDING) {
            close(order, returned, actor, "RETURN_CANCELLED", reason);
            return null;
        }
        if (returned.getStatus() != ReturnRequestStatus.APPROVED
                && returned.getStatus() != ReturnRequestStatus.RETURN_APPROVED) {
            throw new AppException(OrderErrorCode.RETURN_WORKFLOW_CONFLICT);
        }
        ReturnShipment shipment = shipments.findByReturnRequestIdForUpdate(returnId).orElse(null);
        if (shipment == null || shipment.getStatus() == ReturnShipmentStatus.PICKUP_FAILED) {
            // Chưa có đơn trả GHN, hoặc GHN đã hủy lượt lấy hàng: không còn gì để hủy bên GHN.
            close(order, returned, actor, "RETURN_CANCELLED", reason);
            return null;
        }
        if (shipment.getStatus() != ReturnShipmentStatus.READY_TO_PICK) {
            throw new AppException(OrderErrorCode.RETURN_WORKFLOW_CONFLICT);
        }
        return shipment.getGhnOrderCode();
    }

    private CancelOutcome finishCancel(
            String returnId, String actor, String reason, boolean overdue, String ghnOutcome) {
        Order order = lockOrder(returnId);
        ReturnRequest returned = lockReturn(returnId);
        ReturnShipment shipment =
                shipments
                        .findByReturnRequestIdForUpdate(returnId)
                        .orElseThrow(() -> new AppException(OrderErrorCode.RETURN_WORKFLOW_CONFLICT));
        if (returned.getStatus() == ReturnRequestStatus.CANCELLED) {
            return CancelOutcome.CLOSED;
        }
        // Webhook `picked` có thể đã tới trong lúc ta hỏi GHN: GHN xác nhận hủy cũng không đóng nữa.
        if ((returned.getStatus() != ReturnRequestStatus.APPROVED
                        && returned.getStatus() != ReturnRequestStatus.RETURN_APPROVED)
                || shipment.getStatus() != ReturnShipmentStatus.READY_TO_PICK) {
            throw new AppException(OrderErrorCode.RETURN_WORKFLOW_CONFLICT);
        }
        switch (ghnOutcome == null ? "" : ghnOutcome) {
            case "CANCELLED" -> {
                if (overdue) {
                    pickupFailed(order, returned, shipment, actor, "PICKUP_FAILED", reason, clock.instant());
                    return CancelOutcome.CLOSED;
                }
                shipment.setStatus(ReturnShipmentStatus.CANCELLED);
                shipment.setCancelledAt(clock.instant());
                close(order, returned, actor, "RETURN_CANCELLED", reason);
                return CancelOutcome.CLOSED;
            }
            case "HANDED_OFF" -> {
                markPickedUp(order, returned, shipment, clock.instant());
                return CancelOutcome.ALREADY_PICKED;
            }
            default -> {
                return CancelOutcome.UNCONFIRMED;
            }
        }
    }

    private Order lockOrder(String returnId) {
        String orderId =
                returns.findOrderIdByReturnId(returnId)
                        .orElseThrow(() -> new AppException(OrderErrorCode.RETURN_REQUEST_NOT_FOUND));
        return orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private ReturnRequest lockReturn(String returnId) {
        return returns.findByIdForUpdate(returnId)
                .orElseThrow(() -> new AppException(OrderErrorCode.RETURN_REQUEST_NOT_FOUND));
    }

    /**
     * GHN không lấy hàng quá hạn: hủy đơn trả bên GHN và chỉ đóng phiếu khi GHN xác nhận. Không xác
     * nhận được thì thử lại ở lượt sau, quá số lần thì bỏ cuộc và báo admin qua chỉ số.
     */
    @Transactional(propagation = Propagation.NEVER)
    public void expirePickups(Instant now) {
        var ids =
                shipments.findPickupOverdueReturnIds(
                        now.minus(properties.pickupTimeout()),
                        properties.getPickupCancelMaxAttempts(),
                        PageRequest.of(0, properties.getBatchSize()));
        for (String id : ids) {
            try {
                cancel(
                        id,
                        "SYSTEM",
                        "GHN không lấy được hàng quá hạn " + properties.getPickupTimeoutDays() + " ngày",
                        true);
            } catch (AppException conflict) {
                if (conflict.getErrorCode() == OrderErrorCode.RETURN_WORKFLOW_CONFLICT) {
                    log.info("[Return] Return {} changed while expiring its pickup", id);
                } else {
                    countCancelAttempt(id, conflict);
                }
            } catch (RuntimeException failure) {
                countCancelAttempt(id, failure);
            }
        }
    }

    private void countCancelAttempt(String returnId, RuntimeException failure) {
        log.warn("[Return] Could not cancel the overdue GHN return order of {}: {}", returnId, failure.getMessage());
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        tx ->
                                shipments
                                        .findByReturnRequestIdForUpdate(returnId)
                                        .ifPresent(s -> s.setCancelAttempts(s.getCancelAttempts() + 1)));
    }

    /**
     * Đối soát với GHN cho vận đơn trả còn mở: webhook chỉ được GHN gửi lại vài lần rồi bỏ, và bỏ hẳn
     * nếu ta từng trả 4xx. Dùng đúng hàm áp dụng trạng thái như webhook nên idempotent.
     */
    @Transactional(propagation = Propagation.NEVER)
    public void reconcile(Instant now) {
        var ids =
                shipments.findOpenForReconcile(
                        now.minus(properties.reconcileInterval()),
                        PageRequest.of(0, properties.getBatchSize()));
        for (String id : ids) {
            try {
                reconcileOne(id, now);
            } catch (RuntimeException failure) {
                log.warn("[Return] Reconcile of return {} failed: {}", id, failure.getMessage());
            }
        }
    }

    private void reconcileOne(String returnId, Instant now) {
        String code =
                shipments.findByReturnRequestId(returnId).map(ReturnShipment::getGhnOrderCode).orElse(null);
        if (code == null) {
            return;
        }
        VerifiedGhnShipment verified = null;
        try {
            verified = ghnClient.getVerifiedShipment(code);
        } catch (RuntimeException unavailable) {
            log.warn("[Return] GHN unavailable while reconciling {}: {}", code, unavailable.getMessage());
        }
        VerifiedGhnShipment fromGhn = verified;
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        tx -> {
                            if (fromGhn != null
                                    && code.equals(fromGhn.orderCode())
                                    && ghnProperties.getShopId().equals(fromGhn.shopId())
                                    && isReturnClientCode(fromGhn.clientOrderCode(), returnId)) {
                                apply(
                                        returnId,
                                        fromGhn.status().toLowerCase(Locale.ROOT).trim(),
                                        null,
                                        now);
                            }
                            // Kể cả khi GHN lỗi: giãn lượt thử kế tiếp thay vì gọi lại mỗi phút.
                            shipments
                                    .findByReturnRequestIdForUpdate(returnId)
                                    .ifPresent(s -> s.setLastReconciledAt(now));
                        });
    }

    /** Chỉ số cho admin: những gì đang quá hạn xử lý. Mỗi lượt quét ghi lại giá trị hiện tại. */
    public void publishSlaGauges(Instant now) {
        gauge(
                "order.return.requested.overdue",
                returns.countAwaitingReviewSince(
                        java.time.LocalDateTime.now(clock).minus(properties.adminReviewSla())));
        gauge("order.return.in_transit.stale", shipments.countInTransitSince(now.minus(properties.inTransitAlert())));
        gauge(
                "order.return.arrived.unreceived",
                shipments.countArrivedUnreceivedSince(now.minus(properties.arrivalReceiveSla())));
        gauge("order.return_shipment.needs_attention", shipments.countNeedingAttention());
        gauge(
                "order.return.pickup_cancel.exhausted",
                shipments.countPickupCancelExhausted(
                        properties.getPickupCancelMaxAttempts(), now.minus(properties.pickupTimeout())));
    }

    private void gauge(String name, long value) {
        gauges.computeIfAbsent(name, key -> meterRegistry.gauge(key, new AtomicLong())).set(value);
    }

    private Instant parseTime(String time) {
        try {
            return time == null || time.isBlank() ? null : Instant.parse(time);
        } catch (RuntimeException invalid) {
            throw new AppException(OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
        }
    }

    /** Áp dụng một trạng thái GHN đã được xác minh. Idempotent và chỉ tiến, không lùi. */
    private void apply(String returnId, String ghnStatus, String reason, Instant eventTime) {
        String orderId =
                returns.findOrderIdByReturnId(returnId)
                        .orElseThrow(() -> new AppException(OrderErrorCode.RETURN_REQUEST_NOT_FOUND));
        Order order =
                orders.findByIdForUpdate(orderId)
                        .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        ReturnRequest returned =
                returns.findByIdForUpdate(returnId)
                        .orElseThrow(() -> new AppException(OrderErrorCode.RETURN_REQUEST_NOT_FOUND));
        ReturnShipment shipment =
                shipments
                        .findByReturnRequestIdForUpdate(returnId)
                        .orElseThrow(() -> new AppException(OrderErrorCode.RETURN_REQUEST_NOT_FOUND));
        String previousGhnStatus = shipment.getLastGhnStatus();
        shipment.setLastGhnStatus(ghnStatus);

        boolean pickupStage = "ready_to_pick".equals(ghnStatus);
        if (returned.getClosedAt() != null) {
            // Đã đóng phiếu mà GHN vẫn có chuyển động (vd lấy hàng sau khi hủy): ghi nhận, không mở lại.
            if (!pickupStage && shipment.getStatus() != ReturnShipmentStatus.CANCELLED) {
                flagAttention(order, returned, shipment, ghnStatus, "phiếu đã đóng", previousGhnStatus);
            }
            return;
        }

        if (pickupStage) {
            if (reason != null
                    && !reason.isBlank()
                    && shipment.getStatus() == ReturnShipmentStatus.READY_TO_PICK) {
                record(order, "RETURN_PICKUP_ATTEMPT_FAILED", "GHN", reason.trim());
                notifications.sendReturnStatusNotification(
                        order,
                        "Lấy hàng trả chưa thành công",
                        "Shipper chưa lấy được hàng (" + reason.trim() + "). GHN sẽ liên hệ lại.");
            }
        } else if (IN_TRANSIT_STATUSES.contains(ghnStatus)) {
            markPickedUp(order, returned, shipment, eventTime);
        } else if ("delivered".equals(ghnStatus)) {
            markPickedUp(order, returned, shipment, eventTime);
            if (shipment.getStatus() != ReturnShipmentStatus.ARRIVED) {
                shipment.setStatus(ReturnShipmentStatus.ARRIVED);
                shipment.setArrivedAt(eventTime);
                record(order, "RETURN_ARRIVED_AT_WAREHOUSE", "GHN", "GHN báo hàng trả đã tới kho");
            }
        } else if ("cancel".equals(ghnStatus)) {
            if (shipment.getStatus() == ReturnShipmentStatus.READY_TO_PICK) {
                pickupFailed(
                        order,
                        returned,
                        shipment,
                        "GHN",
                        "CARRIER_CANCELLED",
                        "GHN hủy lượt lấy hàng" + (reason == null || reason.isBlank() ? "" : ": " + reason.trim()),
                        eventTime);
            } else if (shipment.getStatus() != ReturnShipmentStatus.PICKUP_FAILED) {
                flagAttention(order, returned, shipment, ghnStatus, "GHN hủy sau khi đã lấy hàng", previousGhnStatus);
            }
        } else if (ATTENTION_STATUSES.contains(ghnStatus)) {
            flagAttention(order, returned, shipment, ghnStatus, reason, previousGhnStatus);
        }
    }

    /**
     * Lượt lấy hàng thất bại trước khi GHN lấy hàng: phiếu vẫn mở, admin tạo lại lượt lấy hàng hoặc
     * hủy phiếu dựa trên lý do lưu ở đây.
     */
    private void pickupFailed(
            Order order,
            ReturnRequest returned,
            ReturnShipment shipment,
            String actor,
            String failureCode,
            String reason,
            Instant at) {
        shipment.setStatus(ReturnShipmentStatus.PICKUP_FAILED);
        shipment.setCancelledAt(at);
        shipment.setFailureCode(failureCode);
        shipment.setFailureReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
        shipment.setFailedAt(at);
        record(order, "RETURN_PICKUP_FAILED", actor, reason);
        notifications.sendReturnStatusNotification(
                order,
                "Lấy hàng trả chưa thành công",
                "GHN chưa lấy được hàng của bạn. Chúng tôi sẽ liên hệ để sắp xếp lại lượt lấy hàng.");
    }

    private static boolean isReturnClientCode(String clientOrderCode, String returnId) {
        // Lượt đầu gửi RET-<id>; lượt tạo lại sau khi thất bại gửi RET-<id>-<mã GHN cũ>.
        String code = "RET-" + returnId;
        return code.equals(clientOrderCode)
                || clientOrderCode != null && clientOrderCode.startsWith(code + "-");
    }

    private void markPickedUp(
            Order order, ReturnRequest returned, ReturnShipment shipment, Instant eventTime) {
        if (shipment.getStatus() != ReturnShipmentStatus.READY_TO_PICK) {
            return;
        }
        shipment.setStatus(ReturnShipmentStatus.PICKED);
        shipment.setPickedAt(eventTime);
        if (returned.getStatus() == ReturnRequestStatus.APPROVED
                || returned.getStatus() == ReturnRequestStatus.RETURN_APPROVED) {
            returned.setStatus(ReturnRequestStatus.IN_TRANSIT);
        }
        returns.save(returned);
        record(order, "RETURN_PICKED_UP", "GHN", "GHN đã lấy hàng trả tại khách");
        notifications.sendReturnStatusNotification(
                order,
                "Đã lấy hàng trả",
                "Shipper đã lấy hàng của bạn. Chúng tôi sẽ hoàn tiền sau khi kho nhận và kiểm tra hàng.");
    }

    private void flagAttention(
            Order order,
            ReturnRequest returned,
            ReturnShipment shipment,
            String ghnStatus,
            String detail,
            String previousGhnStatus) {
        if (ghnStatus.equals(previousGhnStatus)) {
            return; // webhook gửi lại / đối soát thấy lại cùng trạng thái: không ghi lặp
        }
        if (shipment.getStatus() != ReturnShipmentStatus.CANCELLED
                && shipment.getStatus() != ReturnShipmentStatus.ARRIVED) {
            shipment.setStatus(ReturnShipmentStatus.NEEDS_ATTENTION);
        }
        meterRegistry.counter("order.return_shipment.attention", "status", ghnStatus).increment();
        log.warn(
                "[Return] GHN return shipment {} of return {} needs attention: {} ({})",
                shipment.getGhnOrderCode(),
                returned.getId(),
                ghnStatus,
                detail);
        record(
                order,
                "RETURN_SHIPMENT_ATTENTION",
                "GHN",
                "GHN " + ghnStatus + (detail == null || detail.isBlank() ? "" : ": " + detail.trim()));
    }

    /**
     * Đóng phiếu trả hàng mà không hoàn tiền (hủy): phiếu CANCELLED, đơn hoàn tất ngay, báo khách.
     * Gọi trong transaction đã khóa Order rồi Return.
     */
    void close(Order order, ReturnRequest returned, String actor, String action, String reason) {
        Instant now = clock.instant();
        returned.setStatus(ReturnRequestStatus.CANCELLED);
        returned.setRejectReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
        returned.setReviewedBy(actor);
        returned.setReviewedAt(java.time.LocalDateTime.now(clock));
        returned.setClosedAt(now);
        returns.save(returned);
        record(order, action, actor, reason);
        notifications.sendReturnStatusNotification(
                order, "Yêu cầu trả hàng đã bị hủy", "Lý do: " + reason);
        receipts.completeAfterReturnClosed(order, now, actor, reason);
    }

    private void record(Order order, String action, String actor, String reason) {
        history.save(
                OrderStatusHistory.builder()
                        .order(order)
                        .fromStatus(order.getStatus())
                        .toStatus(order.getStatus())
                        .action(action)
                        .changedBy(actor)
                        .reason(reason)
                        .build());
    }
}
