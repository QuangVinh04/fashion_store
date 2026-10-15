package com.fashionstore.order.service;

import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.inventory.command.ConfirmInventoryCommand;
import com.fashionstore.contracts.inventory.command.ReleaseInventoryCommand;
import com.fashionstore.contracts.order.OrderCancelledEvent;
import com.fashionstore.contracts.payment.command.CancelPaymentCommand;
import com.fashionstore.order.dto.SagaCommand;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderSaga;

/**
 * Dựng payload cho timeout/retry, hủy đơn và hoàn tiền sau trả hàng. OrderSagaService dựng payload
 * trực tiếp trong từng handler để dễ học; khi thay đổi wire payload cần cập nhật cả hai nơi để
 * command phát lần đầu và command phát lại lúc timeout luôn tương đương.
 */
public final class SagaCommands {

    private SagaCommands() {}

    public static SagaCommand cancelOrderInventory(OrderSaga saga) {
        return SagaCommand.of(
                EventTypes.ORDER_INVENTORY_CANCELLATION_REQUESTED,
                new com.fashionstore.contracts.inventory.command.CancelOrderInventoryCommand(
                        saga.getId(), saga.getOrderId(), saga.getInventoryReservationId()));
    }

    public static SagaCommand refundCancellation(OrderSaga saga, Order order) {
        return SagaCommand.of(
                EventTypes.ORDER_FUNDS_REFUND_REQUESTED,
                new com.fashionstore.contracts.payment.command.RefundOrderFundsCommand(
                        saga.getRefundId(),
                        order.getId(),
                        saga.getPaymentId(),
                        order.getTotalAmount(),
                        order.getCurrency(),
                        "PAID_CANCELLATION",
                        saga.getId()));
    }

    public static SagaCommand compensateWallet(OrderSaga saga, Order order) {
        return SagaCommand.of(
                EventTypes.WALLET_SAGA_COMPENSATION_REQUESTED,
                new com.fashionstore.contracts.payment.command.RefundWalletSagaCommand(
                        "saga:" + saga.getPaymentId(),
                        order.getId(),
                        saga.getPaymentId(),
                        0,
                        order.getTotalAmount(),
                        order.getCurrency(),
                        saga.getFailureReason()));
    }

    public static SagaCommand compensateOnline(OrderSaga saga, Order order) {
        return SagaCommand.of(
                EventTypes.ORDER_FUNDS_REFUND_REQUESTED,
                new com.fashionstore.contracts.payment.command.RefundOrderFundsCommand(
                        "saga-online:" + saga.getPaymentId(),
                        order.getId(),
                        saga.getPaymentId(),
                        order.getTotalAmount(),
                        order.getCurrency(),
                        "SAGA_COMPENSATION",
                        saga.getId()));
    }

    public static SagaCommand confirmInventory(OrderSaga saga) {
        return SagaCommand.of(
                EventTypes.INVENTORY_CONFIRMATION_REQUESTED,
                new ConfirmInventoryCommand(saga.getOrderId(), saga.getInventoryReservationId()));
    }

    public static SagaCommand releaseInventory(OrderSaga saga) {
        return releaseInventory(saga.getOrderId(), saga.getInventoryReservationId());
    }

    public static SagaCommand releaseInventory(String orderId, String reservationId) {
        return SagaCommand.of(
                EventTypes.INVENTORY_RELEASE_REQUESTED,
                new ReleaseInventoryCommand(orderId, reservationId));
    }

    public static SagaCommand cancelPayment(OrderSaga saga) {
        return SagaCommand.of(
                EventTypes.PAYMENT_CANCELLATION_REQUESTED,
                new CancelPaymentCommand(
                        saga.getOrderId(), saga.getPaymentId(), saga.getFailureReason()));
    }

    public static SagaCommand orderCancelled(Order order, String reason) {
        return SagaCommand.of(
                EventTypes.ORDER_CANCELLED,
                new OrderCancelledEvent(order.getId(), order.getUserId(), reason));
    }
}
