package com.fashionstore.order.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.order.dto.SagaCommand;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderSaga;
import com.fashionstore.order.entity.OrderStatusHistory;
import com.fashionstore.order.entity.enumeration.OrderSagaStatus;
import com.fashionstore.order.entity.enumeration.OrderSagaStep;
import com.fashionstore.order.entity.enumeration.OrderSagaType;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.event.SagaOutbox;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.OrderSagaRepository;
import com.fashionstore.order.repository.OrderStatusHistoryRepository;

import io.micrometer.core.instrument.MeterRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Không có trạng thái kẹt im lặng: mọi bước đều có deadline, và đây là chỗ deadline được thi hành.
 *
 * <p>Bước tiến quá hạn thì chuyển sang bù trừ; bước bù trừ quá hạn thì phát lại command
 * (participant idempotent nên phát lại là an toàn). Cạn {@link OrderSaga#MAX_RETRIES} lần thì
 * chuyển FAILED — có người phải xử lý, không nằm im chờ.
 */
@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class OrderSagaTimeoutService {

    private final OrderSagaRepository sagaRepository;
    private final OrderRepository orderRepository;
    private final SagaOutbox sagaOutbox;
    private final MeterRegistry meterRegistry;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderNotificationService orderNotificationService;

    /** Lý do hiển thị cho khách nếu đơn bị huỷ sau khi admin thử lại, thay cho thông điệp kỹ thuật. */
    public static final String ADMIN_RETRY_REASON = "Đơn hàng bị huỷ do sự cố xử lý đơn";

    /**
     * Admin thử lại saga checkout FAILED: phát lại command của đúng bước đang kẹt để luồng (bù
     * trừ) chạy tiếp. Khoá Order trước Saga như mọi nơi khác.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void retryFailedCheckout(String orderId) {
        Order order =
                orderRepository
                        .findByIdForUpdate(orderId)
                        .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        OrderSaga saga =
                sagaRepository
                        .findByOrderIdAndTypeForUpdate(orderId, OrderSagaType.CHECKOUT)
                        .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_SAGA_NOT_FOUND));
        SagaCommand command = saga.getStatus() == OrderSagaStatus.FAILED ? commandFor(saga) : null;
        if (command == null) {
            throw new AppException(OrderErrorCode.ORDER_SAGA_NOT_RETRYABLE);
        }

        String reason =
                "Retry checkout saga ở bước %s sau lỗi %s"
                        .formatted(saga.getCurrentStep(), saga.getFailureCode());
        log.warn("Admin retry saga {} (order {}): {}", saga.getId(), orderId, reason);
        saga.reopenAfterFailure(ADMIN_RETRY_REASON);
        sagaOutbox.emit(saga, command);
        sagaRepository.save(saga);
        orderStatusHistoryRepository.save(
                OrderStatusHistory.builder()
                        .order(order)
                        .fromStatus(order.getStatus())
                        .toStatus(order.getStatus())
                        .action("SAGA_RETRY")
                        .changedBy("ADMIN")
                        .reason(reason)
                        .build());
    }

    /** Command phát lại của bước hiện tại; null nếu bước đó không có command để phát lại. */
    private SagaCommand commandFor(OrderSaga saga) {
        return switch (saga.getCurrentStep()) {
            case CONFIRM_INVENTORY -> SagaCommands.confirmInventory(saga);
            case CANCEL_PAYMENT -> SagaCommands.cancelPayment(saga);
            case RELEASE_INVENTORY -> SagaCommands.releaseInventory(saga);
            case REFUND_WALLET -> SagaCommands.compensateWallet(saga, lockedOrder(saga));
            case REFUND_ORDER_FUNDS -> SagaCommands.compensateOnline(saga, lockedOrder(saga));
            default -> null;
        };
    }

    private Order lockedOrder(OrderSaga saga) {
        return orderRepository.findByIdForUpdate(saga.getOrderId()).orElseThrow();
    }

    @Transactional
    public void expireCheckout(String orderId, String sagaId, LocalDateTime now) {
        if (orderRepository.findByIdForUpdate(orderId).isEmpty()) return;
        OrderSaga saga = sagaRepository.findByIdForUpdate(sagaId).orElse(null);
        if (saga == null
                || saga.getType() != OrderSagaType.CHECKOUT
                || !java.util.Objects.equals(saga.getOrderId(), orderId)
                || saga.isTerminal()
                || saga.getStepDeadline() == null
                || !saga.getStepDeadline().isBefore(now)) return;
        expire(saga);
    }

    private void expire(OrderSaga saga) {
        OrderSagaStep step = saga.getCurrentStep();
        log.warn(
                "Saga {} quá hạn ở bước {} (lần thử {})", saga.getId(), step, saga.getRetryCount());

        switch (step) {
            case RESERVE_INVENTORY -> {
                // Chưa giữ được gì nên không có gì để bù trừ.
                String reason = "Quá hạn chờ giữ kho";
                saga.compensated("INVENTORY_TIMEOUT", reason);
                cancelOrder(saga, reason);
            }
            case AUTHORIZE_PAYMENT -> {
                saga.startCompensation(
                        OrderSagaStep.CANCEL_PAYMENT, "PAYMENT_TIMEOUT", "Quá hạn chờ thanh toán");
                sagaOutbox.emit(saga, SagaCommands.cancelPayment(saga));
            }
            case CONFIRM_INVENTORY,
                    CANCEL_PAYMENT,
                    RELEASE_INVENTORY,
                    REFUND_WALLET,
                    REFUND_ORDER_FUNDS ->
                    retryOrFail(saga, commandFor(saga));
            case DONE -> {
                log.error("Saga {} ở bước DONE mà vẫn còn deadline — xóa deadline", saga.getId());
                saga.setStepDeadline(null);
            }
            case STOP_SHIPMENT, CANCEL_ORDER_INVENTORY ->
                    throw new IllegalStateException(
                            "Cancellation requires its own timeout handler");
        }

        sagaRepository.save(saga);
    }

    private void retryOrFail(OrderSaga saga, SagaCommand command) {
        if (!saga.canRetry()) {
            saga.fail(
                    "STEP_RETRY_EXHAUSTED",
                    "Cạn %d lần thử ở bước %s"
                            .formatted(OrderSaga.MAX_RETRIES, saga.getCurrentStep()));
            // Saga FAILED ở nhánh bù trừ nghĩa là tồn kho hoặc tiền đang treo — phải kêu lên, không
            // chỉ log.
            meterRegistry
                    .counter("order.saga.failed", "step", saga.getCurrentStep().name())
                    .increment();
            log.error(
                    "Saga {} (order {}) FAILED ở bước {} — cần can thiệp thủ công",
                    saga.getId(),
                    saga.getOrderId(),
                    saga.getCurrentStep());
            return;
        }
        saga.retryCurrentStep();
        sagaOutbox.emit(saga, command);
    }

    private void cancelOrder(OrderSaga saga, String reason) {
        Order order =
                orderRepository
                        .findByIdForUpdate(saga.getOrderId())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Order %s của saga %s không tồn tại"
                                                        .formatted(
                                                                saga.getOrderId(), saga.getId())));
        order.cancel(reason);
        orderRepository.save(order);

        if (orderStatusHistoryRepository != null) {
            orderStatusHistoryRepository.save(
                    OrderStatusHistory.builder()
                            .order(order)
                            .fromStatus(OrderStatus.PENDING)
                            .toStatus(OrderStatus.CANCELLED)
                            .action("SAGA_TIMEOUT")
                            .changedBy("SYSTEM")
                            .reason(reason)
                            .build());
        }

        orderNotificationService.sendOrderCancelledNotification(order, reason);
        sagaOutbox.emit(saga, SagaCommands.orderCancelled(order, reason));
    }
}
