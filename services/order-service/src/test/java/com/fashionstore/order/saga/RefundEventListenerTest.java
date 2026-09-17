package com.fashionstore.order.saga;

import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.payment.event.PaymentRefundedEvent;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundEventListenerTest {

    @Mock
    private ProcessedMessageService processedMessageService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    private RefundEventListener listener;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return null;
        }).when(processedMessageService).processOnce(anyString(), anyString(), any(Runnable.class));

        listener = new RefundEventListener(
                processedMessageService,
                orderRepository,
                new ObjectMapper(),
                orderStatusHistoryRepository
        );
    }

    @Test
    void paymentRefunded_whenReturned_transitionsToRefundedAndRecordsHistory() {
        Order order = Order.builder()
                .status(OrderStatus.RETURNED)
                .totalAmount(new BigDecimal("200000"))
                .build();
        order.setId("order-1");

        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));

        PaymentRefundedEvent event = new PaymentRefundedEvent("order-1", "txn-1", "Hoàn tiền thành công");
        EventEnvelope<PaymentRefundedEvent> envelope = EventEnvelope.v1("payment.refunded", "order-1", "order-1", event);

        listener.paymentRefunded(envelope, "msg-1");

        assertEquals(OrderStatus.REFUNDED, order.getStatus());
        verify(orderRepository, times(1)).save(order);

        ArgumentCaptor<OrderStatusHistory> captor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository, times(1)).save(captor.capture());
        assertEquals(OrderStatus.RETURNED, captor.getValue().getFromStatus());
        assertEquals(OrderStatus.REFUNDED, captor.getValue().getToStatus());
        assertEquals("PAYMENT_REFUNDED", captor.getValue().getAction());
        assertEquals("PAYMENT_SERVICE", captor.getValue().getChangedBy());
    }

    @Test
    void paymentRefunded_whenAlreadyRefunded_isIdempotent() {
        Order order = Order.builder()
                .status(OrderStatus.REFUNDED)
                .build();
        order.setId("order-1");

        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));

        PaymentRefundedEvent event = new PaymentRefundedEvent("order-1", "txn-1", "Hoàn tiền thành công");
        EventEnvelope<PaymentRefundedEvent> envelope = EventEnvelope.v1("payment.refunded", "order-1", "order-1", event);

        listener.paymentRefunded(envelope, "msg-1");

        verify(orderRepository, never()).save(any());
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    void paymentRefunded_whenStatusNotReturned_ignores() {
        Order order = Order.builder()
                .status(OrderStatus.SHIPPING)
                .build();
        order.setId("order-1");

        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));

        PaymentRefundedEvent event = new PaymentRefundedEvent("order-1", "txn-1", "Hoàn tiền thành công");
        EventEnvelope<PaymentRefundedEvent> envelope = EventEnvelope.v1("payment.refunded", "order-1", "order-1", event);

        listener.paymentRefunded(envelope, "msg-1");

        assertEquals(OrderStatus.SHIPPING, order.getStatus());
        verify(orderRepository, never()).save(any());
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    void paymentRefunded_whenOrderNotFound_ignores() {
        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.empty());

        PaymentRefundedEvent event = new PaymentRefundedEvent("order-1", "txn-1", "Hoàn tiền thành công");
        EventEnvelope<PaymentRefundedEvent> envelope = EventEnvelope.v1("payment.refunded", "order-1", "order-1", event);

        listener.paymentRefunded(envelope, "msg-1");

        verify(orderRepository, never()).save(any());
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    void paymentRefundRejected_whenOrderExists_recordsPaymentRefundFailedHistory() {
        Order order = Order.builder()
                .userId("user-1")
                .status(OrderStatus.RETURNED)
                .build();
        order.setId("order-1");

        when(orderRepository.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));

        com.fashionstore.contracts.payment.event.PaymentRefundRejectedEvent event =
                new com.fashionstore.contracts.payment.event.PaymentRefundRejectedEvent(
                        "order-1", "pay-1", "VNPAY_GATEWAY_TIMEOUT", "Cổng VNPay không phản hồi"
                );
        EventEnvelope<com.fashionstore.contracts.payment.event.PaymentRefundRejectedEvent> envelope =
                EventEnvelope.v1("payment.refund.rejected", "order-1", "order-1", event);

        listener.paymentRefundRejected(envelope, "msg-2");

        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository, times(1)).save(historyCaptor.capture());
        assertEquals("PAYMENT_REFUND_FAILED", historyCaptor.getValue().getAction());
        assertEquals("PAYMENT_SERVICE", historyCaptor.getValue().getChangedBy());
        assertEquals(OrderStatus.RETURNED, historyCaptor.getValue().getFromStatus());
        assertEquals(OrderStatus.RETURNED, historyCaptor.getValue().getToStatus());
        org.assertj.core.api.Assertions.assertThat(historyCaptor.getValue().getReason())
                .contains("VNPAY_GATEWAY_TIMEOUT");
    }
}
