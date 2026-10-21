package com.fashionstore.order.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.client.GhnClient;
import com.fashionstore.order.dto.CancelOrderRequest;
import com.fashionstore.order.dto.CancellationCarrierWork;
import com.fashionstore.order.dto.OrderResponse;
import com.fashionstore.order.dto.ShipmentCancellationResult;
import com.fashionstore.order.dto.VerifyCancellationCarrierRequest;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderSaga;
import com.fashionstore.order.entity.enumeration.OrderSagaType;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.OrderSagaRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OrderCancellationService {

    private final OrderCancellationSagaService database;
    private final GhnClient carrier;
    private final CurrentUserProvider owner;
    private final OrderRepository orders;
    private final OrderSagaRepository sagas;
    private final OrderService orderService;

    @Transactional(propagation = Propagation.NEVER)
    public OrderResponse request(String orderId, CancelOrderRequest request) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!Objects.equals(order.getUserId(), owner.getCurrentUserId())) {
            throw new AppException(OrderErrorCode.ORDER_NOT_FOUND);
        }

        // Đơn chưa xác nhận huỷ qua checkout saga, không qua cancellation saga.
        if (order.getStatus() == OrderStatus.PENDING
                || order.getStatus() == OrderStatus.CANCELLED
                        && sagas.findByOrderIdAndType(orderId, OrderSagaType.CANCELLATION).isEmpty()) {
            return orderService.cancelMyOrder(orderId, request);
        }

        String operationId = resolveOperationId(request);
        String cancelReason = resolveCancelReason(request);

        OrderSaga cancellationSaga = database.request(
                orderId,
                operationId,
                cancelReason,
                owner.getCurrentUserId(),
                false);

        reconcileCarrier(database.claimCarrierAttempt(orderId, cancellationSaga.getId()));
        return orderService.getMyOrderById(orderId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(propagation = Propagation.NEVER)
    public OrderResponse requestAdmin(String orderId, CancelOrderRequest request) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() == OrderStatus.PENDING
                || order.getStatus() == OrderStatus.CANCELLED
                        && sagas.findByOrderIdAndType(orderId, OrderSagaType.CANCELLATION).isEmpty()) {
            return orderService.cancelOrderForAdmin(orderId, request);
        }

        String operationId = resolveOperationId(request);
        String cancelReason = resolveCancelReason(request);

        OrderSaga cancellationSaga = database.request(
                orderId,
                operationId,
                cancelReason,
                owner.getCurrentUserId(),
                true);

        reconcileCarrier(database.claimCarrierAttempt(orderId, cancellationSaga.getId()));
        return orderService.getOrderById(orderId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(propagation = Propagation.NEVER)
    public OrderResponse retryAdmin(String orderId, CancelOrderRequest request) {
        OrderSaga cancellationSaga = sagas.findByOrderIdAndType(orderId, OrderSagaType.CANCELLATION)
                .orElseThrow(() -> new AppException(OrderErrorCode.CANCELLATION_CONFLICT));

        String operationId = resolveOperationId(request);
        if (!Objects.equals(cancellationSaga.getRequestOperationId(), operationId)) {
            throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
        }

        database.retry(orderId, cancellationSaga.getId());
        reconcileCarrier(database.claimCarrierAttempt(orderId, cancellationSaga.getId()));
        return orderService.getOrderById(orderId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(propagation = Propagation.NEVER)
    public OrderResponse verifyCarrier(
            String orderId, String cancellationId, VerifyCancellationCarrierRequest request) {
        database.verifyCarrier(orderId, cancellationId, request, owner.getCurrentUserId());
        return orderService.getOrderById(orderId);
    }

    private void reconcileCarrier(CancellationCarrierWork work) {
        if (work == null) {
            return;
        }

        ShipmentCancellationResult result;
        try {
            result = carrier.cancelPendingShipment(work.trackingCode());
        } catch (RuntimeException unavailable) {
            result = ShipmentCancellationResult.unknown(work.trackingCode(), "Carrier temporarily unavailable");
        }

        database.applyCarrierResult(work, result);
    }

    @Transactional(propagation = Propagation.NEVER)
    public void onTimeout(String orderId, String sagaId, LocalDateTime now) {
        reconcileCarrier(database.prepareTimedOutStep(orderId, sagaId, now));
    }

    private String resolveOperationId(CancelOrderRequest request) {
        if (request == null || request.getOperationId() == null || request.getOperationId().isBlank()) {
            throw new AppException(OrderErrorCode.CANCELLATION_CONFLICT);
        }
        return request.getOperationId();
    }

    private String resolveCancelReason(CancelOrderRequest request) {
        return (request == null || request.getReason() == null || request.getReason().isBlank())
                ? "Khách hàng yêu cầu hủy đơn"
                : request.getReason().trim();
    }
}
