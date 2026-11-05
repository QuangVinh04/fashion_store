package com.fashionstore.order.order.service.impl;

import com.fashionstore.order.order.api.DailyRevenue;
import com.fashionstore.order.order.api.OrderStatistics;
import com.fashionstore.order.order.dto.OrderStatusCountResponse;
import com.fashionstore.order.order.dto.TopProductResponse;
import com.fashionstore.order.order.entity.OrderStatus;
import com.fashionstore.order.order.repository.OrderItemRepository;
import com.fashionstore.order.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderStatisticsImpl implements OrderStatistics {

    private static final Set<OrderStatus> REVENUE_STATUSES = Set.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING,
            OrderStatus.PACKED,
            OrderStatus.SHIPPING,
            OrderStatus.DELIVERED,
            OrderStatus.COMPLETED
    );

    private static final int REVENUE_COLUMN = 1;
    private static final int ORDER_COUNT_COLUMN = 2;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Override
    @Transactional(readOnly = true)
    public long countOrders() {
        return orderRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal totalRevenue() {
        BigDecimal revenue = orderRepository.calculateTotalRevenue(REVENUE_STATUSES);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStatusCountResponse> countByStatus() {
        return orderRepository.countOrdersByStatus();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyRevenue> revenueByDay(LocalDateTime since) {
        return orderRepository.findRevenueByDay(REVENUE_STATUSES, since).stream()
                .map(OrderStatisticsImpl::toDailyRevenue)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopProductResponse> topSellingProducts(int limit) {
        return orderItemRepository.findTopSellingProducts(REVENUE_STATUSES, PageRequest.of(0, limit));
    }

    private static DailyRevenue toDailyRevenue(Object[] row) {
        BigDecimal revenue = row[REVENUE_COLUMN] != null ? new BigDecimal(row[REVENUE_COLUMN].toString()) : BigDecimal.ZERO;
        long orderCount = row[ORDER_COUNT_COLUMN] != null ? ((Number) row[ORDER_COUNT_COLUMN]).longValue() : 0L;
        return new DailyRevenue(toLocalDate(row[0]), revenue, orderCount);
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return value != null ? LocalDate.parse(value.toString()) : null;
    }
}
