package com.fashionstore.order.dashboard.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fashionstore.common.redis.CacheRegion;
import com.fashionstore.common.redis.RedisService;
import com.fashionstore.order.shared.client.CatalogClient;
import com.fashionstore.order.dashboard.dto.AdminDashboardResponse;
import com.fashionstore.order.dashboard.dto.OperationsDashboardResponse;
import com.fashionstore.order.dashboard.dto.TopProductQuantityResponse;
import com.fashionstore.order.order.dto.OrderStatusCountResponse;
import com.fashionstore.order.dashboard.dto.RevenueByDayResponse;
import com.fashionstore.order.order.dto.TopProductResponse;
import com.fashionstore.order.order.entity.OrderStatus;
import com.fashionstore.order.order.repository.OrderItemRepository;
import com.fashionstore.order.order.repository.OrderRepository;
import com.fashionstore.order.dashboard.service.AdminDashboardService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import com.fashionstore.common.security.PermissionNames;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
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
    RedisService redis;

    private static final int DEFAULT_DAYS = 30;
    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 10;
    // Số liệu tổng hợp nặng (~400ms ở 200k đơn) và admin chấp nhận trễ tối đa 1 phút.
    private static final CacheRegion<AdminDashboardResponse> DASHBOARD = new CacheRegion<>(
            "dashboard", new TypeReference<AdminDashboardResponse>() {}, Duration.ofMinutes(1));

    private static final int TOP_PRODUCT_LIMIT = 10;

    private static final Set<OrderStatus> REVENUE_STATUSES = Set.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING,
            OrderStatus.PACKED,
            OrderStatus.SHIPPING,
            OrderStatus.DELIVERED,
            OrderStatus.COMPLETED
    );

    @Override
    @PreAuthorize("hasAuthority('" + PermissionNames.REVENUE_READ + "')")
    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboardStats(int days, int lowStockThreshold) {
        int windowDays = days > 0 ? days : DEFAULT_DAYS;
        int threshold = resolveLowStockThreshold(lowStockThreshold);
        return redis.getOrLoad(DASHBOARD, () -> loadDashboardStats(windowDays, threshold),
                String.valueOf(windowDays), String.valueOf(threshold));
    }

    @Override
    @PreAuthorize("hasAuthority('" + PermissionNames.DASHBOARD_READ + "')")
    @Transactional(readOnly = true)
    public OperationsDashboardResponse getOperationsStats(int days, int lowStockThreshold) {
        return loadOperationsStats(resolveLowStockThreshold(lowStockThreshold));
    }

    private OperationsDashboardResponse loadOperationsStats(int lowStockThreshold) {
        List<TopProductQuantityResponse> topProducts = findTopProducts().stream()
                .map(top -> new TopProductQuantityResponse(top.getVariantId(), top.getProductName(), top.getTotalQuantitySold()))
                .toList();
        return new OperationsDashboardResponse(
                orderRepository.count(),
                orderRepository.countOrdersByStatus(),
                catalogClient.getLowStockCount(lowStockThreshold),
                topProducts);
    }

    private int resolveLowStockThreshold(int lowStockThreshold) {
        return lowStockThreshold > 0 ? lowStockThreshold : DEFAULT_LOW_STOCK_THRESHOLD;
    }

    private List<TopProductResponse> findTopProducts() {
        return orderItemRepository.findTopSellingProducts(REVENUE_STATUSES, PageRequest.of(0, TOP_PRODUCT_LIMIT));
    }

    private AdminDashboardResponse loadDashboardStats(int days, int lowStockThreshold) {
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

        List<TopProductResponse> topProducts = findTopProducts();

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