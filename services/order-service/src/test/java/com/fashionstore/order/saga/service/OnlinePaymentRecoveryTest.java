package com.fashionstore.order.saga.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fashionstore.common.payment.*;
import com.fashionstore.contracts.common.*;
import com.fashionstore.contracts.inventory.event.InventoryConfirmationRejectedEvent;
import com.fashionstore.contracts.payment.event.OrderFundsRefundCreditedEvent;
import com.fashionstore.order.saga.entity.OrderSagaStep;
import com.fashionstore.order.order.entity.OrderStatus;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class OnlinePaymentRecoveryTest extends OrderSagaTestFixture {
    @Test
    void latePaymentAfterVerifiedStockReleaseRequestsIdempotentWalletRefund() {
        online();
        saga.compensated();
        order.setStatus(OrderStatus.CANCELLED);
        listener.paymentCompleted(
                EventEnvelope.v1(
                        EventTypes.PAYMENT_COMPLETED,
                        "order-1",
                        saga.getId(),
                        new com.fashionstore.contracts.payment.event.PaymentSuccessEvent(
                                "order-1", "payment-1", 0)),
                "late-capture");
        assertThat(saga.getCurrentStep()).isEqualTo(OrderSagaStep.REFUND_ORDER_FUNDS);
        verify(outbox)
                .emit(
                        eq(saga),
                        argThat(
                                command ->
                                        command.eventType()
                                                .equals(EventTypes.ORDER_FUNDS_REFUND_REQUESTED)));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    private void online() {
        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setPaymentProvider(PaymentProvider.PAYOS);
        order.setTotalAmount(BigDecimal.valueOf(80000));
        order.setCurrency("VND");
        saga.paymentAuthorized("payment-1");
    }

    private void reject(String code) {
        listener.inventoryConfirmationRejected(
                EventEnvelope.v1(
                        EventTypes.INVENTORY_CONFIRMATION_REJECTED,
                        "order-1",
                        saga.getId(),
                        new InventoryConfirmationRejectedEvent(
                                "order-1", "reservation-1", code, "Stock not committed")),
                "rejection");
    }

    @Test
    void knownUncommittedOnlineInventoryFailureUsesWalletBudgetCompensation() {
        online();
        reject("RESERVATION_RELEASED");
        assertThat(saga.getCurrentStep()).isEqualTo(OrderSagaStep.REFUND_ORDER_FUNDS);
        verify(outbox)
                .emit(
                        eq(saga),
                        argThat(
                                command ->
                                        command.eventType()
                                                .equals(EventTypes.ORDER_FUNDS_REFUND_REQUESTED)));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void inventoryUncertaintyDoesNotRefundAndShipTogether() {
        online();
        reject("UNKNOWN");
        assertThat(saga.getCurrentStep()).isEqualTo(OrderSagaStep.CONFIRM_INVENTORY);
        verifyNoInteractions(outbox);
    }

    @Test
    void acceptedOnlineCompensationRequiresExactCreditAndKeepsStockReleaseSeparate() {
        online();
        reject("RESERVATION_RELEASED");
        var c =
                new OrderFundsRefundCreditedEvent(
                        "saga-online:payment-1",
                        "order-1",
                        "payment-1",
                        "SAGA_COMPENSATION",
                        saga.getId(),
                        "ledger",
                        BigDecimal.valueOf(80000));
        listener.orderFundsRefundCredited(
                EventEnvelope.v1(
                        EventTypes.ORDER_FUNDS_REFUND_CREDITED, "order-1", saga.getId(), c),
                "credited");
        assertThat(saga.getCurrentStep()).isEqualTo(OrderSagaStep.RELEASE_INVENTORY);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }
}
