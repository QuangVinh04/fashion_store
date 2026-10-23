package com.fashionstore.order.saga.service;

import com.fashionstore.order.order.service.OrderNotificationService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.order.saga.dto.SagaCommand;
import com.fashionstore.order.order.entity.Order;
import com.fashionstore.order.saga.entity.OrderSaga;
import com.fashionstore.order.order.entity.OrderStatusHistory;
import com.fashionstore.order.saga.entity.OrderSagaStatus;
import com.fashionstore.order.saga.entity.OrderSagaStep;
import com.fashionstore.order.saga.entity.OrderSagaType;
import com.fashionstore.order.order.entity.OrderStatus;
import com.fashionstore.order.saga.event.SagaOutbox;
import com.fashionstore.order.shared.exception.OrderErrorCode;
import com.fashionstore.order.order.repository.OrderRepository;
import com.fashionstore.order.saga.repository.OrderSagaRepository;
import com.fashionstore.order.order.repository.OrderStatusHistoryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/** Admin thử lại saga checkout đã FAILED: phát lại command của đúng bước đang kẹt. */
class OrderSagaAdminRetryTest {

    private final OrderSagaRepository sagas = mock(OrderSagaRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final SagaOutbox outbox = mock(SagaOutbox.class);
    private final OrderStatusHistoryRepository history = mock(OrderStatusHistoryRepository.class);
    private OrderSagaTimeoutService service;
    private Order order;

    @BeforeEach
    void setUp() {
        service =
                new OrderSagaTimeoutService(
                        sagas,
                        orders,
                        outbox,
                        new SimpleMeterRegistry(),
                        history,
                        mock(OrderNotificationService.class));
        order =
                Order.builder()
                        .paymentMethod(PaymentMethod.ONLINE)
                        .paymentProvider(PaymentProvider.PAYOS)
                        .status(OrderStatus.PENDING)
                        .totalAmount(BigDecimal.valueOf(80000))
                        .currency("VND")
                        .build();
        order.setId("order-1");
        when(orders.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));
    }

    @Test
    void everyRetryableStepRestartsWithItsOwnCommand() {
        Map<OrderSagaStep, String> expectedCommand =
                Map.of(
                        OrderSagaStep.CONFIRM_INVENTORY, EventTypes.INVENTORY_CONFIRMATION_REQUESTED,
                        OrderSagaStep.CANCEL_PAYMENT, EventTypes.PAYMENT_CANCELLATION_REQUESTED,
                        OrderSagaStep.RELEASE_INVENTORY, EventTypes.INVENTORY_RELEASE_REQUESTED,
                        OrderSagaStep.REFUND_WALLET, EventTypes.WALLET_SAGA_COMPENSATION_REQUESTED,
                        OrderSagaStep.REFUND_ORDER_FUNDS, EventTypes.ORDER_FUNDS_REFUND_REQUESTED);

        expectedCommand.forEach(
                (step, eventType) -> {
                    OrderSaga saga = failedAt(step);
                    registerSaga(saga);

                    service.retryFailedCheckout("order-1");

                    assertThat(saga.getStatus())
                            .as("status at %s", step)
                            .isEqualTo(
                                    step == OrderSagaStep.CONFIRM_INVENTORY
                                            ? OrderSagaStatus.RUNNING
                                            : OrderSagaStatus.COMPENSATING);
                    assertThat(saga.getCurrentStep()).isEqualTo(step);
                    assertThat(saga.getRetryCount()).isZero();
                    assertThat(saga.getStepDeadline()).isNotNull();
                    ArgumentCaptor<SagaCommand> command = ArgumentCaptor.forClass(SagaCommand.class);
                    verify(outbox).emit(org.mockito.Mockito.eq(saga), command.capture());
                    assertThat(command.getValue().eventType()).as("command at %s", step).isEqualTo(eventType);
                    org.mockito.Mockito.clearInvocations(outbox);
                });
    }

    @Test
    void retryKeepsTheFailureCodeButReplacesTheTechnicalReasonShownToTheCustomer() {
        OrderSaga saga = failedAt(OrderSagaStep.RELEASE_INVENTORY);
        registerSaga(saga);

        service.retryFailedCheckout("order-1");

        assertThat(saga.getFailureCode()).isEqualTo("STEP_RETRY_EXHAUSTED");
        assertThat(saga.getFailureReason()).isEqualTo(OrderSagaTimeoutService.ADMIN_RETRY_REASON);
    }

    @Test
    void retryIsAuditedInTheOrderHistory() {
        registerSaga(failedAt(OrderSagaStep.RELEASE_INVENTORY));

        service.retryFailedCheckout("order-1");

        ArgumentCaptor<OrderStatusHistory> saved = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(history).save(saved.capture());
        assertThat(saved.getValue().getAction()).isEqualTo("SAGA_RETRY");
        assertThat(saved.getValue().getChangedBy()).isEqualTo("ADMIN");
        assertThat(saved.getValue().getFromStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saved.getValue().getToStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void locksTheOrderBeforeTheSaga() {
        registerSaga(failedAt(OrderSagaStep.RELEASE_INVENTORY));

        service.retryFailedCheckout("order-1");

        var locks = inOrder(orders, sagas);
        locks.verify(orders).findByIdForUpdate("order-1");
        locks.verify(sagas).findByOrderIdAndTypeForUpdate("order-1", OrderSagaType.CHECKOUT);
    }

    @Test
    void sagaThatIsStillRunningCannotBeRetried() {
        OrderSaga saga = OrderSaga.start("order-1");
        saga.inventoryReserved("res-1");
        saga.startCompensation(OrderSagaStep.RELEASE_INVENTORY, "PAYMENT_FAILED", "Thẻ bị từ chối");
        registerSaga(saga);

        assertThatThrownBy(() -> service.retryFailedCheckout("order-1"))
                .isInstanceOfSatisfying(
                        AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_SAGA_NOT_RETRYABLE));
        verify(outbox, never()).emit(any(), any());
    }

    @Test
    void failedSagaAtAStepWithNoCommandToReplayCannotBeRetried() {
        OrderSaga saga = OrderSaga.start("order-1");
        saga.inventoryReserved("res-1"); // AUTHORIZE_PAYMENT
        saga.fail("SOMETHING", "unexpected");
        registerSaga(saga);

        assertThatThrownBy(() -> service.retryFailedCheckout("order-1"))
                .isInstanceOfSatisfying(
                        AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_SAGA_NOT_RETRYABLE));
        assertThat(saga.getStatus()).isEqualTo(OrderSagaStatus.FAILED);
        verify(outbox, never()).emit(any(), any());
    }

    @Test
    void orderWithoutACheckoutSagaIsReportedAsSuch() {
        when(sagas.findByOrderIdAndTypeForUpdate("order-1", OrderSagaType.CHECKOUT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retryFailedCheckout("order-1"))
                .isInstanceOfSatisfying(
                        AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_SAGA_NOT_FOUND));
    }

    @Test
    void unknownOrderIsReportedAsSuch() {
        when(orders.findByIdForUpdate("order-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retryFailedCheckout("order-1"))
                .isInstanceOfSatisfying(
                        AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private OrderSaga failedAt(OrderSagaStep step) {
        OrderSaga saga = OrderSaga.start("order-1");
        saga.inventoryReserved("res-1");
        saga.paymentAuthorized("payment-1"); // CONFIRM_INVENTORY
        if (step != OrderSagaStep.CONFIRM_INVENTORY) {
            saga.startCompensation(step, "PAYMENT_FAILED", "Thẻ bị từ chối");
        }
        saga.setRetryCount(OrderSaga.MAX_RETRIES);
        saga.fail("STEP_RETRY_EXHAUSTED", "Cạn 5 lần thử ở bước " + step);
        return saga;
    }

    private void registerSaga(OrderSaga saga) {
        when(sagas.findByOrderIdAndTypeForUpdate("order-1", OrderSagaType.CHECKOUT))
                .thenReturn(Optional.of(saga));
    }
}
