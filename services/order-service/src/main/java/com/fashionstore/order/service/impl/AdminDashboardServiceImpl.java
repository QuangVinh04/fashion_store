package com.fashionstore.order.service.impl;

import com.fashionstore.order.client.CatalogClient;
import com.fashionstore.order.dto.dashboard.AdminDashboardResponse;
import com.fashionstore.order.dto.dashboard.OrderStatusCountResponse;
import com.fashionstore.order.dto.dashboard.RevenueByDayResponse;
import com.fashionstore.order.dto.dashboard.TopProductResponse;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.repository.OrderItemRepository;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.service.AdminDashboardService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminDashboardServiceImpl implements AdminDashboardService {

    OrderRepository orderRepository;
    OrderItemRepository orderItemRepository;
    CatalogClient catalogClient;

    private static final Set<OrderStatus> REVENUE_STATUSES = Set.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING,
            OrderStatus.PACKED,
            OrderStatus.SHIPPING,
            OrderStatus.DELIVERED,
            OrderStatus.COMPLETED
    );

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboardStats(int days, int lowStockThreshold) {
        if (days <= 0) days = 30;
        if (lowStockThreshold <= 0) lowStockThreshold = 10;

        LocalDateTime startDate = LocalDateTime.now().minusDays(days);

        BigDecimal totalRevenue = orderRepository.calculateTotalRevenue(REVENUE_STATUSES);
        long totalOrders = orderRepository.count();
        List<OrderStatusCountResponse> orderByStatus = orderRepository.countOrdersByStatus();

        List<Object[]> rawRevenue = orderRepository.findRevenueByDay(REVENUE_STATUSES, startDate);
        List<RevenueByDayResponse> revenueByDay = rawRevenue.stream()
                .map(row -> {
                    LocalDate date = null;
                    if (row[0] instanceof LocalDate ld) {
                        date = ld;
                    } else if (row[0] instanceof java.sql.Date sd) {
                        date = sd.toLocalDate();
                    } else if (row[0] != null) {
                        date = LocalDate.parse(row[0].toString());
                    }
                    BigDecimal rev = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
                    long count = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                    return new RevenueByDayResponse(date, rev, count);
                })
                .toList();

        List<TopProductResponse> topProducts = orderItemRepository.findTopSellingProducts(
                REVENUE_STATUSES,
                PageRequest.of(0, 10)
        );

        long lowStockCount = catalogClient.getLowStockCount(lowStockThreshold);

        return AdminDashboardResponse.builder()
                .totalRevenue(totalRevenue != null ? totalRevenue : BigDecimal.ZERO)
                .totalOrders(totalOrders)
                .lowStockCount(lowStockCount)
                .revenueByDay(revenueByDay)
                .orderByStatus(orderByStatus)
                .topProducts(topProducts)
                .build();
    }
}