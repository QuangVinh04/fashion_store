package com.fashionstore.order.service;

import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.inventory.command.ConfirmInventoryCommand;
import com.fashionstore.contracts.inventory.command.ReleaseInventoryCommand;
import com.fashionstore.contracts.inventory.command.RestockInventoryCommand;
import com.fashionstore.contracts.order.OrderCancelledEvent;
import com.fashionstore.contracts.order.OrderConfirmedEvent;
import com.fashionstore.contracts.payment.command.AuthorizePaymentCommand;
import com.fashionstore.contracts.payment.command.CancelPaymentCommand;
import com.fashionstore.contracts.payment.command.RefundPaymentCommand;
import com.fashionstore.order.dto.SagaCommand;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderSaga;
import com.fashionstore.order.event.RefundEventListener;

import java.math.BigDecimal;

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

    public static SagaCommand authorizePayment(Order order) {
        return authorizePayment(order, null);
    }

    public static SagaCommand authorizePayment(Order order, java.time.Instant expiresAt) {
        return SagaCommand.of(
                EventTypes.PAYMENT_REQUESTED,
                new AuthorizePaymentCommand(
                        order.getId(),
                        order.getUserId(),
                        order.getPaymentMethod().name(),
                        order.getPaymentProvider().name(),
                        order.getTotalAmount(),
                        order.getCurrency(),
                        order.getClientIp(),
                        expiresAt));
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

    /**
     * Hoàn tiền không thuộc saga đặt hàng — order đã ở RETURNED từ lâu, không có {@code
     * correlationId} là sagaId vì không có saga nào đang chạy. {@link RefundEventListener} nhận
     * reply theo orderId.
     */
    public static SagaCommand refundPayment(Order order, BigDecimal amount, String reason) {
        return SagaCommand.of(
                EventTypes.PAYMENT_REFUND_REQUESTED,
                new RefundPaymentCommand(order.getId(), order.getPaymentId(), amount, reason));
    }

    public static SagaCommand restockInventory(String orderId) {
        return SagaCommand.of(
                EventTypes.INVENTORY_RESTOCK_REQUESTED, new RestockInventoryCommand(orderId));
    }

    // ----- việc phụ, phát sau khi saga đã đóng: pub/sub, không phải bước saga -----

    public static SagaCommand orderConfirmed(Order order) {
        return SagaCommand.of(
                EventTypes.ORDER_CONFIRMED,
                new OrderConfirmedEvent(order.getId(), order.getUserId(), order.getCheckoutId()));
    }

    public static SagaCommand orderCancelled(Order order, String reason) {
        return SagaCommand.of(
                EventTypes.ORDER_CANCELLED,
                new OrderCancelledEvent(order.getId(), order.getUserId(), reason));
    }
}
