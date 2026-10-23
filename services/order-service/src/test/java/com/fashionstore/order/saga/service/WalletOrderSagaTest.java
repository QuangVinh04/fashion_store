package com.fashionstore.order.saga.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fashionstore.common.payment.*;
import com.fashionstore.contracts.common.*;
import com.fashionstore.contracts.payment.event.*;

import com.fashionstore.order.saga.entity.OrderSagaStatus;
import com.fashionstore.order.saga.entity.OrderSagaStep;
import com.fashionstore.order.order.entity.OrderStatus;


import org.junit.jupiter.api.Test;

import java.util.*;

class WalletOrderSagaTest extends OrderSagaTestFixture {
    @Test
    void walletRefundAcknowledgementReleasesInventory() {
        saga.paymentAuthorized("payment-1");
        saga.startCompensation(OrderSagaStep.REFUND_WALLET, "STOCK_REJECTED", "Stock rejected");

        listener.walletCompensated(
                envelope(
                        EventTypes.WALLET_SAGA_COMPENSATION_COMPLETED,
                        new WalletSagaCompensatedEvent(
                                "saga:payment-1", "order-1", "payment-1", 0, "ledger-1")),
                "wallet-refunded");

        assertThat(saga.getCurrentStep()).isEqualTo(OrderSagaStep.RELEASE_INVENTORY);
        var command =
                org.mockito.ArgumentCaptor.forClass(com.fashionstore.order.saga.dto.SagaCommand.class);
        verify(outbox).emit(eq(saga), command.capture());
        assertThat(command.getValue().eventType())
                .isEqualTo(EventTypes.INVENTORY_RELEASE_REQUESTED);
    }

    @Test
    void rejectedWalletRefundFailsTheSagaAndRaisesTheFailedMetric() {
        saga.paymentAuthorized("payment-1");
        saga.startCompensation(OrderSagaStep.REFUND_WALLET, "STOCK_REJECTED", "Stock rejected");

        listener.walletCompensationRejected(
                envelope(
                        EventTypes.WALLET_SAGA_COMPENSATION_REJECTED,
                        new WalletSagaCompensationRejectedEvent(
                                "saga:payment-1", "order-1", "payment-1", "SOURCE_INVALID", "No")),
                "wallet-rejected");

        assertThat(saga.getStatus()).isEqualTo(OrderSagaStatus.FAILED);
        assertThat(meters.counter("order.saga.failed", "step", "REFUND_WALLET").count())
                .isEqualTo(1.0);
    }

    @Test
    void rejectedOnlineRefundFailsTheSagaAndRaisesTheFailedMetric() {
        saga.paymentAuthorized("payment-1");
        saga.startCompensation(
                OrderSagaStep.REFUND_ORDER_FUNDS, "STOCK_REJECTED", "Stock rejected");

        listener.orderFundsRefundRejected(
                envelope(
                        EventTypes.ORDER_FUNDS_REFUND_REJECTED,
                        new OrderFundsRefundRejectedEvent(
                                "saga-online:payment-1",
                                "order-1",
                                "payment-1",
                                "SAGA_COMPENSATION",
                                saga.getId(),
                                "SOURCE_INVALID",
                                "No")),
                "online-rejected");

        assertThat(saga.getStatus()).isEqualTo(OrderSagaStatus.FAILED);
        assertThat(meters.counter("order.saga.failed", "step", "REFUND_ORDER_FUNDS").count())
                .isEqualTo(1.0);
    }

    @Test
    void walletRefundAcknowledgementWithWrongPaymentDoesNotAdvanceSaga() {
        saga.paymentAuthorized("payment-1");
        saga.startCompensation(OrderSagaStep.REFUND_WALLET, "STOCK_REJECTED", "Stock rejected");

        listener.walletCompensated(
                envelope(
                        EventTypes.WALLET_SAGA_COMPENSATION_COMPLETED,
                        new WalletSagaCompensatedEvent(
                                "saga:other-payment", "order-1", "other-payment", 0, "ledger-1")),
                "wrong-wallet-refund");

        assertThat(saga.getCurrentStep()).isEqualTo(OrderSagaStep.REFUND_WALLET);
        verifyNoInteractions(outbox);
    }

    @Test
    void walletSagaWaitsForConfirmation() {
        listener.paymentInitiated(
                envelope(
                        EventTypes.PAYMENT_INITIATED,
                        new PaymentInitiatedEvent("order-1", "payment-1", "wallet-url", 0)),
                "message-1");
        assertThat(saga.getCurrentStep()).isEqualTo(OrderSagaStep.AUTHORIZE_PAYMENT);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verifyNoInteractions(outbox);
    }

    @Test
    void sagaReplyLocksOrderBeforeSaga() {
        listener.paymentInitiated(
                envelope(
                        EventTypes.PAYMENT_INITIATED,
                        new PaymentInitiatedEvent("order-1", "payment-1", "wallet-url", 0)),
                "message-lock");
        var locks = inOrder(orders, sagas);
        locks.verify(orders).findByIdForUpdate("order-1");
        locks.verify(sagas).findByIdForUpdate(saga.getId());
    }

    @Test
    void verifiedInventoryRejectionStartsWalletCompensation() {
        saga.paymentAuthorized("payment-1");
        var payload =
                new com.fashionstore.contracts.inventory.event.InventoryConfirmationRejectedEvent(
                        "order-1",
                        "reservation-1",
                        "RESERVATION_RELEASED",
                        "Reservation was released before stock commit");
        listener.inventoryConfirmationRejected(
                envelope(EventTypes.INVENTORY_CONFIRMATION_REJECTED, payload), "verified-failure");
        assertThat(saga.getCurrentStep()).isEqualTo(OrderSagaStep.REFUND_WALLET);
    }

    private EventEnvelope<?> envelope(String type, Object payload) {
        return EventEnvelope.v1(type, "order-1", saga.getId(), payload);
    }
}
