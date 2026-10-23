package com.fashionstore.order.shipment.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.shipment.dto.CreateShipmentRequest;
import com.fashionstore.order.shared.dto.UserAddressDto;
import com.fashionstore.order.order.entity.Order;
import com.fashionstore.order.order.entity.OrderStatusHistory;
import com.fashionstore.order.shipment.entity.Shipment;
import com.fashionstore.order.saga.entity.OrderSagaStatus;
import com.fashionstore.order.saga.entity.OrderSagaStep;
import com.fashionstore.order.saga.entity.OrderSagaType;
import com.fashionstore.order.order.entity.OrderStatus;
import com.fashionstore.order.shipment.entity.ShipmentProvider;
import com.fashionstore.order.shipment.entity.ShipmentStatus;
import com.fashionstore.order.shared.exception.OrderErrorCode;
import com.fashionstore.order.order.repository.OrderRepository;
import com.fashionstore.order.saga.repository.OrderSagaRepository;
import com.fashionstore.order.order.repository.OrderStatusHistoryRepository;
import com.fashionstore.order.shipment.repository.ShipmentRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Claims and commits shipment creation around provider HTTP; a failed call releases the claim so
 * the admin can retry (GHN dedupes by client_order_code).
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
            Shipment existing,
            String clientOrderCode) {}

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
        if (existing != null && !recreatable(existing))
            return new Claim(order, null, weight, existing.getProvider(), null, existing, null);
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
                null,
                // Tạo lại sau khi GHN huỷ: mã mới để GHN không gộp vào vận đơn cũ, nhưng cố định
                // giữa các lần bấm lại nên GHN vẫn chống trùng được.
                existing == null
                        ? order.getOrderCode()
                        : order.getOrderCode() + "-" + existing.getTrackingCode());
    }

    @Transactional
    public Shipment finish(Claim claim, String tracking) {
        var order = locked(claim.order().getId());
        if (tracking == null || tracking.isBlank() || tracking.length() > 100)
            throw new AppException(OrderErrorCode.SHIPMENT_INITIATION_CONFLICT);
        if (!Objects.equals(order.getShipmentInitiationToken(), claim.token()))
            throw new AppException(OrderErrorCode.SHIPMENT_INITIATION_CONFLICT);
        var existing = shipments.findByOrderId(order.getId()).orElse(null);
        if (existing != null && !recreatable(existing)) {
            if (!Objects.equals(existing.getTrackingCode(), tracking))
                throw new AppException(OrderErrorCode.SHIPMENT_INITIATION_CONFLICT);
            return existing;
        }
        // Mỗi đơn một dòng shipment: vận đơn tạo lại ghi đè dòng đã huỷ
        var shipment = existing != null ? existing : Shipment.builder().order(order).build();
        shipment.setProvider(claim.provider());
        shipment.setTrackingCode(tracking);
        shipment.setGhnOrderCode(tracking);
        shipment.setToDistrictId(claim.address().getDistrictId());
        shipment.setToWardCode(claim.address().getWardCode());
        shipment.setWeightGram(claim.weight());
        shipment.setFee(order.getShippingFee());
        shipment.setStatus(ShipmentStatus.PENDING);
        shipment.setCodStatus("PENDING");
        shipment.setCodAmount(null);
        shipment.setCodCollectedAt(null);
        shipment.setCodTransferredAt(null);
        shipment.setDeliveredAt(null);
        shipment.setPodUrl(null);
        shipment = shipments.save(shipment);
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
        order.setShipmentInitiationToken(null);
        record(
                order,
                "SHIPMENT_CREATION_UNKNOWN",
                "Provider creation failed; retry reuses the same client_order_code");
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

    /** Vận đơn GHN đã huỷ trước khi lấy hàng thì được tạo lại. */
    private static boolean recreatable(Shipment shipment) {
        return shipment.getStatus() == ShipmentStatus.CANCELLED && shipment.getHandedOffAt() == null;
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
