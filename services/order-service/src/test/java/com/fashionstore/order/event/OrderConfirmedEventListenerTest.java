package com.fashionstore.order.event;

import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.order.OrderConfirmedEvent;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderStatusHistory;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.OrderStatusHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderConfirmedEventListenerTest {

    @Mock
    private ProcessedMessageService processedMessageService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    private OrderConfirmedEventListener listener;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return null;
        }).when(processedMessageService).processOnce(anyString(), anyString(), any(Runnable.class));

        listener = new OrderConfirmedEventListener(
                processedMessageService,
                orderRepository,
                new ObjectMapper(),
                orderStatusHistoryRepository
        );
    }

    @Test
    void onOrderConfirmed_whenStatusIsConfirmed_transitionsToProcessingAndRecordsHistory() {
        Order order = Order.builder()
                .status(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("150000"))
                .build();
        order.setId("order-101");

        when(orderRepository.findByIdForUpdate("order-101")).thenReturn(Optional.of(order));

        OrderConfirmedEvent event = new OrderConfirmedEvent("order-101", "user-1", "checkout-1");
        EventEnvelope<OrderConfirmedEvent> envelope = EventEnvelope.v1("order.confirmed", "order-101", "checkout-1", event);

        listener.onOrderConfirmed(envelope, "msg-1");

        assertEquals(OrderStatus.PROCESSING, order.getStatus());
        verify(orderRepository, times(1)).save(order);

        ArgumentCaptor<OrderStatusHistory> captor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository, times(1)).save(captor.capture());
        assertEquals(OrderStatus.CONFIRMED, captor.getValue().getFromStatus());
        assertEquals(OrderStatus.PROCESSING, captor.getValue().getToStatus());
        assertEquals("AUTO_PROCESSING", captor.getValue().getAction());
        assertEquals("SYSTEM", captor.getValue().getChangedBy());
    }

    @Test
    void onOrderConfirmed_whenAlreadyProcessing_isIdempotent() {
        Order order = Order.builder()
                .status(OrderStatus.PROCESSING)
                .build();
        order.setId("order-101");

        when(orderRepository.findByIdForUpdate("order-101")).thenReturn(Optional.of(order));

        OrderConfirmedEvent event = new OrderConfirmedEvent("order-101", "user-1", "checkout-1");
        EventEnvelope<OrderConfirmedEvent> envelope = EventEnvelope.v1("order.confirmed", "order-101", "checkout-1", event);

        listener.onOrderConfirmed(envelope, "msg-1");

        verify(orderRepository, never()).save(any());
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    void onOrderConfirmed_whenStatusNotConfirmed_ignores() {
        Order order = Order.builder()
                .status(OrderStatus.CANCELLED)
                .build();
        order.setId("order-101");

        when(orderRepository.findByIdForUpdate("order-101")).thenReturn(Optional.of(order));

        OrderConfirmedEvent event = new OrderConfirmedEvent("order-101", "user-1", "checkout-1");
        EventEnvelope<OrderConfirmedEvent> envelope = EventEnvelope.v1("order.confirmed", "order-101", "checkout-1", event);

        listener.onOrderConfirmed(envelope, "msg-1");

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orderRepository, never()).save(any());
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    void onOrderConfirmed_whenOrderNotFound_ignores() {
        when(orderRepository.findByIdForUpdate("order-999")).thenReturn(Optional.empty());

        OrderConfirmedEvent event = new OrderConfirmedEvent("order-999", "user-1", "checkout-1");
        EventEnvelope<OrderConfirmedEvent> envelope = EventEnvelope.v1("order.confirmed", "order-999", "checkout-1", event);

        listener.onOrderConfirmed(envelope, "msg-1");

        verify(orderRepository, never()).save(any());
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    void onOrderConfirmed_whenOrderIdNull_ignores() {
        OrderConfirmedEvent event = new OrderConfirmedEvent(null, "user-1", "checkout-1");
        EventEnvelope<OrderConfirmedEvent> envelope = EventEnvelope.v1("order.confirmed", null, "checkout-1", event);

        listener.onOrderConfirmed(envelope, "msg-1");

        verify(orderRepository, never()).findByIdForUpdate(any());
        verify(orderRepository, never()).save(any());
        verify(orderStatusHistoryRepository, never()).save(any());
    }
}
