package com.fashionstore.order.messaging;

import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.inventory.command.ConfirmInventoryCommand;
import com.fashionstore.contracts.inventory.command.ReleaseInventoryCommand;
import com.fashionstore.contracts.order.OrderCancelledEvent;
import com.fashionstore.contracts.order.OrderConfirmedEvent;
import com.fashionstore.contracts.payment.command.AuthorizePaymentCommand;
import com.fashionstore.contracts.payment.command.CancelPaymentCommand;
import com.fashionstore.contracts.payment.command.RefundPaymentCommand;
import com.fashionstore.order.model.Order;
import com.fashionstore.order.model.OrderSaga;

import java.math.BigDecimal;
import java.util.List;

/**
 * Nơi duy nhất dựng payload của các message saga phát ra. Listener và scanner dùng chung, nên một command
 * phát lần đầu và phát lại lúc timeout luôn giống hệt nhau.
 */
public final class SagaCommands {

    private SagaCommands() {
    }

    public static SagaCommand authorizePayment(Order order) {
        return SagaCommand.of(
                EventTypes.PAYMENT_REQUESTED,
                new AuthorizePaymentCommand(
                        order.getId(),
                        order.getUserId(),
                        order.getPaymentMethod().name(),
                        order.getPaymentProvider().name(),
                        order.getTotalAmount(),
                        order.getCurrency()
                )
        );
    }

    public static SagaCommand confirmInventory(OrderSaga saga) {
        return SagaCommand.of(
                EventTypes.INVENTORY_CONFIRMATION_REQUESTED,
                new ConfirmInventoryCommand(saga.getOrderId(), saga.getInventoryReservationId())
        );
    }

    public static SagaCommand releaseInventory(OrderSaga saga) {
        return releaseInventory(saga.getOrderId(), saga.getInventoryReservationId());
    }

    public static SagaCommand releaseInventory(String orderId, String reservationId) {
        return SagaCommand.of(
                EventTypes.INVENTORY_RELEASE_REQUESTED,
                new ReleaseInventoryCommand(orderId, reservationId)
        );
    }

    public static SagaCommand cancelPayment(OrderSaga saga) {
        return SagaCommand.of(
                EventTypes.PAYMENT_CANCELLATION_REQUESTED,
                new CancelPaymentCommand(saga.getOrderId(), saga.getPaymentId(), saga.getFailureReason())
        );
    }

    /**
     * Hoàn tiền không thuộc saga đặt hàng — order đã ở RETURNED từ lâu, không có {@code correlationId}
     * là sagaId vì không có saga nào đang chạy. {@link RefundEventListener} nhận reply theo orderId.
     */
    public static SagaCommand refundPayment(Order order, BigDecimal amount, String reason) {
        return SagaCommand.of(
                EventTypes.PAYMENT_REFUND_REQUESTED,
                new RefundPaymentCommand(order.getId(), order.getPaymentId(), amount, reason)
        );
    }

    // ----- việc phụ, phát sau khi saga đã đóng: pub/sub, không phải bước saga -----

    public static SagaCommand orderConfirmed(Order order) {
        return SagaCommand.of(
                EventTypes.ORDER_CONFIRMED,
                new OrderConfirmedEvent(order.getId(), order.getUserId(), order.getCheckoutId())
        );
    }

    public static SagaCommand orderCancelled(Order order, String reason) {
        return SagaCommand.of(
                EventTypes.ORDER_CANCELLED,
                new OrderCancelledEvent(order.getId(), order.getUserId(), reason)
        );
    }


}
