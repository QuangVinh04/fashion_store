package com.fashionstore.order.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.dto.*;
import com.fashionstore.order.entity.*;
import com.fashionstore.order.entity.enumeration.*;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Claims and commits shipment creation around provider HTTP; uncertainty retains the original
 * claim.
 */
@Service
@RequiredArgsConstructor
public class ShipmentCreationTransactionService {
    private final OrderRepository orders;
    private final ShipmentRepository shipments;
    private final OrderStatusHistoryRepository history;
    private final CurrentUserProvider user;
    private final OrderSagaRepository cancellations;

    public record Claim(
            Order order,
            UserAddressDto address,
            int weight,
            ShipmentProvider provider,
            String token,
            Shipment existing) {}

    @Transactional(readOnly = true)
    public Order snapshot(String id) {
        var order =
                orders.findWithItemsById(id)
                        .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        if (Set.of(OrderStatus.CANCELLING, OrderStatus.CANCELLED).contains(order.getStatus()))
            throw new AppException(OrderErrorCode.FULFILLMENT_BLOCKED);
        return order;
    }

    @Transactional
    public Claim claim(String id, CreateShipmentRequest request, int weight) {
        var order = locked(id);
        if (Set.of(OrderStatus.CANCELLING, OrderStatus.CANCELLED).contains(order.getStatus()))
            throw new AppException(OrderErrorCode.FULFILLMENT_BLOCKED);
        var existing = shipments.findByOrderId(id).orElse(null);
        if (existing != null)
            return new Claim(order, null, weight, existing.getProvider(), null, existing);
        if (!Set.of(OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.PACKED)
                .contains(order.getStatus()))
            throw new AppException(OrderErrorCode.ORDER_STATUS_INVALID);
        if (order.getShipmentInitiationToken() != null)
            throw new AppException(OrderErrorCode.SHIPMENT_INITIATION_CONFLICT);
        var address = order.getAddress();
        if (address == null || address.getProvinceId() == null || address.getWardId() == null)
            throw new AppException(OrderErrorCode.SHIPPING_ADDRESS_INVALID);
        var snapshot =
                UserAddressDto.builder()
                        .recipientName(address.getRecipientName())
                        .phone(address.getRecipientPhone())
                        .fullAddress(order.getShippingAddress())
                        .province(address.getProvince())
                        .ward(address.getWard())
                        .provinceId(address.getProvinceId())
                        .wardId(address.getWardId())
                        .districtId(address.getDistrictId())
                        .wardCode(address.getWardCode())
                        .build();
        String token = UUID.randomUUID().toString();
        order.setShipmentInitiationToken(token);
        order.setShipmentInitiationStartedAt(Instant.now());
        order.getItems().size();
        return new Claim(
                order,
                snapshot,
                weight,
                request != null && request.getProvider() != null
                        ? request.getProvider()
                        : ShipmentProvider.GHN,
                token,
                null);
    }

    @Transactional
    public Shipment finish(Claim claim, String tracking) {
        var order = locked(claim.order().getId());
        if (tracking == null || tracking.isBlank() || tracking.length() > 100)
            throw new AppException(OrderErrorCode.SHIPMENT_INITIATION_CONFLICT);
        if (!Objects.equals(order.getShipmentInitiationToken(), claim.token()))
            throw new AppException(OrderErrorCode.SHIPMENT_INITIATION_CONFLICT);
        var existing = shipments.findByOrderId(order.getId()).orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.getTrackingCode(), tracking))
                throw new AppException(OrderErrorCode.SHIPMENT_INITIATION_CONFLICT);
            return existing;
        }
        var shipment =
                shipments.save(
                        Shipment.builder()
                                .order(order)
                                .provider(claim.provider())
                                .trackingCode(tracking)
                                .ghnOrderCode(tracking)
                                .toDistrictId(claim.address().getDistrictId())
                                .toWardCode(claim.address().getWardCode())
                                .weightGram(claim.weight())
                                .fee(order.getShippingFee())
                                .status(ShipmentStatus.PENDING)
                                .build());
        order.setTrackingCode(tracking);
        order.setShippingProvider(claim.provider().name());
        order.setShipmentInitiationToken(null);
        if (Set.of(OrderStatus.CANCELLING, OrderStatus.CANCELLED).contains(order.getStatus())) {
            record(
                    order,
                    "SHIPMENT_CREATED_DURING_CANCELLATION",
                    "Carrier label requires cancellation; fulfillment remains blocked");
            cancellations
                    .findByOrderIdAndTypeForUpdate(order.getId(), OrderSagaType.CANCELLATION)
                    .ifPresent(
                            c -> {
                                if (!"CARRIER_HANDOFF".equals(c.getFailureCode())) {
                                    c.fail(
                                            c.getCurrentStep() == OrderSagaStep.STOP_SHIPMENT
                                                    ? "CARRIER_UNKNOWN"
                                                    : "CARRIER_LABEL_CONFLICT",
                                            "Carrier label was created during cancellation and must"
                                                + " be reconciled");
                                }
                            });
        } else if (order.getStatus() == OrderStatus.PROCESSING) {
            var before = order.getStatus();
            order.setStatus(OrderStatus.PACKED);
            history.save(
                    OrderStatusHistory.builder()
                            .order(order)
                            .fromStatus(before)
                            .toStatus(OrderStatus.PACKED)
                            .action("SHIPMENT_CREATED")
                            .changedBy(actor())
                            .reason("Shipment created: " + tracking)
                            .build());
        }
        orders.save(order);
        return shipment;
    }

    @Transactional
    public void unknown(String id, String token) {
        var order = locked(id);
        if (!Objects.equals(order.getShipmentInitiationToken(), token)) return;
        record(
                order,
                "SHIPMENT_CREATION_UNKNOWN",
                "Provider creation result needs verification before another attempt");
        cancellations
                .findByOrderIdAndTypeForUpdate(id, OrderSagaType.CANCELLATION)
                .filter(
                        c ->
                                c.getStatus() != OrderSagaStatus.COMPLETED
                                        && !"CARRIER_HANDOFF".equals(c.getFailureCode()))
                .ifPresent(
                        c -> {
                            c.fail(
                                    "CARRIER_UNKNOWN",
                                    "Shipment creation result is uncertain; fulfillment remains"
                                            + " blocked");
                        });
    }

    private Order locked(String id) {
        return orders.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private String actor() {
        try {
            String actor = user.getCurrentUserId();
            return actor == null ? "ADMIN" : actor;
        } catch (RuntimeException error) {
            return "ADMIN";
        }
    }

    private void record(Order order, String action, String reason) {
        history.save(
                OrderStatusHistory.builder()
                        .order(order)
                        .fromStatus(order.getStatus())
                        .toStatus(order.getStatus())
                        .action(action)
                        .changedBy(actor())
                        .reason(reason)
                        .build());
    }
}
