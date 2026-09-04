package com.fashionstore.order.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.order.config.ErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.config.messaging.RabbitMQNames;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.order.dto.CancelOrderRequest;
import com.fashionstore.order.dto.CreateOrderRequest;
import com.fashionstore.order.dto.OrderItemResponse;
import com.fashionstore.order.dto.OrderResponse;
import com.fashionstore.order.dto.OrderSagaResponse;
import com.fashionstore.order.dto.OrderSummaryResponse;
import com.fashionstore.order.dto.ReturnOrderRequest;
import com.fashionstore.order.dto.UpdateOrderStatusRequest;
import com.fashionstore.order.messaging.SagaCancellationService;
import com.fashionstore.order.messaging.SagaCommands;
import com.fashionstore.order.messaging.SagaOutbox;
import com.fashionstore.order.model.*;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.inventory.command.InventoryItem;
import com.fashionstore.contracts.inventory.command.ReservationInventoryCommand;
import com.fashionstore.order.model.enumeration.CheckoutStatus;
import com.fashionstore.order.model.enumeration.OrderStatus;
import com.fashionstore.order.repository.CheckoutRepository;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.OrderSagaRepository;
import com.fashionstore.order.service.OrderService;
import com.fashionstore.order.outbox.OutboxService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    OrderSagaRepository orderSagaRepository;
    OutboxService outboxService;
    SagaOutbox sagaOutbox;
    SagaCancellationService sagaCancellationService;
    CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public OrderResponse createOrder(String checkoutId, String idempotencyKey, CreateOrderRequest request) {
        String userId = currentUserProvider.getCurrentUserId();
        String effectiveIdempotencyKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? checkoutId
                : idempotencyKey.trim();

        Order existingOrder = orderRepository.findByUserIdAndIdempotencyKey(userId, effectiveIdempotencyKey)
                .orElse(null);
        if (existingOrder != null) {
            return toResponse(existingOrder, existingOrder.getCheckoutId());
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
                .paymentMethod(checkout.getPaymentMethod())
                .paymentProvider(checkout.getPaymentProvider())
                .userId(userId)
                .idempotencyKey(effectiveIdempotencyKey)
                .status(OrderStatus.PENDING)
                .checkoutId(checkout.getId())
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

        OrderSaga saga = OrderSaga.start(order.getId());
        orderSagaRepository.save(saga);

        outboxService.saveMessage(
                order.getId(),
                EventTypes.INVENTORY_RESERVATION_REQUESTED,
                reserveInventory(order, saga, checkoutItems)
        );

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
        return toResponse(order, order.getCheckoutId());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<OrderSummaryResponse>> getMyOrders(OrderStatus status, Pageable pageable) {
        String userId = currentUserProvider.getCurrentUserId();
        Page<Order> orders = status == null
                ? orderRepository.findByUserId(userId, pageable)
                : orderRepository.findByUserIdAndStatus(userId, status, pageable);
        return toPageResponse(orders, pageable);
    }

    @Override
    @Transactional
    public OrderResponse cancelMyOrder(String orderId, CancelOrderRequest request) {
        String userId = currentUserProvider.getCurrentUserId();
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return toResponse(order, order.getCheckoutId());   // hủy hai lần vẫn ra cùng kết quả
        }
        // Đơn đã qua CONFIRMED là chuyện hoàn tiền / hủy giao, không còn là bù trừ saga.
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new AppException(ErrorCode.ORDER_CANNOT_BE_CANCELLED);
        }

        String reason = reasonOrDefault(request);

        // Khóa saga trước, orders sau — cùng thứ tự với handler saga.
        OrderSaga saga = orderSagaRepository.findByOrderIdForUpdate(orderId).orElse(null);
        Order locked = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (saga == null) {
            // Đơn cũ tạo trước khi có saga: không có gì để bù trừ.
            locked.cancel(reason);
            orderRepository.save(locked);
            return toResponse(locked, locked.getCheckoutId());
        }

        SagaCancellationService.Outcome outcome = sagaCancellationService.cancel(saga, locked, reason);
        if (outcome == SagaCancellationService.Outcome.NOT_ALLOWED) {
            throw new AppException(ErrorCode.ORDER_CANNOT_BE_CANCELLED);
        }
        return toResponse(locked, locked.getCheckoutId());
    }

    @Override
    @Transactional
    public OrderResponse requestReturn(String orderId, ReturnOrderRequest request) {
        String userId = currentUserProvider.getCurrentUserId();
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() == OrderStatus.RETURNED) {
            return toResponse(order, order.getCheckoutId());   // yêu cầu hai lần vẫn ra cùng kết quả
        }
        // Chỉ nhận trả hàng sau khi đã giao — trước đó khách dùng đường hủy đơn (cancelMyOrder).
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new AppException(ErrorCode.ORDER_RETURN_NOT_ALLOWED);
        }

        String reason = request == null || request.getReason() == null || request.getReason().isBlank()
                ? "Khách hàng yêu cầu trả hàng"
                : request.getReason().trim();

        Order locked = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        locked.setStatus(OrderStatus.RETURNED);
        locked.setCancelReason(reason);
        orderRepository.save(locked);
        return toResponse(locked, locked.getCheckoutId());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<List<OrderSummaryResponse>> searchOrders(String userId, OrderStatus status, Pageable pageable) {
        Page<Order> orders;
        if (userId != null && status != null) {
            orders = orderRepository.findByUserIdAndStatus(userId, status, pageable);
        } else if (userId != null) {
            orders = orderRepository.findByUserId(userId, pageable);
        } else if (status != null) {
            orders = orderRepository.findByStatus(status, pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }
        return toPageResponse(orders, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse getOrderById(String orderId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        return toResponse(order, order.getCheckoutId());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public OrderSagaResponse getOrderSaga(String orderId) {
        OrderSaga saga = orderSagaRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_SAGA_NOT_FOUND));
        return OrderSagaResponse.builder()
                .sagaId(saga.getId())
                .orderId(saga.getOrderId())
                .status(saga.getStatus())
                .currentStep(saga.getCurrentStep())
                .inventoryReservationId(saga.getInventoryReservationId())
                .paymentId(saga.getPaymentId())
                .failureCode(saga.getFailureCode())
                .failureReason(saga.getFailureReason())
                .retryCount(saga.getRetryCount())
                .stepDeadline(saga.getStepDeadline())
                .createdAt(saga.getCreatedAt())
                .updatedAt(saga.getUpdatedAt())
                .completedAt(saga.getCompletedAt())
                .build();
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse updateOrderStatus(String orderId, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        validateTransition(order.getStatus(), request.getStatus());

        // REFUNDED không phải một cột cần set — nó là kết quả của một reply từ payment-service.
        // Đơn giữ nguyên RETURNED cho tới khi RefundEventListener nhận payment.refunded xác nhận.
        if (request.getStatus() == OrderStatus.REFUNDED) {
            sagaOutbox.emit(order.getId(), order.getId(),
                    SagaCommands.refundPayment(order, order.getTotalAmount(), order.getCancelReason()));
            return toResponse(order, order.getCheckoutId());
        }

        order.setStatus(request.getStatus());
        order.setShippingProvider(request.getShippingProvider());
        order.setTrackingCode(request.getTrackingCode());

        Order saved = orderRepository.save(order);
        return toResponse(saved, saved.getCheckoutId());
    }

    /** correlationId = sagaId ngay từ command đầu tiên, để mọi reply về đúng một saga instance. */
    private EventEnvelope<ReservationInventoryCommand> reserveInventory(Order order, OrderSaga saga, List<CheckoutItem> checkoutItems) {
        List<InventoryItem> items = checkoutItems.stream()
                .map(item -> new InventoryItem(item.getVariantId(), item.getQuantity()))
                .toList();
        return EventEnvelope.v1(
                EventTypes.INVENTORY_RESERVATION_REQUESTED,
                order.getId(),
                saga.getId(),
                new ReservationInventoryCommand(order.getId(), order.getUserId(), items)
        );
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
        // Đơn PENDING còn nằm trong saga — admin không được can thiệp giữa chừng.
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

    private String reasonOrDefault(CancelOrderRequest request) {
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            return "Khách hàng yêu cầu hủy đơn";
        }
        return request.getReason().trim();
    }

    private PageResponse<List<OrderSummaryResponse>> toPageResponse(Page<Order> orders, Pageable pageable) {
        List<OrderSummaryResponse> items = orders.stream()
                .map(this::toSummary)
                .toList();
        return PageResponse.<List<OrderSummaryResponse>>builder()
                .pageNo(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalPage(orders.getTotalPages())
                .items(items)
                .build();
    }

    private OrderSummaryResponse toSummary(Order order) {
        return OrderSummaryResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentProvider(order.getPaymentProvider())
                .currency(order.getCurrency())
                .totalAmount(order.getTotalAmount())
                .cancelReason(order.getCancelReason())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
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
                .paymentMethod(order.getPaymentMethod())
                .paymentProvider(order.getPaymentProvider())
                .currency(order.getCurrency())
                .cancelReason(order.getCancelReason())
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
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
