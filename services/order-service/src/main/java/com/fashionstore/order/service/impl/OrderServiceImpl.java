package com.fashionstore.order.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.order.config.ErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.dto.CreateOrderRequest;
import com.fashionstore.order.dto.OrderItemResponse;
import com.fashionstore.order.dto.OrderResponse;
import com.fashionstore.order.dto.UpdateOrderStatusRequest;
import com.fashionstore.order.entity.*;
import com.fashionstore.contracts.EventEnvelope;
import com.fashionstore.contracts.EventTypes;
import com.fashionstore.contracts.inventory.InventoryItem;
import com.fashionstore.contracts.inventory.InventoryReservationRequested;
import com.fashionstore.order.repository.CheckoutRepository;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.service.OrderService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderServiceImpl implements OrderService {

    OrderRepository orderRepository;
    CheckoutRepository checkoutRepository;
    CurrentUserProvider currentUserProvider;
    ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public OrderResponse placeOrder(String checkoutId, String idempotencyKey, CreateOrderRequest request) {
        String userId = currentUserProvider.getCurrentUserId();
        String effectiveIdempotencyKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? checkoutId
                : idempotencyKey.trim();

        Order existingOrder = orderRepository.findByUserIdAndIdempotencyKey(userId, effectiveIdempotencyKey)
                .orElse(null);
        if (existingOrder != null) {
            String existingCheckoutId = existingOrder.getCheckout() == null ? checkoutId : existingOrder.getCheckout().getId();
            return toResponse(existingOrder, existingCheckoutId);
        }

        Checkout checkout = checkoutRepository.findForUpdateByIdAndUserId(checkoutId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CHECKOUT_NOT_FOUND));

        if (checkout.getOrder() != null) {
            return toResponse(checkout.getOrder(), checkoutId);
        }
        if (checkout.getStatus() == CheckoutStatus.CANCELLED || checkout.getStatus() == CheckoutStatus.EXPIRED) {
            throw new AppException(ErrorCode.CHECKOUT_STATUS_INVALID);
        }

        List<CheckoutItem> checkoutItems = checkout.getItems();
        if (checkoutItems == null || checkoutItems.isEmpty()) {
            throw new AppException(ErrorCode.CHECKOUT_NOT_FOUND);
        }

        Order order = Order.builder()
                .orderCode(generateOrderCode())
                .userId(userId)
                .idempotencyKey(effectiveIdempotencyKey)
                .status(OrderStatus.PENDING_INVENTORY)
                .recipientName(request.getRecipientName())
                .recipientPhone(request.getRecipientPhone())
                .shippingAddress(request.getShippingAddress())
                .subtotalAmount(checkout.getSubtotalAmount())
                .discountAmount(checkout.getDiscountAmount())
                .shippingFee(checkout.getShippingFee())
                .totalAmount(checkout.getTotalAmount())
                .build();

        List<OrderItem> orderItems = checkoutItems.stream()
                .map(item -> buildOrderItem(order, item))
                .toList();

        order.setItems(orderItems);
        orderRepository.save(order);

        checkout.setOrder(order);
        checkout.setStatus(CheckoutStatus.COMPLETED);
        checkoutRepository.save(checkout);

        requestInventoryReservation(order, checkoutItems);

        return toResponse(order, checkoutId);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getMyOrderById(String orderId) {
        String userId = currentUserProvider.getCurrentUserId();
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }
        String checkoutId = order.getCheckout() == null ? null : order.getCheckout().getId();
        return toResponse(order, checkoutId);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse updateOrderStatus(String orderId, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        validateTransition(order.getStatus(), request.getStatus());
        order.setStatus(request.getStatus());
        order.setShippingProvider(request.getShippingProvider());
        order.setTrackingCode(request.getTrackingCode());

        Order saved = orderRepository.save(order);
        String checkoutId = saved.getCheckout() == null ? null : saved.getCheckout().getId();
        return toResponse(saved, checkoutId);
    }

    private void requestInventoryReservation(Order order, List<CheckoutItem> checkoutItems) {
        List<InventoryItem> items = checkoutItems.stream()
                .map(item -> new InventoryItem(item.getVariantId(), item.getQuantity()))
                .toList();
        eventPublisher.publishEvent(EventEnvelope.v1(
                EventTypes.INVENTORY_RESERVATION_REQUESTED,
                order.getId(),
                order.getId(),
                new InventoryReservationRequested(order.getId(), order.getUserId(), items)
        ));
    }

    private OrderItem buildOrderItem(Order order, CheckoutItem checkoutItem) {
        return OrderItem.builder()
                .order(order)
                .cartItemId(checkoutItem.getCartItemId())
                .variantId(checkoutItem.getVariantId())
                .productName(checkoutItem.getProductName())
                .size(checkoutItem.getSize())
                .color(checkoutItem.getColor())
                .unitPrice(checkoutItem.getUnitPrice())
                .quantity(checkoutItem.getQuantity())
                .lineTotal(checkoutItem.getLineTotal())
                .build();
    }

    private void validateTransition(OrderStatus current, OrderStatus target) {
        if (current == target) {
            return;
        }
        boolean valid = switch (current) {
            case CONFIRMED -> target == OrderStatus.PROCESSING;
            case PROCESSING -> target == OrderStatus.PACKED;
            case PACKED -> target == OrderStatus.SHIPPING;
            case SHIPPING -> target == OrderStatus.DELIVERED;
            case DELIVERED -> target == OrderStatus.COMPLETED || target == OrderStatus.RETURNED;
            case RETURNED -> target == OrderStatus.REFUNDED;
            default -> false;
        };

        if (!valid) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID);
        }
    }

    private String generateOrderCode() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "ORD-" + LocalDateTime.now().getYear() + "-" + suffix;
    }

    private OrderResponse toResponse(Order order, String checkoutId) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .variantId(item.getVariantId())
                        .productName(item.getProductName())
                        .size(item.getSize())
                        .color(item.getColor())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .lineTotal(item.getLineTotal())
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .checkoutId(checkoutId)
                .status(order.getStatus())
                .paymentId(order.getPaymentId())
                .sagaFailureReason(order.getSagaFailureReason())
                .recipientName(order.getRecipientName())
                .recipientPhone(order.getRecipientPhone())
                .shippingAddress(order.getShippingAddress())
                .shippingProvider(order.getShippingProvider())
                .trackingCode(order.getTrackingCode())
                .subtotalAmount(order.getSubtotalAmount())
                .discountAmount(order.getDiscountAmount())
                .shippingFee(order.getShippingFee())
                .totalAmount(order.getTotalAmount())
                .items(itemResponses)
                .build();
    }
}
