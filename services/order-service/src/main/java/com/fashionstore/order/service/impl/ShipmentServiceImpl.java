package com.fashionstore.order.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.client.CatalogClient;
import com.fashionstore.order.client.GhnClient;
import com.fashionstore.order.client.IdentityClient;
import com.fashionstore.order.dto.CreateShipmentRequest;
import com.fashionstore.order.dto.ProductVariantDto;
import com.fashionstore.order.dto.ShipmentResponse;
import com.fashionstore.order.dto.UserAddressDto;
import com.fashionstore.order.dto.ghn.GhnWebhookPayload;
import com.fashionstore.order.entity.Checkout;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderItem;
import com.fashionstore.order.entity.Shipment;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.entity.enumeration.ShipmentProvider;
import com.fashionstore.order.entity.enumeration.ShipmentStatus;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.repository.CheckoutRepository;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.ShipmentRepository;
import com.fashionstore.order.service.ShipmentService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShipmentServiceImpl implements ShipmentService {

    ShipmentRepository shipmentRepository;
    OrderRepository orderRepository;
    CheckoutRepository checkoutRepository;
    IdentityClient identityClient;
    GhnClient ghnClient;
    CatalogClient catalogClient;
    CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public ShipmentResponse createShipment(String orderId, CreateShipmentRequest request) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        // Kiểm tra xem đã có vận đơn cho đơn này chưa
        Shipment existing = shipmentRepository.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            log.info("[Shipment] Order {} already has shipment id={}, returning existing shipment", orderId, existing.getId());
            return toResponse(existing);
        }

        // Chỉ cho phép tạo vận đơn từ trạng thái hợp lệ
        if (order.getStatus() != OrderStatus.CONFIRMED
                && order.getStatus() != OrderStatus.PROCESSING
                && order.getStatus() != OrderStatus.PACKED) {
            throw new AppException(OrderErrorCode.ORDER_STATUS_INVALID);
        }

        Integer toDistrictId = (request != null) ? request.getToDistrictId() : null;
        String toWardCode = (request != null && request.getToWardCode() != null && !request.getToWardCode().isBlank())
                ? request.getToWardCode().trim() : null;

        if ((toDistrictId == null || toWardCode == null) && order.getCheckoutId() != null) {
            Checkout checkout = checkoutRepository.findById(order.getCheckoutId()).orElse(null);
            if (checkout != null && checkout.getAddressId() != null && !checkout.getAddressId().isBlank()) {
                try {
                    UserAddressDto addressDto = identityClient.getAddress(checkout.getAddressId());
                    if (addressDto != null) {
                        if (toDistrictId == null) {
                            toDistrictId = addressDto.getDistrictId();
                        }
                        if (toWardCode == null) {
                            toWardCode = addressDto.getWardCode();
                        }
                    }
                } catch (Exception e) {
                    log.warn("[Shipment] Could not fetch address from identity-service for addressId={}: {}",
                            checkout.getAddressId(), e.getMessage());
                }
            }
        }

        if (toDistrictId == null || toWardCode == null || toWardCode.isBlank()) {
            throw new AppException(OrderErrorCode.SHIPPING_ADDRESS_INVALID);
        }

        int totalWeightGram = calculateTotalWeight(order.getItems());
        UserAddressDto addressDto = UserAddressDto.builder()
                .recipientName(order.getRecipientName())
                .phone(order.getRecipientPhone())
                .fullAddress(order.getShippingAddress())
                .districtId(toDistrictId)
                .wardCode(toWardCode)
                .build();

        ShipmentProvider provider = (request != null && request.getProvider() != null)
                ? request.getProvider()
                : ShipmentProvider.GHN;

        String trackingCode = ghnClient.createOrder(order, addressDto, totalWeightGram);

        Shipment shipment = Shipment.builder()
                .order(order)
                .provider(provider)
                .trackingCode(trackingCode)
                .ghnOrderCode(trackingCode)
                .toDistrictId(toDistrictId)
                .toWardCode(toWardCode)
                .fee(order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO)
                .weightGram(totalWeightGram)
                .status(ShipmentStatus.PENDING)
                .build();

        Shipment saved = shipmentRepository.save(shipment);

        order.setShippingProvider(provider.name());
        order.setTrackingCode(trackingCode);
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            order.setStatus(OrderStatus.PACKED);
        }
        orderRepository.save(order);

        log.info("[Shipment] Created shipment id={} with trackingCode={} for orderId={}",
                saved.getId(), trackingCode, orderId);

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentByOrderId(String orderId) {
        String currentUserId = currentUserProvider.getCurrentUserId();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.getUserId().equals(currentUserId)) {
            throw new AppException(OrderErrorCode.ORDER_NOT_FOUND);
        }

        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        return toResponse(shipment);
    }

    @Override
    @Transactional
    public void handleGhnCallback(GhnWebhookPayload payload) {
        if (payload == null || payload.getOrderCode() == null || payload.getOrderCode().isBlank()) {
            log.warn("[GHN Webhook] Received empty order code payload");
            return;
        }

        String orderCode = payload.getOrderCode().trim();
        Shipment shipment = shipmentRepository.findByGhnOrderCode(orderCode)
                .or(() -> shipmentRepository.findByTrackingCode(orderCode))
                .orElse(null);

        if (shipment == null) {
            log.warn("[GHN Webhook] Shipment not found for GHN order code: {}", orderCode);
            return;
        }

        ShipmentStatus newStatus = mapGhnStatus(payload.getStatus());
        if (newStatus == null) {
            log.info("[GHN Webhook] Ignored unmapped GHN status: {} for orderCode: {}",
                    payload.getStatus(), orderCode);
            return;
        }

        if (!canTransition(shipment.getStatus(), newStatus)) {
            log.warn("[GHN Webhook] Ignored invalid status transition from {} to {} for orderCode: {}",
                    shipment.getStatus(), newStatus, orderCode);
            return;
        }

        shipment.setStatus(newStatus);
        if (payload.getWeight() != null && payload.getWeight() > 0) {
            shipment.setWeightGram(payload.getWeight());
        }
        shipmentRepository.save(shipment);

        Order order = shipment.getOrder();
        if (order != null) {
            if (newStatus == ShipmentStatus.SHIPPING && (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PACKED || order.getStatus() == OrderStatus.PROCESSING)) {
                order.setStatus(OrderStatus.SHIPPING);
                orderRepository.save(order);
                log.info("[GHN Webhook] Order {} transitioned to SHIPPING", order.getId());
            } else if (newStatus == ShipmentStatus.DELIVERED && (order.getStatus() == OrderStatus.SHIPPING || order.getStatus() == OrderStatus.PACKED || order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PROCESSING)) {
                order.setStatus(OrderStatus.DELIVERED);
                orderRepository.save(order);
                log.info("[GHN Webhook] Order {} transitioned to DELIVERED", order.getId());
            } else if (newStatus == ShipmentStatus.RETURNED && order.getStatus() != OrderStatus.RETURNED && order.getStatus() != OrderStatus.CANCELLED) {
                order.setStatus(OrderStatus.RETURNED);
                orderRepository.save(order);
                log.info("[GHN Webhook] Order {} transitioned to RETURNED", order.getId());
            }
        }
    }


    private int calculateTotalWeight(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return 200;
        }
        try {
            List<String> variantIds = items.stream().map(OrderItem::getVariantId).toList();
            Map<String, Integer> weightMap = catalogClient.getVariantsBatch(variantIds).stream()
                    .collect(Collectors.toMap(
                            ProductVariantDto::getVariantId,
                            v -> v.getWeightGram() != null && v.getWeightGram() > 0 ? v.getWeightGram() : 200,
                            (a, b) -> a
                    ));
            return items.stream()
                    .mapToInt(item -> weightMap.getOrDefault(item.getVariantId(), 200) * item.getQuantity())
                    .sum();
        } catch (Exception e) {
            log.warn("[Shipment] Failed to fetch variant weights from catalog; defaulting to 200g per item: {}", e.getMessage());
            return items.stream().mapToInt(item -> 200 * item.getQuantity()).sum();
        }
    }

    private ShipmentStatus mapGhnStatus(String ghnStatus) {
        if (ghnStatus == null) {
            return null;
        }
        return switch (ghnStatus.toLowerCase().trim()) {
            case "ready_to_pick", "picking" -> ShipmentStatus.PICKED;
            case "storing", "delivering", "transporting" -> ShipmentStatus.SHIPPING;
            case "delivered" -> ShipmentStatus.DELIVERED;
            case "cancel" -> ShipmentStatus.CANCELLED;
            case "return", "returned" -> ShipmentStatus.RETURNED;
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
        if (current == ShipmentStatus.DELIVERED || current == ShipmentStatus.RETURNED || current == ShipmentStatus.CANCELLED) {
            return false;
        }
        return switch (current) {
            case PENDING -> next == ShipmentStatus.PICKED || next == ShipmentStatus.SHIPPING || next == ShipmentStatus.DELIVERED || next == ShipmentStatus.RETURNED || next == ShipmentStatus.CANCELLED;
            case PICKED -> next == ShipmentStatus.SHIPPING || next == ShipmentStatus.DELIVERED || next == ShipmentStatus.RETURNED || next == ShipmentStatus.CANCELLED;
            case SHIPPING -> next == ShipmentStatus.DELIVERED || next == ShipmentStatus.RETURNED || next == ShipmentStatus.CANCELLED;
            default -> false;
        };
    }
}

