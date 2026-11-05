package com.fashionstore.order.order.service.impl;

import com.fashionstore.order.order.api.DailyRevenue;
import com.fashionstore.order.order.dto.TopProductResponse;
import com.fashionstore.order.order.entity.OrderStatus;
import com.fashionstore.order.order.repository.OrderItemRepository;
import com.fashionstore.order.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatisticsImplTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 18);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private OrderStatisticsImpl statistics;

    @Test
    void totalRevenueCountsOnlyRevenueStatuses() {
        when(orderRepository.calculateTotalRevenue(any())).thenReturn(new BigDecimal("15000000"));

        BigDecimal revenue = statistics.totalRevenue();

        assertThat(revenue).isEqualByComparingTo("15000000");
        ArgumentCaptor<Collection<OrderStatus>> statuses = ArgumentCaptor.captor();
        verify(orderRepository).calculateTotalRevenue(statuses.capture());
        assertThat(statuses.getValue()).contains(OrderStatus.CONFIRMED, OrderStatus.COMPLETED)
                .doesNotContain(OrderStatus.CANCELLED);
    }

    @Test
    void revenueByDayConvertsLocalDateSqlDateAndStringRows() {
        List<Object[]> rows = List.of(
                new Object[]{DAY, new BigDecimal("5000000"), 10L},
                new Object[]{Date.valueOf(DAY.plusDays(1)), 7, 2},
                new Object[]{DAY.plusDays(2).toString(), null, null});
        when(orderRepository.findRevenueByDay(any(), any(LocalDateTime.class))).thenReturn(rows);

        List<DailyRevenue> result = statistics.revenueByDay(LocalDateTime.now().minusDays(30));

        assertThat(result).containsExactly(
                new DailyRevenue(DAY, new BigDecimal("5000000"), 10L),
                new DailyRevenue(DAY.plusDays(1), new BigDecimal("7"), 2L),
                new DailyRevenue(DAY.plusDays(2), BigDecimal.ZERO, 0L));
    }

    @Test
    void topSellingProductsPassesLimitAsFirstPage() {
        TopProductResponse top = new TopProductResponse("var-1", "Áo Polo", 50L, new BigDecimal("10000000"));
        when(orderItemRepository.findTopSellingProducts(any(), any(Pageable.class))).thenReturn(List.of(top));

        List<TopProductResponse> result = statistics.topSellingProducts(10);

        assertThat(result).containsExactly(top);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(orderItemRepository).findTopSellingProducts(any(), page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(10);
    }
}
