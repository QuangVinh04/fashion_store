package com.fashionstore.order.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.order.OrderDeliveredEvent;
import com.fashionstore.order.client.CatalogClient;
import com.fashionstore.order.client.GhnClient;
import com.fashionstore.order.config.GhnProperties;
import com.fashionstore.order.dto.CreateShipmentRequest;
import com.fashionstore.order.dto.GhnWebhookPayload;
import com.fashionstore.order.dto.ProductVariantDto;
import com.fashionstore.order.dto.ShipmentResponse;
import com.fashionstore.order.dto.VerifiedGhnShipment;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderItem;
import com.fashionstore.order.entity.OrderStatusHistory;
import com.fashionstore.order.entity.Shipment;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.entity.enumeration.ShipmentProvider;
import com.fashionstore.order.entity.enumeration.ShipmentStatus;
import com.fashionstore.order.event.OutboxEventRecorder;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.OrderStatusHistoryRepository;
import com.fashionstore.order.repository.ShipmentRepository;
import com.fashionstore.order.service.OrderNotificationService;
import com.fashionstore.order.service.ShipmentService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShipmentServiceImpl implements ShipmentService {

    ShipmentRepository shipmentRepository;
    OrderRepository orderRepository;
    GhnClient ghnClient;
    CatalogClient catalogClient;
    CurrentUserProvider currentUserProvider;
    OrderStatusHistoryRepository orderStatusHistoryRepository;
    OrderNotificationService orderNotificationService;
    OutboxEventRecorder outboxEventRecorder;
    com.fashionstore.order.service.OrderReceiptService orderReceiptService;
    com.fashionstore.order.service.OrderCancellationSagaService orderCancellation;
    com.fashionstore.order.service.ShipmentCreationTransactionService shipmentCreation;
    GhnProperties ghnProperties;
    PlatformTransactionManager transactionManager;
    Clock clock;

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NEVER)
    public ShipmentResponse createShipment(String orderId, CreateShipmentRequest request) {
        var snapshot = shipmentCreation.snapshot(orderId);
        int weight =
                shipmentRepository.findByOrderId(orderId).isPresent()
                        ? 200
                        : calculateTotalWeight(snapshot.getItems());
        var claim = shipmentCreation.claim(orderId, request, weight);
        if (claim.existing() != null) return toResponse(claim.existing());
        try {
            String tracking = ghnClient.createOrder(claim.order(), claim.address(), claim.weight());
            return toResponse(shipmentCreation.finish(claim, tracking));
        } catch (RuntimeException error) {
            shipmentCreation.unknown(orderId, claim.token());
            throw error;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentByOrderId(String orderId) {
        String currentUserId = currentUserProvider.getCurrentUserId();
        Order order =
                orderRepository
                        .findById(orderId)
                        .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.getUserId().equals(currentUserId)) {
            throw new AppException(OrderErrorCode.ORDER_NOT_FOUND);
        }

        Shipment shipment =
                shipmentRepository
                        .findByOrderId(orderId)
                        .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        return toResponse(shipment);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public ShipmentResponse getShipmentForAdmin(String orderId) {
        orderRepository
                .findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        return toResponse(
                shipmentRepository
                        .findByOrderId(orderId)
                        .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND)));
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void handleGhnCallback(GhnWebhookPayload payload) {
        if (payload == null || payload.getOrderCode() == null || payload.getOrderCode().isBlank()) {
            throw new AppException(OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
        }
        if (payload.getShopId() == null || !payload.getShopId().equals(ghnProperties.getShopId())) {
            throw new AppException(OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
        }
        String orderCode = payload.getOrderCode().trim();
        String orderId =
                shipmentRepository
                        .findOrderIdByGhnOrderCode(orderCode)
                        .or(() -> shipmentRepository.findOrderIdByTrackingCode(orderCode))
                        .orElse(null);
        if (orderId == null) {
            log.info("Ignoring GHN callback for unknown tracking code {}", orderCode);
            return;
        }
        boolean transferCallback = "cod".equalsIgnoreCase(payload.getType());
        ShipmentStatus nextStatus = mapGhnStatus(payload.getStatus());
        if (!transferCallback && nextStatus == null) {
            return;
        }
        Instant eventTime;
        try {
            eventTime =
                    payload.getTime() == null || payload.getTime().isBlank()
                            ? null
                            : Instant.parse(payload.getTime());
        } catch (RuntimeException invalidTime) {
            throw new AppException(OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
        }
        if (eventTime != null && eventTime.isAfter(clock.instant().plusSeconds(300))) {
            throw new AppException(OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
        }
        boolean deliveryCallback = nextStatus == ShipmentStatus.DELIVERED;
        if (deliveryCallback && eventTime == null) {
            throw new AppException(OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
        }

        // Carrier HTTP happens before the short database transaction.
        VerifiedGhnShipment verified =
                transferCallback || deliveryCallback
                        ? ghnClient.getVerifiedShipment(orderCode)
                        : null;
        if (verified != null
                && (!orderCode.equals(verified.orderCode())
                        || !payload.getShopId().equals(verified.shopId())
                        || deliveryCallback && !"delivered".equals(verified.status())
                        || payload.getCodAmount() != null
                                && payload.getCodAmount().compareTo(verified.codAmount()) != 0)) {
            throw new AppException(OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
        }
        if ((transferCallback || deliveryCallback) && verified == null) {
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        }

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        transaction -> {
                            Order order =
                                    orderRepository
                                            .findByIdForUpdate(orderId)
                                            .orElseThrow(
                                                    () ->
                                                            new AppException(
                                                                    OrderErrorCode
                                                                            .ORDER_NOT_FOUND));
                            Shipment shipment =
                                    shipmentRepository
                                            .findByOrderIdForUpdate(orderId)
                                            .orElseThrow(
                                                    () ->
                                                            new AppException(
                                                                    OrderErrorCode
                                                                            .SHIPMENT_EVIDENCE_INVALID));
                            if (shipment.getProvider() != ShipmentProvider.GHN
                                    || !orderCode.equals(shipment.getGhnOrderCode())
                                            && !orderCode.equals(shipment.getTrackingCode())
                                    || payload.getClientOrderCode() != null
                                            && !payload.getClientOrderCode().isBlank()
                                            && !payload.getClientOrderCode()
                                                    .equals(order.getOrderCode())
                                    || verified != null
                                            && !Objects.equals(
                                                    order.getOrderCode(),
                                                    verified.clientOrderCode())) {
                                throw new AppException(OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
                            }

                            boolean newCollection = false;
                            if (verified != null && order.getPaymentMethod() == PaymentMethod.COD) {
                                if (!"VND".equals(order.getCurrency())
                                        || verified.codAmount() == null
                                        || verified.codAmount().signum() <= 0
                                        || verified.codAmount().compareTo(order.getTotalAmount())
                                                != 0) {
                                    throw new AppException(
                                            OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
                                }
                                shipment.setCodAmount(verified.codAmount());
                                if (verified.codCollected()) {
                                    Instant collectedAt =
                                            verified.codCollectedAt() != null
                                                    ? verified.codCollectedAt()
                                                    : deliveryCallback
                                                            ? eventTime
                                                            : shipment.getDeliveredAt();
                                    if (collectedAt == null
                                            || collectedAt.isAfter(
                                                    clock.instant().plusSeconds(300))) {
                                        throw new AppException(
                                                OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
                                    }
                                    if (shipment.getCodCollectedAt() == null) {
                                        shipment.setCodCollectedAt(collectedAt);
                                        newCollection = true;
                                    }
                                    if (!"TRANSFERRED".equals(shipment.getCodStatus())) {
                                        shipment.setCodStatus("COLLECTED");
                                    }
                                }
                                if (transferCallback) {
                                    if (!verified.codCollected()
                                            || !verified.codTransferred()
                                            || verified.codTransferredAt() == null
                                            || verified.codTransferredAt()
                                                    .isAfter(clock.instant().plusSeconds(300))
                                            || verified.codTransferredAt()
                                                    .isBefore(shipment.getCodCollectedAt())) {
                                        throw new AppException(
                                                OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
                                    }
                                    shipment.setCodStatus("TRANSFERRED");
                                    if (shipment.getCodTransferredAt() == null) {
                                        shipment.setCodTransferredAt(verified.codTransferredAt());
                                    }
                                }
                            } else if (verified != null) {
                                if (transferCallback
                                        || verified.codAmount() == null
                                        || verified.codAmount().signum() != 0) {
                                    throw new AppException(
                                            OrderErrorCode.SHIPMENT_EVIDENCE_INVALID);
                                }
                                shipment.setCodAmount(BigDecimal.ZERO);
                                shipment.setCodStatus("NOT_APPLICABLE");
                            }

                            if (transferCallback) {
                                if (newCollection) {
                                    outboxEventRecorder.saveMessage(
                                            orderId,
                                            EventTypes.ORDER_DELIVERED,
                                            EventEnvelope.v1(
                                                    EventTypes.ORDER_DELIVERED,
                                                    orderId,
                                                    orderId,
                                                    new OrderDeliveredEvent(
                                                            orderId,
                                                            order.getDeliveredAt() == null
                                                                    ? null
                                                                    : LocalDateTime.ofInstant(
                                                                            order.getDeliveredAt(),
                                                                            ZoneOffset.UTC),
                                                            shipment.getCodAmount(),
                                                            shipment.getCodCollectedAt(),
                                                            orderCode,
                                                            verified.shopId())));
                                }
                                shipmentRepository.save(shipment);
                                return;
                            }

                            boolean handoff =
                                    Set.of(
                                                    ShipmentStatus.PICKED,
                                                    ShipmentStatus.SHIPPING,
                                                    ShipmentStatus.DELIVERED,
                                                    ShipmentStatus.RETURNING,
                                                    ShipmentStatus.RETURNED)
                                            .contains(nextStatus);
                            if (Set.of(OrderStatus.CANCELLING, OrderStatus.CANCELLED)
                                            .contains(order.getStatus())
                                    && handoff) {
                                if (shipment.getHandedOffAt() == null) {
                                    shipment.setHandedOffAt(eventTime);
                                }
                                shipment.setStatus(nextStatus);
                                if (newCollection) {
                                    outboxEventRecorder.saveMessage(
                                            orderId,
                                            EventTypes.ORDER_DELIVERED,
                                            EventEnvelope.v1(
                                                    EventTypes.ORDER_DELIVERED,
                                                    orderId,
                                                    orderId,
                                                    new OrderDeliveredEvent(
                                                            orderId,
                                                            LocalDateTime.ofInstant(
                                                                    eventTime, ZoneOffset.UTC),
                                                            shipment.getCodAmount(),
                                                            shipment.getCodCollectedAt(),
                                                            orderCode,
                                                            verified.shopId())));
                                }
                                shipmentRepository.save(shipment);
                                orderCancellation.lateHandoff(
                                        orderId,
                                        "GHN status " + payload.getStatus() + " for " + orderCode);
                                return;
                            }
                            if (newCollection) {
                                shipmentRepository.save(shipment);
                                orderReceiptService.markDelivered(
                                        orderId,
                                        eventTime,
                                        "GHN",
                                        shipment.getCodAmount(),
                                        shipment.getCodCollectedAt(),
                                        orderCode,
                                        verified.shopId());
                            }
                            if (!canTransition(shipment.getStatus(), nextStatus)) {
                                return;
                            }
                            shipment.setStatus(nextStatus);
                            if (handoff && shipment.getHandedOffAt() == null) {
                                shipment.setHandedOffAt(eventTime);
                            }
                            if (payload.getWeight() != null && payload.getWeight() > 0) {
                                shipment.setWeightGram(payload.getWeight());
                            }
                            // Carrier đã lấy hàng (picked) là đơn đang giao — không chờ tới storing/delivering
                            if ((nextStatus == ShipmentStatus.PICKED
                                            || nextStatus == ShipmentStatus.SHIPPING)
                                    && Set.of(
                                                    OrderStatus.CONFIRMED,
                                                    OrderStatus.PACKED,
                                                    OrderStatus.PROCESSING)
                                            .contains(order.getStatus())) {
                                OrderStatus before = order.getStatus();
                                order.setStatus(OrderStatus.SHIPPING);
                                orderRepository.save(order);
                                recordHistory(
                                        order,
                                        before,
                                        OrderStatus.SHIPPING,
                                        "GHN_WEBHOOK",
                                        "GHN",
                                        payload.getStatus());
                                orderNotificationService.sendOrderShippedNotification(
                                        order, shipment);
                            } else if (deliveryCallback) {
                                if (shipment.getDeliveredAt() == null) {
                                    shipment.setDeliveredAt(eventTime);
                                }
                                if (payload.getPodUrl() != null
                                        && !payload.getPodUrl().isBlank()
                                        && payload.getPodUrl().length() <= 1000) {
                                    shipment.setPodUrl(payload.getPodUrl());
                                }
                                if (!newCollection) {
                                    orderReceiptService.markDelivered(
                                            orderId, eventTime, "GHN", null, null, null, null);
                                }
                            } else if (nextStatus == ShipmentStatus.RETURNED
                                    && order.getStatus() != OrderStatus.RETURNED
                                    && order.getStatus() != OrderStatus.CANCELLED
                                    && order.getStatus() != OrderStatus.REFUNDED) {
                                OrderStatus before = order.getStatus();
                                order.setStatus(OrderStatus.RETURNED);
                                orderRepository.save(order);
                                recordHistory(
                                        order,
                                        before,
                                        OrderStatus.RETURNED,
                                        "GHN_WEBHOOK",
                                        "GHN",
                                        payload.getStatus());
                            }
                            shipmentRepository.save(shipment);
                        });
    }

    private int calculateTotalWeight(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return 200;
        }
        try {
            List<String> variantIds = items.stream().map(OrderItem::getVariantId).toList();
            Map<String, Integer> weightMap =
                    catalogClient.getVariantsBatch(variantIds).stream()
                            .collect(
                                    Collectors.toMap(
                                            ProductVariantDto::getVariantId,
                                            v ->
                                                    v.getWeightGram() != null
                                                                    && v.getWeightGram() > 0
                                                            ? v.getWeightGram()
                                                            : 200,
                                            (a, b) -> a));
            return items.stream()
                    .mapToInt(
                            item ->
                                    weightMap.getOrDefault(item.getVariantId(), 200)
                                            * item.getQuantity())
                    .sum();
        } catch (Exception e) {
            log.warn(
                    "[Shipment] Failed to fetch variant weights from catalog; defaulting to 200g"
                            + " per item: {}",
                    e.getMessage());
            return items.stream().mapToInt(item -> 200 * item.getQuantity()).sum();
        }
    }

    private ShipmentStatus mapGhnStatus(String ghnStatus) {
        if (ghnStatus == null) {
            return null;
        }
        return switch (ghnStatus.toLowerCase().trim()) {
            case "ready_to_pick", "picking", "money_collect_picking" -> ShipmentStatus.PENDING;
            case "picked" -> ShipmentStatus.PICKED;
            case "storing", "delivering", "transporting" -> ShipmentStatus.SHIPPING;
            case "delivered" -> ShipmentStatus.DELIVERED;
            case "cancel" -> ShipmentStatus.CANCELLED;
            case "return" -> ShipmentStatus.RETURNING;
            case "returned" -> ShipmentStatus.RETURNED;
            default -> null;
        };
    }

    private ShipmentResponse toResponse(Shipment shipment) {
        return ShipmentResponse.builder()
                .id(shipment.getId())
                .orderId(shipment.getOrder() != null ? shipment.getOrder().getId() : null)
                .provider(shipment.getProvider())
                .trackingCode(shipment.getTrackingCode())
                .ghnOrderCode(shipment.getGhnOrderCode())
                .fee(shipment.getFee())
                .weightGram(shipment.getWeightGram())
                .status(shipment.getStatus())
                .codStatus(shipment.getCodStatus())
                .codAmount(shipment.getCodAmount())
                .codCollectedAt(shipment.getCodCollectedAt())
                .codTransferredAt(shipment.getCodTransferredAt())
                .deliveredAt(shipment.getDeliveredAt())
                .handedOffAt(shipment.getHandedOffAt())
                .podUrl(shipment.getPodUrl())
                .createdAt(shipment.getCreatedAt())
                .updatedAt(shipment.getUpdatedAt())
                .build();
    }

    private boolean canTransition(ShipmentStatus current, ShipmentStatus next) {

        if (current == null || next == null) {
            return false;
        }
        if (current == next) {
            return true;
        }
        // Terminal states cannot transition to anything
        if (current == ShipmentStatus.DELIVERED
                || current == ShipmentStatus.RETURNED
                || current == ShipmentStatus.CANCELLED) {
            return false;
        }
        return switch (current) {
            case PENDING ->
                    next == ShipmentStatus.PICKED
                            || next == ShipmentStatus.SHIPPING
                            || next == ShipmentStatus.DELIVERED
                            || next == ShipmentStatus.RETURNING
                            || next == ShipmentStatus.RETURNED
                            || next == ShipmentStatus.CANCELLED;
            case PICKED ->
                    next == ShipmentStatus.SHIPPING
                            || next == ShipmentStatus.DELIVERED
                            || next == ShipmentStatus.RETURNING
                            || next == ShipmentStatus.RETURNED
                            || next == ShipmentStatus.CANCELLED;
            case SHIPPING ->
                    next == ShipmentStatus.DELIVERED
                            || next == ShipmentStatus.RETURNING
                            || next == ShipmentStatus.RETURNED
                            || next == ShipmentStatus.CANCELLED;
            case RETURNING -> next == ShipmentStatus.RETURNED;
            default -> false;
        };
    }

    private void recordHistory(
            Order order,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String action,
            String changedBy,
            String reason) {
        if (orderStatusHistoryRepository == null) {
            return;
        }
        OrderStatusHistory history =
                OrderStatusHistory.builder()
                        .order(order)
                        .fromStatus(fromStatus)
                        .toStatus(toStatus)
                        .action(action)
                        .changedBy(changedBy != null && !changedBy.isBlank() ? changedBy : "SYSTEM")
                        .reason(reason)
                        .build();
        orderStatusHistoryRepository.save(history);
    }
}
