package com.fashionstore.order.saga.event;

import com.fashionstore.common.messaging.RabbitTopology;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.common.*;
import com.fashionstore.contracts.payment.event.*;
import com.fashionstore.order.shared.config.messaging.RabbitMQNames;
import com.fashionstore.order.order.entity.OrderStatusHistory;
import com.fashionstore.order.order.repository.OrderRepository;
import com.fashionstore.order.order.repository.OrderStatusHistoryRepository;
import com.fashionstore.order.saga.service.OrderSagaService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class OrderFundsRefundEventListener {
    private final ObjectMapper mapper;
    private final ProcessedMessageService processed;
    private final OrderSagaService saga;
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final com.fashionstore.order.returns.service.ReturnService returns;

    @Transactional
    @RabbitListener(queues = RabbitMQNames.ORDER_FUNDS_REFUND_QUEUE)
    public void onReply(
            EventEnvelope<?> envelope,
            @Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId) {
        if (EventTypes.ORDER_FUNDS_REFUND_CREDITED.equals(envelope.eventType())) {
            var c = mapper.convertValue(envelope.payload(), OrderFundsRefundCreditedEvent.class);
            if ("PAID_CANCELLATION".equals(c.reasonType())) return;
            if (java.util.Set.of("RETURN_REFUND", "VERIFIED_LOSS", "CARRIER_RETURN")
                    .contains(c.reasonType())) {
                processed.processOnce(
                        messageId,
                        "order.return-refund-credit.v1",
                        () -> returns.refundCredited(c));
                return;
            }
            if ("SAGA_COMPENSATION".equals(c.reasonType())) {
                saga.orderFundsRefundCredited(envelope, messageId);
                return;
            }
            processed.processOnce(
                    c.refundId(),
                    "order.funds-credit-audit.v1",
                    () -> {
                        var order = orders.findByIdForUpdate(c.orderId()).orElseThrow();
                        if (!Objects.equals(order.getPaymentId(), c.paymentId())
                                || c.ledgerEntryId() == null
                                || c.ledgerEntryId().isBlank()
                                || c.amount() == null
                                || order.getTotalAmount() == null
                                || c.amount().compareTo(order.getTotalAmount()) != 0)
                            throw new IllegalArgumentException(
                                    "Funds credit does not match order/payment/amount");
                        history.save(
                                OrderStatusHistory.builder()
                                        .order(order)
                                        .fromStatus(order.getStatus())
                                        .toStatus(order.getStatus())
                                        .action(c.reasonType() + "_REFUNDED")
                                        .changedBy("SYSTEM")
                                        .reason(
                                                "Wallet refund "
                                                        + c.refundId()
                                                        + "; ledger "
                                                        + c.ledgerEntryId()
                                                        + "; amount "
                                                        + c.amount().toPlainString())
                                        .build());
                    });
            return;
        }
        if (EventTypes.ORDER_FUNDS_REFUND_REJECTED.equals(envelope.eventType())) {
            var c = mapper.convertValue(envelope.payload(), OrderFundsRefundRejectedEvent.class);
            if ("PAID_CANCELLATION".equals(c.reasonType())) return;
            if (java.util.Set.of("RETURN_REFUND", "VERIFIED_LOSS", "CARRIER_RETURN")
                    .contains(c.reasonType())) {
                processed.processOnce(
                        messageId,
                        "order.return-refund-rejection.v1",
                        () -> returns.refundRejected(c));
                return;
            }
            if ("SAGA_COMPENSATION".equals(c.reasonType())) {
                saga.orderFundsRefundRejected(envelope, messageId);
                return;
            }
            processed.processOnce(
                    messageId,
                    "order.funds-rejection-audit.v1",
                    () -> {
                        var order = orders.findByIdForUpdate(c.orderId()).orElseThrow();
                        if (!Objects.equals(order.getPaymentId(), c.paymentId()))
                            throw new IllegalArgumentException("Funds rejection payment mismatch");
                        history.save(
                                OrderStatusHistory.builder()
                                        .order(order)
                                        .fromStatus(order.getStatus())
                                        .toStatus(order.getStatus())
                                        .action(c.reasonType() + "_REFUND_REVIEW_REQUIRED")
                                        .changedBy("SYSTEM")
                                        .reason(c.code() + ": " + c.reason())
                                        .build());
                    });
            return;
        }
        throw new IllegalArgumentException("Unsupported order funds reply");
    }
}
