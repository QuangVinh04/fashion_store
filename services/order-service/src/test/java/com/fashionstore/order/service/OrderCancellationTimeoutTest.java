package com.fashionstore.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fashionstore.order.entity.*;
import com.fashionstore.order.entity.enumeration.*;
import com.fashionstore.order.event.SagaOutbox;
import com.fashionstore.order.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

class OrderCancellationTimeoutTest {
    final OrderRepository orders = mock(OrderRepository.class);
    final OrderSagaRepository sagas = mock(OrderSagaRepository.class);
    final ShipmentRepository shipments = mock(ShipmentRepository.class);
    final SagaOutbox outbox = mock(SagaOutbox.class);
    final OrderCancellationSagaService service =
            new OrderCancellationSagaService(
                    orders,
                    sagas,
                    shipments,
                    outbox,
                    mock(PromotionService.class),
                    mock(OrderStatusHistoryRepository.class),
                    mock(OrderNotificationService.class),
                    new ObjectMapper());
    final Order order = Order.builder().status(OrderStatus.CANCELLING).build();

    OrderSaga due(OrderSagaStep step) {
        order.setId("order-1");
        var saga = OrderSaga.startCancellation("order-1", "op", "payment-1", "reservation-1");
        saga.setCurrentStep(step);
        saga.setStepDeadline(LocalDateTime.now().minusSeconds(10));
        when(orders.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));
        when(sagas.findByIdForUpdate(saga.getId())).thenReturn(Optional.of(saga));
        return saga;
    }

    @Test
    void timedOutInventoryRetriesStableIdentityAndCannotBeClaimedTwice() {
        var saga = due(OrderSagaStep.CANCEL_ORDER_INVENTORY);
        var now = LocalDateTime.now();
        service.prepareTimedOutStep("order-1", saga.getId(), now);
        service.prepareTimedOutStep("order-1", saga.getId(), now);
        assertThat(saga.getRetryCount()).isEqualTo(1);
        verify(outbox, times(1)).emit(eq(saga), any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLING);
    }

    @Test
    void exhaustedRetriesFailWithoutCancellingOrder() {
        var saga = due(OrderSagaStep.CANCEL_ORDER_INVENTORY);
        saga.setRetryCount(OrderSaga.MAX_RETRIES);
        service.prepareTimedOutStep("order-1", saga.getId(), LocalDateTime.now());
        assertThat(saga.getStatus()).isEqualTo(OrderSagaStatus.FAILED);
        assertThat(saga.getCurrentStep()).isEqualTo(OrderSagaStep.CANCEL_ORDER_INVENTORY);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLING);
        verifyNoInteractions(outbox);
    }

    @Test
    void staleCandidateCannotRetryAlreadyAdvancedStep() {
        var saga = due(OrderSagaStep.REFUND_ORDER_FUNDS);
        saga.setStepDeadline(LocalDateTime.now().plusMinutes(1));
        service.prepareTimedOutStep("order-1", saga.getId(), LocalDateTime.now());
        verifyNoInteractions(outbox);
    }

    @Test
    void checkoutIsNeverProcessedAsCancellationTimeout() {
        var saga = due(OrderSagaStep.CANCEL_PAYMENT);
        saga.setType(OrderSagaType.CHECKOUT);
        service.prepareTimedOutStep("order-1", saga.getId(), LocalDateTime.now());
        verifyNoInteractions(outbox);
        assertThat(saga.getRetryCount()).isZero();
    }
}
