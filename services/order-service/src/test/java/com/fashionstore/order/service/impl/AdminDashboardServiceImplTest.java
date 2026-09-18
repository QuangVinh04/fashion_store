package com.fashionstore.order.service.impl;

import com.fashionstore.order.client.CatalogClient;
import com.fashionstore.order.dto.dashboard.AdminDashboardResponse;
import com.fashionstore.order.dto.dashboard.OrderStatusCountResponse;
import com.fashionstore.order.dto.dashboard.TopProductResponse;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.repository.OrderItemRepository;
import com.fashionstore.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CatalogClient catalogClient;

    private AdminDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardServiceImpl(orderRepository, orderItemRepository, catalogClient);
    }

    @Test
    void getDashboardStats_aggregatesDataCorrectly() {
        when(orderRepository.calculateTotalRevenue(any())).thenReturn(new BigDecimal("15000000"));
        when(orderRepository.count()).thenReturn(50L);
        when(orderRepository.countOrdersByStatus()).thenReturn(List.of(
                new OrderStatusCountResponse(OrderStatus.CONFIRMED, 20L),
                new OrderStatusCountResponse(OrderStatus.DELIVERED, 30L)
        ));

        Object[] row1 = new Object[]{LocalDate.of(2026, 9, 18), new BigDecimal("5000000"), 10L};
        List<Object[]> rows = new java.util.ArrayList<>();
        rows.add(row1);
        when(orderRepository.findRevenueByDay(any(), any(LocalDateTime.class))).thenReturn(rows);

        TopProductResponse top1 = new TopProductResponse("var-1", "Áo Polo", 50L, new BigDecimal("10000000"));
        when(orderItemRepository.findTopSellingProducts(any(), any(Pageable.class))).thenReturn(List.of(top1));

        when(catalogClient.getLowStockCount(10)).thenReturn(4L);

        AdminDashboardResponse response = service.getDashboardStats(30, 10);

        assertThat(response).isNotNull();
        assertThat(response.getTotalRevenue()).isEqualByComparingTo(new BigDecimal("15000000"));
        assertThat(response.getTotalOrders()).isEqualTo(50L);
        assertThat(response.getLowStockCount()).isEqualTo(4L);
        assertThat(response.getOrderByStatus()).hasSize(2);
        assertThat(response.getRevenueByDay()).hasSize(1);
        assertThat(response.getRevenueByDay().get(0).getDate()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(response.getRevenueByDay().get(0).getRevenue()).isEqualByComparingTo(new BigDecimal("5000000"));
        assertThat(response.getTopProducts()).hasSize(1);
        assertThat(response.getTopProducts().get(0).getVariantId()).isEqualTo("var-1");

        verify(orderRepository).calculateTotalRevenue(any());
        verify(catalogClient).getLowStockCount(10);
    }

    @Test
    void getDashboardStats_whenCatalogClientFails_fallbackReturnsZeroLowStock() {
        when(orderRepository.calculateTotalRevenue(any())).thenReturn(BigDecimal.ZERO);
        when(orderRepository.count()).thenReturn(0L);
        when(orderRepository.countOrdersByStatus()).thenReturn(List.of());
        when(orderRepository.findRevenueByDay(any(), any(LocalDateTime.class))).thenReturn(List.of());
        when(orderItemRepository.findTopSellingProducts(any(), any(Pageable.class))).thenReturn(List.of());
        when(catalogClient.getLowStockCount(10)).thenReturn(0L);

        AdminDashboardResponse response = service.getDashboardStats(30, 10);

        assertThat(response.getLowStockCount()).isEqualTo(0L);
        assertThat(response.getTotalOrders()).isEqualTo(0L);
    }
}