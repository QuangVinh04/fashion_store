package com.fashionstore.order.event;

import static org.mockito.Mockito.*;

import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.common.*;
import com.fashionstore.contracts.payment.event.*;
import com.fashionstore.order.repository.*;
import com.fashionstore.order.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class GenericFundsReplyIsolationTest {
    @Test
    void cancellationReceiptsAreOwnedOnlyByCancellationConsumer() {
        var processed = mock(ProcessedMessageService.class);
        var saga = mock(OrderSagaService.class);
        var orders = mock(OrderRepository.class);
        var history = mock(OrderStatusHistoryRepository.class);
        var returns = mock(ReturnService.class);
        var listener =
                new OrderFundsRefundEventListener(
                        new ObjectMapper(), processed, saga, orders, history, returns);
        listener.onReply(
                EventEnvelope.v1(
                        EventTypes.ORDER_FUNDS_REFUND_CREDITED,
                        "order-1",
                        "cancel-1",
                        new OrderFundsRefundCreditedEvent(
                                "refund-1",
                                "order-1",
                                "payment-1",
                                "PAID_CANCELLATION",
                                "cancel-1",
                                "ledger-1",
                                BigDecimal.TEN)),
                "message-1");
        listener.onReply(
                EventEnvelope.v1(
                        EventTypes.ORDER_FUNDS_REFUND_REJECTED,
                        "order-1",
                        "cancel-1",
                        new OrderFundsRefundRejectedEvent(
                                "refund-1",
                                "order-1",
                                "payment-1",
                                "PAID_CANCELLATION",
                                "cancel-1",
                                "REJECTED",
                                "Reason")),
                "message-2");
        verifyNoInteractions(processed, saga, orders, history, returns);
    }
}
