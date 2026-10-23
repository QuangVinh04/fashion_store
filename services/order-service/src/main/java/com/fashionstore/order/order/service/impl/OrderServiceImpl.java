package com.fashionstore.order.order.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.inventory.command.InventoryItem;
import com.fashionstore.contracts.inventory.command.ReservationInventoryCommand;
import com.fashionstore.contracts.order.dto.VerifyPurchaseRequest;
import com.fashionstore.contracts.order.dto.VerifyPurchaseResponse;
import com.fashionstore.order.saga.dto.CancelOrderRequest;
import com.fashionstore.order.order.dto.OrderPurchaseTab;
import com.fashionstore.order.order.dto.OrderResponse;
import com.fashionstore.order.returns.dto.OrderReturnReference;
import com.fashionstore.order.saga.dto.OrderSagaResponse;
import com.fashionstore.order.order.dto.OrderStatusHistoryResponse;
import com.fashionstore.order.order.dto.OrderSummaryResponse;
import com.fashionstore.order.promotion.dto.PromotionItemDto;
import com.fashionstore.order.order.dto.UpdateOrderStatusRequest;
import com.fashionstore.order.checkout.entity.Checkout;
import com.fashionstore.order.checkout.entity.CheckoutItem;
import com.fashionstore.order.order.entity.Order;
import com.fashionstore.order.order.entity.OrderItem;
import com.fashionstore.order.saga.entity.OrderSaga;
import com.fashionstore.order.order.entity.OrderStatusHistory;
import com.fashionstore.order.order.entity.ShippingAddress;
import com.fashionstore.order.checkout.entity.CheckoutStatus;
import com.fashionstore.order.saga.entity.OrderSagaType;
import com.fashionstore.order.order.entity.OrderStatus;
import com.fashionstore.order.shipment.entity.ShipmentStatus;
import com.fashionstore.order.shared.outbox.OutboxEventRecorder;
import com.fashionstore.order.saga.event.SagaOutbox;
import com.fashionstore.order.shared.exception.OrderErrorCode;
import com.fashionstore.order.checkout.repository.CheckoutRepository;
import com.fashionstore.order.order.repository.OrderRepository;
import com.fashionstore.order.saga.repository.OrderSagaRepository;
import com.fashionstore.order.order.repository.OrderStatusHistoryRepository;
import com.fashionstore.order.shipment.repository.ShipmentRepository;
import com.fashionstore.order.order.service.OrderNotificationService;
import com.fashionstore.order.order.service.OrderService;
import com.fashionstore.order.promotion.service.PromotionService;
import com.fashionstore.order.saga.service.SagaCancellationService;

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
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderServiceImpl implements OrderService {

    OrderRepository orderRepository;
    CheckoutRepository checkoutRepository;
    OrderSagaRepository orderSagaRepository;
    OutboxEventRecorder outboxEventRecorder;
    SagaOutbox sagaOutbox;
    SagaCancellationService sagaCancellationService;
    CurrentUserProvider currentUserProvider;
    PromotionService promotionService;
    OrderStatusHistoryRepository orderStatusHistoryRepository;
    ShipmentRepository shipmentRepository;
    OrderNotificationService orderNotificationService;
    com.fashionstore.order.order.service.OrderReceiptService orderReceiptService;
    com.fashionstore.order.saga.service.OrderCancellationViewService cancellationProjection;

    @Override
    @Transactional
    public OrderResponse createOrder(String checkoutId, String idempotencyKey, String clientIp) {
        String userId = currentUserProvider.getCurrentUserId();
        String effectiveIdempotencyKey =
                idempotencyKey == null || idempotencyKey.isBlank()
                        ? checkoutId
                        : idempotencyKey.trim();

        Order existingOrder =
                orderRepository
                        .findByUserIdAndIdempotencyKey(userId, effectiveIdempotencyKey)
                        .orElse(null);
        if (existingOrder != null) {
            return toResponse(existingOrder, existingOrder.getCheckoutId());
        }

        Checkout checkout =
                checkoutRepository
                        .findForUpdateByIdAndUserId(checkoutId, userId)
                        .orElseThrow(() -> new AppException(OrderErrorCode.CHECKOUT_NOT_FOUND));

        if (checkout.getOrder() != null) {
            return toResponse(checkout.getOrder(), checkoutId);
        }
        if (checkout.getStatus() == CheckoutStatus.CANCELLED
                || checkout.getStatus() == CheckoutStatus.EXPIRED) {
            throw new AppException(OrderErrorCode.CHECKOUT_STATUS_INVALID);
        }

        List<CheckoutItem> checkoutItems = checkout.getItems();
        if (checkoutItems == null || checkoutItems.isEmpty()) {
            throw new AppException(OrderErrorCode.CHECKOUT_NOT_FOUND);
        }

        ShippingAddress address = copyAddress(checkout.getShippingAddress());

        Order order =
                Order.builder()
                        .orderCode(generateOrderCode())
                        .paymentMethod(checkout.getPaymentMethod())
                        .paymentProvider(checkout.getPaymentProvider())
                        .userId(userId)
                        .idempotencyKey(effectiveIdempotencyKey)
                        .status(OrderStatus.PENDING)
                        .checkoutId(checkout.getId())
                        .clientIp(clientIp)
                        .recipientEmail(checkout.getRecipientEmail())
                        .shippingAddress(fullAddress(address))
                        .address(address)
                        .subtotalAmount(checkout.getSubtotalAmount())
                        .discountAmount(checkout.getDiscountAmount())
                        .shippingFee(checkout.getShippingFee())
                        .totalAmount(checkout.getTotalAmount())
                        .build();

        List<OrderItem> orderItems =
                checkoutItems.stream().map(item -> buildOrderItem(order, item)).toList();

        order.setItems(orderItems);
        orderRepository.save(order);
        recordHistory(
                order,
                null,
                OrderStatus.PENDING,
                "ORDER_CREATED",
                userId,
                "Khách hàng tạo đơn hàng từ checkout");

        if (checkout.getCouponCode() != null && !checkout.getCouponCode().isBlank()) {

            List<PromotionItemDto> promoItems =
                    checkoutItems.stream()
                            .map(
                                    item ->
                                            PromotionItemDto.builder()
                                                    .variantId(item.getVariantId())
                                                    .productId(item.getProductId())
                                                    .categoryId(item.getCategoryId())
                                                    .unitPrice(item.getUnitPrice())
                                                    .quantity(item.getQuantity())
                                                    .lineTotal(item.getLineTotal())
                                                    .build())
                            .toList();
            promotionService.reserve(
                    checkout.getCouponCode(),
                    userId,
                    order.getId(),
                    checkout.getSubtotalAmount(),
                    promoItems);
        }

        checkout.setOrder(order);
        checkout.setStatus(CheckoutStatus.COMPLETED);
        checkoutRepository.save(checkout);

        OrderSaga saga = OrderSaga.start(order.getId());
        orderSagaRepository.save(saga);

        outboxEventRecorder.saveMessage(
                order.getId(),
                EventTypes.INVENTORY_RESERVATION_REQUESTED,
                reserveInventory(order, saga, checkoutItems));

        return toResponse(order, checkoutId);
    }

    /**
     * Đơn chỉ chép bản chụp địa chỉ của checkout (chụp cùng lúc tính phí ship) — không đọc lại
     * identity. Chép ra object mới: hai entity không được dùng chung một instance embeddable.
     */
    private static ShippingAddress copyAddress(ShippingAddress source) {
        if (source == null) {
            throw new AppException(OrderErrorCode.SHIPPING_ADDRESS_INVALID);
        }
        return source.toBuilder().build();
    }

    private static String fullAddress(ShippingAddress address) {
        return java.util.stream.Stream.of(
                        address.getDetailAddress(),
                        address.getWard(),
                        address.getDistrict(),
                        address.getProvince())
                .filter(part -> part != null && !part.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getMyOrderById(String orderId) {
        String userId = currentUserProvider.getCurrentUserId();
        Order order =
                orderRepository
                        .findWithItemsById(orderId)
                        .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!order.getUserId().equals(userId)) {
            throw new AppException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        return toResponse(order, order.getCheckoutId());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<OrderSummaryResponse>> getMyOrders(
            OrderStatus status, Pageable pageable) {
        return getMyOrders(status, null, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<OrderSummaryResponse>> getMyOrders(
            OrderStatus status, OrderPurchaseTab tab, String keyword, Pageable pageable) {
        String userId = currentUserProvider.getCurrentUserId();
        Page<Order> orders =
                tab != null || keyword != null && !keyword.isBlank()
                        ? orderRepository.findAll(com.fashionstore.order.order.service.OrderPurchaseSpecifications
                                .owned(userId, status, tab, keyword), pageable)
                        : status == null
                        ? orderRepository.findByUserId(userId, pageable)
                        : orderRepository.findByUserIdAndStatus(userId, status, pageable);
        var response = toPageResponse(orders, pageable);
        if (orders.isEmpty()) return response;
        List<String> ids = orders.map(Order::getId).getContent();
        var snapshots = orderRepository.findWithItemsByIdIn(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Order::getId,
                        order -> com.fashionstore.order.order.mapper.OrderResponseMapper.create(order, order.getCheckoutId()).getItems()));
        var returns = orderRepository.findReturnReferences(ids).stream()
                .collect(java.util.stream.Collectors.toMap(OrderReturnReference::orderId, reference -> reference));
        var entities = orders.stream().collect(java.util.stream.Collectors.toMap(Order::getId, order -> order));
        response.getItems().forEach(order -> {
            order.setItems(snapshots.getOrDefault(order.getId(), List.of()));
            var returned = returns.get(order.getId());
            if (returned != null) {
                order.setReturnRequestId(returned.returnRequestId());
                order.setReturnStatus(returned.returnStatus());
                order.setReturnKind(returned.kind());
                order.setReturnRefundStatus(returned.refundStatus());
                order.setExpectedRefundAmount(returned.approvedRefundAmount() != null
                        ? returned.approvedRefundAmount() : order.getTotalAmount());
                order.setActualRefundAmount(returned.actualRefundAmount());
            }
            order.setCanRequestReturn(orderReceiptService.canRequestReturn(entities.get(order.getId()), returns.containsKey(order.getId())));
        });
        return response;
    }

    @Override
    @Transactional
    public OrderResponse cancelMyOrder(String orderId, CancelOrderRequest request) {
        return cancelPendingOrder(orderId, request, currentUserProvider.getCurrentUserId(), false);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse cancelOrderForAdmin(String orderId, CancelOrderRequest request) {
        return cancelPendingOrder(orderId, request, currentUserProvider.getCurrentUserId(), true);
    }

    private OrderResponse cancelPendingOrder(
            String orderId, CancelOrderRequest request, String userId, boolean admin) {
        // Khoá Order trước Saga như mọi handler của saga.
        Order order =
                orderRepository
                        .findByIdForUpdate(orderId)
                        .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!admin && !order.getUserId().equals(userId)) {
            throw new AppException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return toResponse(order, order.getCheckoutId()); // hủy hai lần vẫn ra cùng kết quả
        }
        // Đơn PENDING huỷ qua checkout saga (chưa giữ kho: đóng ngay; đang chờ thanh toán: huỷ
        // payment rồi nhả kho). Các trạng thái sau xác nhận đi qua OrderCancellationService.
        if (order.getStatus() == OrderStatus.PENDING) {
            OrderSaga saga =
                    orderSagaRepository
                            .findByOrderIdAndTypeForUpdate(orderId, OrderSagaType.CHECKOUT)
                            .orElse(null);
            String reason =
                    request == null || request.getReason() == null || request.getReason().isBlank()
                            ? "Khách hàng yêu cầu hủy đơn"
                            : request.getReason().trim();
            if (saga != null
                    && sagaCancellationService.cancel(saga, order, reason)
                            != SagaCancellationService.Outcome.NOT_ALLOWED) {
                return toResponse(order, order.getCheckoutId());
            }
        }
        throw new AppException(OrderErrorCode.ORDER_CANNOT_BE_CANCELLED);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<List<OrderSummaryResponse>> searchOrders(
            String userId, OrderStatus status, Pageable pageable) {
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
        Order order =
                orderRepository
                        .findWithItemsById(orderId)
                        .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        return toResponse(order, order.getCheckoutId());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public OrderSagaResponse getOrderSaga(String orderId) {
        return getOrderSaga(orderId, OrderSagaType.CHECKOUT);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public OrderSagaResponse getOrderSaga(String orderId, OrderSagaType type) {
        OrderSaga saga =
                orderSagaRepository
                        .findByOrderIdAndType(orderId, type)
                        .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_SAGA_NOT_FOUND));
        return OrderSagaResponse.builder()
                .sagaId(saga.getId())
                .orderId(saga.getOrderId())
                .type(saga.getType())
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
        Order order =
                orderRepository
                        .findByIdForUpdate(orderId)
                        .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        OrderStatus fromStatus = order.getStatus();
        if (fromStatus == request.getStatus()) {
            return toResponse(order, order.getCheckoutId());
        }
        if (request.getStatus() == OrderStatus.COMPLETED
                || request.getStatus() == OrderStatus.RETURNED
                || request.getStatus() == OrderStatus.REFUNDED)
            throw new AppException(OrderErrorCode.ORDER_STATUS_INVALID);
        if (order.getStatus() == OrderStatus.CANCELLING
                && java.util.Set.of(
                                OrderStatus.PROCESSING,
                                OrderStatus.PACKED,
                                OrderStatus.SHIPPING,
                                OrderStatus.DELIVERED)
                        .contains(request.getStatus()))
            throw new AppException(OrderErrorCode.FULFILLMENT_BLOCKED);
        validateTransition(fromStatus, request.getStatus());

        // Guard: Chỉ cho phép chuyển sang SHIPPING khi đã có vận đơn hoặc trackingCode
        if (request.getStatus() == OrderStatus.SHIPPING) {
            boolean hasShipment =
                    shipmentRepository
                                    .findByOrderId(order.getId())
                                    .filter(s -> s.getStatus() != ShipmentStatus.CANCELLED)
                                    .isPresent()
                            || (order.getTrackingCode() != null
                                    && !order.getTrackingCode().isBlank());
            if (!hasShipment) {
                throw new AppException(OrderErrorCode.SHIPMENT_REQUIRED);
            }
        }

        // REFUNDED không phải một cột cần set — nó là kết quả của một reply từ payment-service.
        // Đơn giữ nguyên RETURNED cho tới khi ví xác nhận đã ghi có (order-funds-refund.credited).
        if (request.getStatus() == OrderStatus.DELIVERED) {
            orderReceiptService.markDelivered(orderId, orderReceiptService.now(), "ADMIN");
            return toResponse(order, order.getCheckoutId());
        }

        order.setStatus(request.getStatus());
        if (request.getShippingProvider() != null && !request.getShippingProvider().isBlank()) {
            order.setShippingProvider(request.getShippingProvider());
        }
        if (request.getTrackingCode() != null && !request.getTrackingCode().isBlank()) {
            order.setTrackingCode(request.getTrackingCode());
        }

        Order saved = orderRepository.save(order);
        String changedBy = currentUserProvider.getCurrentUserId();
        recordHistory(
                saved,
                fromStatus,
                request.getStatus(),
                "ADMIN_UPDATE",
                changedBy != null ? changedBy : "ADMIN",
                request.getReason());
        return toResponse(saved, saved.getCheckoutId());
    }

    /** correlationId = sagaId ngay từ command đầu tiên, để mọi reply về đúng một saga instance. */
    private EventEnvelope<ReservationInventoryCommand> reserveInventory(
            Order order, OrderSaga saga, List<CheckoutItem> checkoutItems) {
        List<InventoryItem> items =
                checkoutItems.stream()
                        .map(item -> new InventoryItem(item.getVariantId(), item.getQuantity()))
                        .toList();
        return EventEnvelope.v1(
                EventTypes.INVENTORY_RESERVATION_REQUESTED,
                order.getId(),
                saga.getId(),
                new ReservationInventoryCommand(order.getId(), order.getUserId(), items));
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
        boolean valid =
                switch (current) {
                    case CONFIRMED -> target == OrderStatus.PROCESSING;
                    case PROCESSING -> target == OrderStatus.PACKED;
                    case PACKED -> target == OrderStatus.SHIPPING;
                    case SHIPPING -> target == OrderStatus.DELIVERED;
                    case DELIVERED ->
                            target == OrderStatus.COMPLETED || target == OrderStatus.RETURNED;
                    case RETURNED -> target == OrderStatus.REFUNDED;
                    default -> false;
                };

        if (!valid) {
            throw new AppException(OrderErrorCode.ORDER_STATUS_INVALID);
        }
    }

    private String generateOrderCode() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "ORD-" + LocalDateTime.now().getYear() + "-" + suffix;
    }

    private PageResponse<List<OrderSummaryResponse>> toPageResponse(
            Page<Order> orders, Pageable pageable) {
        List<OrderSummaryResponse> items = orders.stream().map(this::toSummary).toList();
        return PageResponse.<List<OrderSummaryResponse>>builder()
                .pageNo(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalPage(orders.getTotalPages())
                .totalElements(orders.getTotalElements())
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
        return cancellationProjection.enrich(
                order,
                orderReceiptService.enrich(
                        order,
                        com.fashionstore.order.order.mapper.OrderResponseMapper.create(
                                order, checkoutId)));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<OrderStatusHistoryResponse> getOrderHistory(String orderId) {
        Order order =
                orderRepository
                        .findById(orderId)
                        .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        return orderStatusHistoryRepository
                .findByOrderIdOrderByCreatedAtDescIdDesc(order.getId())
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> getMyOrderHistory(String orderId) {
        String userId = currentUserProvider.getCurrentUserId();
        Order order =
                orderRepository
                        .findById(orderId)
                        .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!order.getUserId().equals(userId)) {
            throw new AppException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        return orderStatusHistoryRepository
                .findByOrderIdOrderByCreatedAtDescIdDesc(order.getId())
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private void recordHistory(
            Order order,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String action,
            String changedBy,
            String reason) {
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

    private OrderStatusHistoryResponse toHistoryResponse(OrderStatusHistory history) {
        return OrderStatusHistoryResponse.builder()
                .id(history.getId())
                .orderId(history.getOrder() != null ? history.getOrder().getId() : null)
                .fromStatus(history.getFromStatus())
                .toStatus(history.getToStatus())
                .action(history.getAction())
                .changedBy(history.getChangedBy())
                .reason(history.getReason())
                .createdAt(history.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public VerifyPurchaseResponse verifyPurchase(VerifyPurchaseRequest request) {
        if (request == null
                || request.userId() == null
                || request.variantIds() == null
                || request.variantIds().isEmpty()) {
            return new VerifyPurchaseResponse(false, null);
        }

        List<OrderStatus> eligibleStatuses = List.of(OrderStatus.DELIVERED, OrderStatus.COMPLETED);

        if (request.orderId() != null && !request.orderId().isBlank()) {
            Optional<Order> orderOpt =
                    orderRepository.findEligibleOrderForReviewById(
                            request.orderId().trim(),
                            request.userId(),
                            request.variantIds(),
                            eligibleStatuses);
            return orderOpt.map(order -> new VerifyPurchaseResponse(true, order.getId()))
                    .orElseGet(() -> new VerifyPurchaseResponse(false, null));
        }

        List<Order> orders =
                orderRepository.findEligibleOrdersForReview(
                        request.userId(), request.variantIds(), eligibleStatuses);

        if (orders.isEmpty()) {
            return new VerifyPurchaseResponse(false, null);
        }

        return new VerifyPurchaseResponse(true, orders.get(0).getId());
    }
}
