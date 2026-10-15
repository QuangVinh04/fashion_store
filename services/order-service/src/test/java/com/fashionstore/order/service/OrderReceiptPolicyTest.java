package com.fashionstore.order.service;

import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.dto.OrderResponse;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.event.OutboxEventRecorder;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.OrderStatusHistoryRepository;
import com.fashionstore.order.repository.ReturnRequestRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderReceiptPolicyTest {

    @Test
    void dayFourRemainsEligibleUnderTheSevenDayPolicy() {
        Instant now = Instant.parse("2026-10-12T01:00:00Z");
        ReturnRequestRepository returns = mock(ReturnRequestRepository.class);
        OrderReceiptService service = new OrderReceiptService(mock(OrderRepository.class), returns,
                mock(OrderStatusHistoryRepository.class), mock(CurrentUserProvider.class), mock(OutboxEventRecorder.class),
                mock(OrderNotificationService.class), Clock.fixed(now, ZoneOffset.UTC));
        Order order = Order.builder().userId("customer-1").status(OrderStatus.DELIVERED)
                .deliveredAt(now.minusSeconds(4 * 24 * 3600)).build();
        order.setId("order-1");
        when(returns.findByOrderId("order-1")).thenReturn(Optional.empty());

        OrderResponse response = service.enrich(order, new OrderResponse());
        assertThat(response.isCanRequestReturn()).isTrue();
        assertThat(response.getReturnEligibleUntil()).isEqualTo(order.getDeliveredAt().plusSeconds(7 * 24 * 3600));
    }

    private OrderReceiptService service(Instant now, OrderStatusHistoryRepository history) {
        return new OrderReceiptService(mock(OrderRepository.class), mock(ReturnRequestRepository.class),
                history, mock(CurrentUserProvider.class), mock(OutboxEventRecorder.class),
                mock(OrderNotificationService.class), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void closingAReturnCompletesTheDeliveredOrderImmediately() {
        Instant now = Instant.parse("2026-10-12T01:00:00Z");
        var history = mock(OrderStatusHistoryRepository.class);
        Order order = Order.builder().userId("customer-1").status(OrderStatus.DELIVERED)
                .deliveredAt(now.minusSeconds(2 * 24 * 3600)).build();
        order.setId("order-1");

        service(now, history).completeAfterReturnClosed(order, now, "SYSTEM", "GHN hủy đơn trả");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getCompletedAt()).isEqualTo(now);
        assertThat(order.getCompletionSource()).isEqualTo("RETURN_CLOSED");
        var saved = org.mockito.ArgumentCaptor.forClass(com.fashionstore.order.entity.OrderStatusHistory.class);
        org.mockito.Mockito.verify(history).save(saved.capture());
        assertThat(saved.getValue().getAction()).isEqualTo("RETURN_CLOSED_ORDER_COMPLETED");
        assertThat(saved.getValue().getFromStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(saved.getValue().getToStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(saved.getValue().getChangedBy()).isEqualTo("SYSTEM");
    }

    @Test
    void closingAReturnLeavesAnOrderThatIsNotDeliveredUntouched() {
        Instant now = Instant.parse("2026-10-12T01:00:00Z");
        var history = mock(OrderStatusHistoryRepository.class);
        Order order = Order.builder().userId("customer-1").status(OrderStatus.REFUNDED).build();
        order.setId("order-1");

        service(now, history).completeAfterReturnClosed(order, now, "SYSTEM", "x");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        org.mockito.Mockito.verifyNoInteractions(history);
    }
}
