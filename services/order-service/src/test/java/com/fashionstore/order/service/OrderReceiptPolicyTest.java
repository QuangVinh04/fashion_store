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
}
