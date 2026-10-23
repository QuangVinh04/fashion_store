package com.fashionstore.order.saga.service;

import static org.assertj.core.api.Assertions.*;

import com.fashionstore.order.saga.entity.OrderSaga;
import com.fashionstore.order.saga.entity.OrderSagaStatus;
import com.fashionstore.order.saga.entity.OrderSagaStep;
import com.fashionstore.order.saga.entity.OrderSagaType;

import org.junit.jupiter.api.Test;

class OrderSagaCancellationStateTest {
    @Test
    void checkoutFactoryKeepsExistingFirstStep() {
        var saga = OrderSaga.start("order-1");
        assertThat(saga.getType()).isEqualTo(OrderSagaType.CHECKOUT);
        assertThat(saga.getCurrentStep()).isEqualTo(OrderSagaStep.RESERVE_INVENTORY);
    }

    @Test
    void cancellationFactoryHasIndependentIdAndStableRefundId() {
        var checkout = OrderSaga.start("order-1");
        checkout.complete();
        var cancellation =
                OrderSaga.startCancellation("order-1", "op-1", "payment-1", "reservation-1");
        assertThat(cancellation.getId()).isNotEqualTo(checkout.getId());
        assertThat(cancellation.getType()).isEqualTo(OrderSagaType.CANCELLATION);
        assertThat(cancellation.getCurrentStep()).isEqualTo(OrderSagaStep.STOP_SHIPMENT);
        assertThat(cancellation.getStatus()).isEqualTo(OrderSagaStatus.RUNNING);
        assertThat(cancellation.getRefundId()).isEqualTo("cancel:" + cancellation.getId());
        assertThat(cancellation.getRequestOperationId()).isEqualTo("op-1");
        assertThat(cancellation.getPaymentId()).isEqualTo("payment-1");
        assertThat(cancellation.getInventoryReservationId()).isEqualTo("reservation-1");
        assertThat(cancellation.getStepDeadline()).isNotNull();
        cancellation.retryCurrentStep();
        assertThat(cancellation.getRefundId()).isEqualTo("cancel:" + cancellation.getId());
        assertThat(checkout.getStatus()).isEqualTo(OrderSagaStatus.COMPLETED);
    }
}
