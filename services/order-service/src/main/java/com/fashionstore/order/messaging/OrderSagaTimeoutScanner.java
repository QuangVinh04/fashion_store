package com.fashionstore.order.messaging;

import com.fashionstore.order.model.Order;
import com.fashionstore.order.model.OrderSaga;
import com.fashionstore.order.model.enumeration.OrderSagaStatus;
import com.fashionstore.order.model.enumeration.OrderSagaStep;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.OrderSagaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Không có trạng thái kẹt im lặng: mọi bước đều có deadline, và đây là chỗ deadline được thi hành.
 *
 * <p>Bước tiến quá hạn thì chuyển sang bù trừ; bước bù trừ quá hạn thì phát lại command (participant
 * idempotent nên phát lại là an toàn). Cạn {@link OrderSaga#MAX_RETRIES} lần thì chuyển FAILED — có
 * người phải xử lý, không nằm im chờ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaTimeoutScanner {

    private static final List<OrderSagaStatus> ACTIVE = List.of(
            OrderSagaStatus.RUNNING,
            OrderSagaStatus.COMPENSATING
    );

    private final OrderSagaRepository sagaRepository;
    private final OrderRepository orderRepository;
    private final SagaOutbox sagaOutbox;
    private final MeterRegistry meterRegistry;

    @Transactional
    @Scheduled(fixedDelayString = "${app.saga.timeout-scan-delay-ms:15000}")
    public void expireDueSteps() {
        List<OrderSaga> due = sagaRepository.findDueForUpdate(ACTIVE, LocalDateTime.now());
        if (due.isEmpty()) {
            return;
        }
        log.info("Có {} saga quá hạn ở bước hiện tại", due.size());
        due.forEach(this::expire);
    }

    private void expire(OrderSaga saga) {
        OrderSagaStep step = saga.getCurrentStep();
        log.warn("Saga {} quá hạn ở bước {} (lần thử {})", saga.getId(), step, saga.getRetryCount());

        switch (step) {
            case RESERVE_INVENTORY -> {
                // Chưa giữ được gì nên không có gì để bù trừ.
                String reason = "Quá hạn chờ giữ kho";
                saga.compensated("INVENTORY_TIMEOUT", reason);
                cancelOrder(saga, reason);
            }
            case AUTHORIZE_PAYMENT -> {
                saga.startCompensation(OrderSagaStep.CANCEL_PAYMENT, "PAYMENT_TIMEOUT", "Quá hạn chờ thanh toán");
                sagaOutbox.emit(saga, SagaCommands.cancelPayment(saga));
            }
            case CONFIRM_INVENTORY -> retryOrFail(saga, SagaCommands.confirmInventory(saga));
            case CANCEL_PAYMENT -> retryOrFail(saga, SagaCommands.cancelPayment(saga));
            case RELEASE_INVENTORY -> retryOrFail(saga, SagaCommands.releaseInventory(saga));
            case DONE -> {
                log.error("Saga {} ở bước DONE mà vẫn còn deadline — xóa deadline", saga.getId());
                saga.setStepDeadline(null);
            }
        }

        sagaRepository.save(saga);
    }

    private void retryOrFail(OrderSaga saga, SagaCommand command) {
        if (!saga.canRetry()) {
            saga.fail("STEP_RETRY_EXHAUSTED",
                    "Cạn %d lần thử ở bước %s".formatted(OrderSaga.MAX_RETRIES, saga.getCurrentStep()));
            // Saga FAILED ở nhánh bù trừ nghĩa là tồn kho hoặc tiền đang treo — phải kêu lên, không chỉ log.
            meterRegistry.counter("order.saga.failed", "step", saga.getCurrentStep().name()).increment();
            log.error("Saga {} (order {}) FAILED ở bước {} — cần can thiệp thủ công",
                    saga.getId(), saga.getOrderId(), saga.getCurrentStep());
            return;
        }
        saga.retryCurrentStep();
        sagaOutbox.emit(saga, command);
    }

    private void cancelOrder(OrderSaga saga, String reason) {
        Order order = orderRepository.findByIdForUpdate(saga.getOrderId())
                .orElseThrow(() -> new IllegalStateException(
                        "Order %s của saga %s không tồn tại".formatted(saga.getOrderId(), saga.getId())));
        order.cancel(reason);
        orderRepository.save(order);
        sagaOutbox.emit(saga, SagaCommands.orderCancelled(order, reason));
    }
}
