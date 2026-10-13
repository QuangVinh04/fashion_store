package com.fashionstore.order.dto;

import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderSaga;
import com.fashionstore.order.entity.OrderStatusHistory;
import com.fashionstore.order.entity.enumeration.OrderSagaStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Compatible API projection; history is audit, never the workflow source of truth. */
public record OrderCancellationResponse(
        String id,
        String operationId,
        String status,
        String reason,
        Instant requestedAt,
        Instant fulfillmentStoppedAt,
        Instant inventoryCancelledAt,
        String refundId,
        String refundLedgerEntryId,
        Instant closedAt,
        String failureReason,
        String reviewOwner,
        Instant reviewDueAt,
        Instant carrierVerifiedAt) {
    public static OrderCancellationResponse from(
            OrderSaga saga, Order order, List<OrderStatusHistory> checkpoints) {
        String status;
        if (saga.getStatus() == OrderSagaStatus.COMPLETED) status = "COMPLETED";
        else if (saga.getStatus() == OrderSagaStatus.FAILED)
            status =
                    Set.of("CARRIER_HANDOFF", "CARRIER_LABEL_CONFLICT")
                                    .contains(Objects.toString(saga.getFailureCode(), ""))
                            ? "FULFILLMENT_EXCEPTION"
                            : "REVIEW_REQUIRED";
        else
            status =
                    switch (saga.getCurrentStep()) {
                        case STOP_SHIPMENT -> "FULFILLMENT_PENDING";
                        case CANCEL_ORDER_INVENTORY -> "INVENTORY_PENDING";
                        case REFUND_ORDER_FUNDS -> "REFUND_PENDING";
                        default ->
                                throw new IllegalStateException(
                                        "Invalid cancellation projection step");
                    };
        Instant stopped = null, inventory = null, verified = null, reviewDue = null;
        String reviewOwner = null;
        for (var row : checkpoints) {
            Instant at =
                    row.getCreatedAt() == null
                            ? null
                            : row.getCreatedAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
            if ("CANCELLATION_CARRIER_STOPPED".equals(row.getAction()) && stopped == null)
                stopped = at;
            if ("CANCELLATION_INVENTORY_CANCELLED".equals(row.getAction()) && inventory == null)
                inventory = at;
            if ("CANCELLATION_CARRIER_VERIFIED".equals(row.getAction()) && verified == null)
                verified = at;
            if ("CANCELLATION_LEGACY_IMPORTED".equals(row.getAction())) {
                try {
                    var legacy = new ObjectMapper().readTree(row.getDetailsJson());
                    if (stopped == null && !legacy.path("fulfillment_stopped_at").isNull())
                        stopped = Instant.parse(legacy.path("fulfillment_stopped_at").asText());
                    if (inventory == null && !legacy.path("inventory_cancelled_at").isNull())
                        inventory = Instant.parse(legacy.path("inventory_cancelled_at").asText());
                    if (verified == null && !legacy.path("carrier_verified_at").isNull())
                        verified = Instant.parse(legacy.path("carrier_verified_at").asText());
                    if (!legacy.path("review_due_at").isNull())
                        reviewDue = Instant.parse(legacy.path("review_due_at").asText());
                    if (!legacy.path("review_owner").isNull())
                        reviewOwner = legacy.path("review_owner").asText();
                } catch (java.io.IOException | java.time.DateTimeException invalid) {
                    throw new IllegalStateException("Invalid cancellation audit snapshot", invalid);
                }
            }
        }
        return new OrderCancellationResponse(
                saga.getId(),
                saga.getRequestOperationId(),
                status,
                order.getCancelReason(),
                saga.getCreatedAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(),
                stopped,
                inventory,
                saga.getRefundId(),
                saga.getRefundLedgerEntryId(),
                saga.getCompletedAt() == null
                        ? null
                        : saga.getCompletedAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(),
                saga.getFailureReason(),
                reviewOwner,
                reviewDue,
                verified);
    }
}
