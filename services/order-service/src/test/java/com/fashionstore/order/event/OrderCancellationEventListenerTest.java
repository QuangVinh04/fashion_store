package com.fashionstore.order.event;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.common.*;
import com.fashionstore.contracts.inventory.event.OrderInventoryCancelledEvent;
import com.fashionstore.order.service.OrderCancellationSagaService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class OrderCancellationEventListenerTest {
    final ProcessedMessageService processed = mock(ProcessedMessageService.class);
    final OrderCancellationSagaService service = mock(OrderCancellationSagaService.class);
    final OrderCancellationEventListener listener =
            new OrderCancellationEventListener(new ObjectMapper(), processed, service);

    @Test
    void wrongCorrelationCannotAcquireInboxMarker() {
        var reply = new OrderInventoryCancelledEvent("cancel-1", "order-1", "reservation-1");
        assertThatThrownBy(
                        () ->
                                listener.onReply(
                                        EventEnvelope.v1(
                                                EventTypes.ORDER_INVENTORY_CANCELLED,
                                                "order-1",
                                                "wrong-id",
                                                reply),
                                        "msg-1"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(processed, service);
    }

    @Test
    void wrongAggregateCannotAcquireInboxMarker() {
        var reply = new OrderInventoryCancelledEvent("cancel-1", "order-1", "reservation-1");
        assertThatThrownBy(
                        () ->
                                listener.onReply(
                                        EventEnvelope.v1(
                                                EventTypes.ORDER_INVENTORY_CANCELLED,
                                                "wrong-order",
                                                "cancel-1",
                                                reply),
                                        "msg-1"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(processed, service);
    }

    @Test
    void queuedLegacyReplyKeepsConsumerAndCorrelation() {
        doAnswer(
                        call -> {
                            call.getArgument(2, Runnable.class).run();
                            return null;
                        })
                .when(processed)
                .processOnce(any(), any(), any());
        var reply =
                new OrderInventoryCancelledEvent("old-cancellation-id", "order-1", "reservation-1");
        listener.onReply(
                EventEnvelope.v1(
                        EventTypes.ORDER_INVENTORY_CANCELLED,
                        "order-1",
                        "old-cancellation-id",
                        reply),
                "old-msg");
        verify(processed).processOnce(eq("old-msg"), eq("order.cancellation.inventory.v1"), any());
        verify(service).inventoryCancelled(reply, "old-cancellation-id");
    }
}
