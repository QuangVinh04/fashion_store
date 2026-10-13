package com.fashionstore.order.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.common.*;
import com.fashionstore.contracts.order.OrderDeliveredEvent;
import com.fashionstore.order.dto.OrderResponse;
import com.fashionstore.order.entity.*;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.event.OutboxEventRecorder;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.mapper.OrderResponseMapper;
import com.fashionstore.order.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderReceiptService {
    public static final Duration RETURN_WINDOW = Duration.ofDays(7);
    private final OrderRepository orders;
    private final ReturnRequestRepository returns;
    private final OrderStatusHistoryRepository history;
    private final CurrentUserProvider currentUser;
    private final OutboxEventRecorder outbox;
    private final OrderNotificationService notifications;
    private final Clock clock;

    @Transactional
    public void markDelivered(String id, Instant eventTime, String source) {
        markDelivered(id, eventTime, source, null, null, null, null);
    }

    @Transactional
    public void markDelivered(
            String id,
            Instant eventTime,
            String source,
            BigDecimal codAmount,
            Instant codCollectedAt,
            String trackingCode,
            String carrierShopId) {
        Order order =
                orders.findByIdForUpdate(id)
                        .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        if (codAmount != null
                && (!"GHN".equals(source)
                        || order.getPaymentMethod() != PaymentMethod.COD
                        || codAmount.compareTo(order.getTotalAmount()) != 0
                        || codCollectedAt == null
                        || trackingCode == null
                        || trackingCode.isBlank()
                        || carrierShopId == null
                        || carrierShopId.isBlank())) {
            throw new AppException(OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
        }
        if (order.getDeliveredAt() != null) {
            if (codAmount != null) {
                outbox.saveMessage(
                        id,
                        EventTypes.ORDER_DELIVERED,
                        EventEnvelope.v1(
                                EventTypes.ORDER_DELIVERED,
                                id,
                                id,
                                new OrderDeliveredEvent(
                                        id,
                                        LocalDateTime.ofInstant(
                                                order.getDeliveredAt(), ZoneOffset.UTC),
                                        codAmount,
                                        codCollectedAt,
                                        trackingCode,
                                        carrierShopId)));
            }
            return;
        }
        if (order.getStatus() != OrderStatus.SHIPPING
                && order.getStatus() != OrderStatus.PACKED
                && order.getStatus() != OrderStatus.PROCESSING
                && order.getStatus() != OrderStatus.CONFIRMED
                && order.getStatus() != OrderStatus.DELIVERED) {
            throw new AppException(OrderErrorCode.ORDER_STATUS_INVALID);
        }
        OrderStatus before = order.getStatus();
        boolean valid = eventTime != null && !eventTime.isAfter(clock.instant().plusSeconds(300));
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(valid ? eventTime : null);
        order.setDeliveryEvidenceIssue(valid ? null : "Delivery timestamp needs verification");
        orders.save(order);
        if (before != OrderStatus.DELIVERED || valid) {
            record(
                    order,
                    before,
                    OrderStatus.DELIVERED,
                    valid ? "DELIVERY_CONFIRMED" : "DELIVERY_TIME_REVIEW",
                    "ADMIN".equals(source) ? currentUser.getCurrentUserId() : source,
                    "Delivery recorded from " + source);
        }
        outbox.saveMessage(
                id,
                EventTypes.ORDER_DELIVERED,
                EventEnvelope.v1(
                        EventTypes.ORDER_DELIVERED,
                        id,
                        id,
                        new OrderDeliveredEvent(
                                id,
                                valid ? LocalDateTime.ofInstant(eventTime, ZoneOffset.UTC) : null,
                                codAmount,
                                codCollectedAt,
                                trackingCode,
                                carrierShopId)));
        if (order.getDeliveryNotifiedAt() == null) {
            order.setDeliveryNotifiedAt(clock.instant());
            notifications.sendOrderDeliveredNotification(order);
        }
    }

    @Transactional
    public OrderResponse confirmReceipt(String id) {
        Order order =
                orders.findByIdForUpdate(id)
                        .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!order.getUserId().equals(currentUser.getCurrentUserId()))
            throw new AppException(OrderErrorCode.ORDER_NOT_FOUND);
        if (order.getStatus() == OrderStatus.COMPLETED)
            return enrich(order, OrderResponseMapper.create(order, order.getCheckoutId()));
        if (order.getStatus() != OrderStatus.DELIVERED || hasOpenReturn(id))
            throw new AppException(OrderErrorCode.ORDER_RETURN_NOT_ALLOWED);
        complete(order, clock.instant(), "CUSTOMER", currentUser.getCurrentUserId());
        return enrich(order, OrderResponseMapper.create(order, order.getCheckoutId()));
    }

    @Transactional
    public void completeExpired(Instant now) {
        orders.findReceiptCompletionCandidates(now.minus(RETURN_WINDOW)).stream()
                .sorted()
                .forEach(
                        id -> {
                            Order order = orders.findByIdForUpdate(id).orElse(null);
                            if (order != null
                                    && order.getStatus() == OrderStatus.DELIVERED
                                    && order.getDeliveredAt() != null
                                    && !now.isBefore(order.getDeliveredAt().plus(RETURN_WINDOW))
                                    && !hasOpenReturn(id)) complete(order, now, "AUTO", "SYSTEM");
                        });
    }

    @Transactional(readOnly = true)
    public boolean canRequestReturn(Order order, Instant now) {
        return inWindow(order, now) && returns.findByOrderId(order.getId()).isEmpty();
    }

    @Transactional(readOnly = true)
    public OrderResponse enrich(Order order, OrderResponse response) {
        Optional<ReturnRequest> request = returns.findByOrderId(order.getId());
        boolean open = request.filter(value -> value.getClosedAt() == null).isPresent();
        response.setDeliveredAt(order.getDeliveredAt());
        response.setCompletedAt(order.getCompletedAt());
        response.setCompletionSource(order.getCompletionSource());
        response.setDeliveryEvidenceIssue(order.getDeliveryEvidenceIssue());
        response.setReturnEligibleUntil(
                order.getDeliveredAt() == null ? null : order.getDeliveredAt().plus(RETURN_WINDOW));
        response.setCanRequestReturn(inWindow(order, clock.instant()) && request.isEmpty());
        response.setCanConfirmReceipt(order.getStatus() == OrderStatus.DELIVERED && !open);
        response.setActiveReturnId(
                request.filter(value -> value.getClosedAt() == null)
                        .map(ReturnRequest::getId)
                        .orElse(null));
        return response;
    }

    private boolean inWindow(Order order, Instant now) {
        return order.getStatus() == OrderStatus.DELIVERED
                && order.getDeliveredAt() != null
                && order.getCompletedAt() == null
                && now.isBefore(order.getDeliveredAt().plus(RETURN_WINDOW));
    }

    public Instant now() {
        return clock.instant();
    }

    private boolean hasOpenReturn(String id) {
        return returns.findByOrderIdForUpdate(id)
                .filter(value -> value.getClosedAt() == null)
                .isPresent();
    }

    private void complete(Order order, Instant now, String source, String actor) {
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(now);
        order.setCompletionSource(source);
        record(
                order,
                OrderStatus.DELIVERED,
                OrderStatus.COMPLETED,
                "RECEIPT_CONFIRMED",
                actor,
                "Receipt confirmed by " + source);
    }

    private void record(
            Order order,
            OrderStatus before,
            OrderStatus after,
            String action,
            String actor,
            String reason) {
        history.save(
                OrderStatusHistory.builder()
                        .order(order)
                        .fromStatus(before)
                        .toStatus(after)
                        .action(action)
                        .changedBy(actor)
                        .reason(reason)
                        .build());
    }
}
